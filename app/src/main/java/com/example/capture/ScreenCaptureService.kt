package com.example.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.FamilyGuardRepository
import com.example.data.FamilyLink
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ScreenCaptureMode {
    LIVE_SHARE,
    VIDEO_RECORD
}

data class ScreenCaptureStatus(
    val active: Boolean = false,
    val mode: ScreenCaptureMode = ScreenCaptureMode.LIVE_SHARE,
    val lastRecordingPath: String? = null,
    val errorMessage: String? = null
)

class ScreenCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var currentOutputFile: File? = null
    private var currentLink: FamilyLink? = null
    private var lastUploadedFrameMs: Long = 0L

    private lateinit var repository: FamilyGuardRepository

    override fun onCreate() {
        super.onCreate()
        repository = FamilyGuardRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCaptureAndService()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        val linkId = intent?.getStringExtra(EXTRA_LINK_ID).orEmpty()
        val parentUid = intent?.getStringExtra(EXTRA_PARENT_UID).orEmpty()
        val childUid = intent?.getStringExtra(EXTRA_CHILD_UID).orEmpty()
        val memberUids = intent?.getStringArrayListExtra(EXTRA_MEMBER_UIDS)?.toList() ?: emptyList()
        val modeName = intent?.getStringExtra(EXTRA_MODE) ?: ScreenCaptureMode.LIVE_SHARE.name
        val mode = ScreenCaptureMode.entries.firstOrNull { it.name == modeName }
            ?: ScreenCaptureMode.LIVE_SHARE

        if (resultCode != Activity.RESULT_OK || resultData == null || linkId.isBlank()) {
            _status.value = ScreenCaptureStatus(
                active = false,
                errorMessage = "لم يتم منح إذن مشاركة أو تسجيل الشاشة"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        currentLink = FamilyLink(
            id = linkId,
            parentUid = parentUid,
            childUid = childUid,
            memberUids = memberUids
        )

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startProjection(resultCode, resultData, mode)
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, resultData: Intent, mode: ScreenCaptureMode) {
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                _status.value = ScreenCaptureStatus(
                    active = false,
                    errorMessage = "تعذر بدء جلسة الشاشة"
                )
                stopSelf()
                return
            }

            mediaProjection = projection
            captureThread = HandlerThread("ScreenCaptureThread").apply { start() }
            captureHandler = Handler(captureThread!!.looper)

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Handler(Looper.getMainLooper()).post {
                        stopCaptureAndService()
                    }
                }
            }, captureHandler)

            val metrics = DisplayMetrics()
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val width = (metrics.widthPixels / 2).coerceAtLeast(360) and 0xFFFE
            val height = (metrics.heightPixels / 2).coerceAtLeast(640) and 0xFFFE
            val density = metrics.densityDpi

            if (mode == ScreenCaptureMode.VIDEO_RECORD) {
                setupVideoRecorder(projection, width, height, density)
            } else {
                setupLiveImageReader(projection, width, height, density)
            }

            _status.value = ScreenCaptureStatus(
                active = true,
                mode = mode,
                lastRecordingPath = currentOutputFile?.absolutePath,
                errorMessage = null
            )

            currentLink?.let { link ->
                serviceScope.launch {
                    val battery = ImageUtils.readBatteryTelemetry(applicationContext)
                    repository.updateChildTelemetry(
                        linkId = link.id,
                        batteryPercent = battery.percent,
                        charging = battery.charging,
                        statusText = if (mode == ScreenCaptureMode.VIDEO_RECORD) {
                            "جاري تسجيل الشاشة بشفافية"
                        } else {
                            "مشاركة الشاشة نشطة"
                        },
                        screenSharingActive = true,
                        clearActiveRequest = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media projection", e)
            _status.value = ScreenCaptureStatus(
                active = false,
                errorMessage = "تعذر تشغيل التقاط الشاشة: ${e.localizedMessage ?: ""}"
            )
            stopCaptureAndService()
        }
    }

    private fun setupLiveImageReader(
        projection: MediaProjection,
        width: Int,
        height: Int,
        density: Int
    ) {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ imgReader ->
            val image = imgReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastUploadedFrameMs >= 3000L) {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val rawBitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    rawBitmap.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
                    rawBitmap.recycle()

                    val encoded = ImageUtils.encodeBitmapToBase64(
                        bitmap = cropped,
                        maxDimension = 380,
                        quality = 45
                    )
                    cropped.recycle()

                    lastUploadedFrameMs = now
                    val link = currentLink
                    if (link != null && Firebase.auth.currentUser != null) {
                        serviceScope.launch {
                            repository.uploadCheckinFrame(
                                link = link,
                                kind = "screen",
                                imageBase64 = encoded,
                                caption = "لقطة شاشة حية"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Screen frame process failed: ${e.message}")
            } finally {
                image.close()
            }
        }, captureHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "FamilyGuardLiveScreen",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            captureHandler
        )
    }

    private fun setupVideoRecorder(
        projection: MediaProjection,
        width: Int,
        height: Int,
        density: Int
    ) {
        val dir = File(filesDir, "recordings").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(dir, "screen_$stamp.mp4")
        currentOutputFile = outFile

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        recorder.setVideoEncodingBitRate(2_000_000)
        recorder.setVideoFrameRate(20)
        recorder.setVideoSize(width, height)
        recorder.setOutputFile(outFile.absolutePath)
        recorder.prepare()
        recorder.start()
        mediaRecorder = recorder

        virtualDisplay = projection.createVirtualDisplay(
            "FamilyGuardRecorder",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface,
            null,
            captureHandler
        )
    }

    private fun stopCaptureAndService() {
        val recordedFile = currentOutputFile
        val link = currentLink

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                reset()
                release()
            }
        } catch (_: Exception) {
        }
        mediaRecorder = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null

        try {
            captureThread?.quitSafely()
        } catch (_: Exception) {
        }
        captureThread = null
        captureHandler = null

        if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0L && link != null) {
            serviceScope.launch {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(recordedFile.absolutePath)
                    val frame = retriever.getFrameAtTime(500_000L)
                    retriever.release()
                    if (frame != null && Firebase.auth.currentUser != null) {
                        val base64 = ImageUtils.encodeBitmapToBase64(frame, maxDimension = 380, quality = 48)
                        frame.recycle()
                        repository.uploadCheckinFrame(
                            link = link,
                            kind = "screen",
                            imageBase64 = base64,
                            caption = "تسجيل شاشة محفوظ (${recordedFile.name})"
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Thumbnail extraction skipped: ${e.message}")
                }
            }
        }

        if (link != null && Firebase.auth.currentUser != null) {
            serviceScope.launch {
                val battery = ImageUtils.readBatteryTelemetry(applicationContext)
                repository.updateChildTelemetry(
                    linkId = link.id,
                    batteryPercent = battery.percent,
                    charging = battery.charging,
                    statusText = "متصل",
                    screenSharingActive = false
                )
            }
        }

        _status.value = ScreenCaptureStatus(
            active = false,
            lastRecordingPath = recordedFile?.absolutePath
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_screen_share_title))
            .setContentText(getString(R.string.notif_screen_share_body))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_stop_action), stopIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "family_guard_screen_share"
        private const val NOTIFICATION_ID = 4102

        const val ACTION_STOP = "com.example.action.STOP_SCREEN_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_LINK_ID = "link_id"
        const val EXTRA_PARENT_UID = "parent_uid"
        const val EXTRA_CHILD_UID = "child_uid"
        const val EXTRA_MEMBER_UIDS = "member_uids"
        const val EXTRA_MODE = "capture_mode"

        private val _status = MutableStateFlow(ScreenCaptureStatus())
        val status: StateFlow<ScreenCaptureStatus> = _status.asStateFlow()

        fun listRecordings(context: Context): List<File> {
            val dir = File(context.filesDir, "recordings")
            if (!dir.exists()) return emptyList()
            return dir.listFiles { f -> f.isFile && f.extension == "mp4" }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
