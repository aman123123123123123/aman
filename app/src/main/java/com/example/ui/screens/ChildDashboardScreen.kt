package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.capture.CameraCheckInCard
import com.example.capture.ImageUtils
import com.example.capture.ScreenCaptureMode
import com.example.capture.ScreenCaptureService
import com.example.data.FamilyLink
import com.example.data.FamilyMessage
import com.example.data.ParentRequestType
import com.example.data.UiState
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.ChildViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDashboardScreen(
    viewModel: ChildViewModel,
    onSwitchRole: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingCode.collectAsStateWithLifecycle()
    val linkState by viewModel.childLinkState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val screenStatus by ScreenCaptureService.status.collectAsStateWithLifecycle()

    val activeLink = (linkState as? UiState.Success)?.data
    var cameraCheckInActive by remember { mutableStateOf(false) }
    var pendingScreenMode by remember { mutableStateOf(ScreenCaptureMode.LIVE_SHARE) }
    var recordings by remember(screenStatus.active, screenStatus.lastRecordingPath) {
        mutableStateOf(ScreenCaptureService.listRecordings(context))
    }
    var showPinDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (cameraCheckInActive) {
            cameraCheckInActive = false
            activeLink?.let { link ->
                val battery = ImageUtils.readBatteryTelemetry(context)
                viewModel.updateTelemetry(
                    linkId = link.id,
                    batteryPercent = battery.percent,
                    charging = battery.charging,
                    statusText = "متصل",
                    cameraSharingActive = false
                )
            }
        } else if (!activeLink?.parentPin.isNullOrBlank()) {
            showPinDialog = true
        } else {
            onSwitchRole()
        }
    }

    LaunchedEffect(feedback) {
        if (!feedback.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedback!!)
            viewModel.clearFeedback()
        }
    }

    LaunchedEffect(screenStatus.errorMessage) {
        screenStatus.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Periodic real battery sync while paired
    LaunchedEffect(activeLink?.id) {
        val link = activeLink ?: return@LaunchedEffect
        while (true) {
            val battery = ImageUtils.readBatteryTelemetry(context)
            viewModel.updateTelemetry(
                linkId = link.id,
                batteryPercent = battery.percent,
                charging = battery.charging
            )
            delay(30_000L)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraCheckInActive = true
            activeLink?.let { link ->
                val battery = ImageUtils.readBatteryTelemetry(context)
                viewModel.updateTelemetry(
                    linkId = link.id,
                    batteryPercent = battery.percent,
                    charging = battery.charging,
                    statusText = "فحص الكاميرا نشط بشفافية",
                    cameraSharingActive = true,
                    clearActiveRequest = true
                )
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("يلزم منح إذن الكاميرا لمشاركة الفحص مع الوالد")
            }
        }
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val link = activeLink
        if (result.resultCode == Activity.RESULT_OK && result.data != null && link != null) {
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_LINK_ID, link.id)
                putExtra(ScreenCaptureService.EXTRA_PARENT_UID, link.parentUid)
                putExtra(ScreenCaptureService.EXTRA_CHILD_UID, link.childUid)
                putStringArrayListExtra(
                    ScreenCaptureService.EXTRA_MEMBER_UIDS,
                    ArrayList(link.memberUids)
                )
                putExtra(ScreenCaptureService.EXTRA_MODE, pendingScreenMode.name)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("تم إلغاء طلب مشاركة/تسجيل الشاشة")
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
    }

    fun launchScreenCapture(mode: ScreenCaptureMode) {
        if (activeLink == null) {
            scope.launch { snackbarHostState.showSnackbar("يرجى ربط الهاتف بجهاز الوالد أولاً") }
            return
        }
        pendingScreenMode = mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
        }
    }

    fun startCameraCheckIn() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraCheckInActive = true
            activeLink?.let { link ->
                val battery = ImageUtils.readBatteryTelemetry(context)
                viewModel.updateTelemetry(
                    linkId = link.id,
                    batteryPercent = battery.percent,
                    charging = battery.charging,
                    statusText = "فحص الكاميرا نشط بشفافية",
                    cameraSharingActive = true,
                    clearActiveRequest = true
                )
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("وضع الطفل • حارس العائلة", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (activeLink != null) "مرتبط بجهاز الوالد (${activeLink.childName})"
                            else "بانتظار الربط برمز الاقتران",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!activeLink?.parentPin.isNullOrBlank()) {
                                showPinDialog = true
                            } else {
                                onSwitchRole()
                            }
                        },
                        modifier = Modifier.testTag("child_switch_role_button")
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "تغيير نوع الجهاز")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Pairing Code or Connected Banner
                item {
                    PairingCodeCard(
                        code = pairingCode,
                        isPaired = activeLink != null,
                        childName = activeLink?.childName,
                        isGenerating = isGenerating,
                        onCopy = { code ->
                            clipboard.setText(AnnotatedString(code))
                            scope.launch { snackbarHostState.showSnackbar("تم نسخ رمز الاقتران: $code") }
                        },
                        onRefresh = viewModel::regeneratePairingCode
                    )
                }

                // 2. Parent Check-in Request Alert (Transparent consent)
                if (activeLink != null) {
                    val req = ParentRequestType.fromWire(activeLink.activeRequestType)
                    if (req != ParentRequestType.NONE) {
                        item {
                            ParentRequestCard(
                                requestType = req,
                                onApprove = {
                                    when (req) {
                                        ParentRequestType.CAMERA_CHECKIN -> startCameraCheckIn()
                                        ParentRequestType.SCREEN_SHARE -> launchScreenCapture(ScreenCaptureMode.LIVE_SHARE)
                                        ParentRequestType.NONE -> Unit
                                    }
                                }
                            )
                        }
                    }

                    // 3. Active Camera Check-In Preview
                    if (cameraCheckInActive) {
                        item {
                            CameraCheckInCard(
                                onFrameCaptured = { encoded ->
                                    viewModel.uploadCameraFrame(activeLink, encoded)
                                },
                                onStopCamera = {
                                    cameraCheckInActive = false
                                    val battery = ImageUtils.readBatteryTelemetry(context)
                                    viewModel.updateTelemetry(
                                        linkId = activeLink.id,
                                        batteryPercent = battery.percent,
                                        charging = battery.charging,
                                        statusText = "متصل",
                                        cameraSharingActive = false
                                    )
                                }
                            )
                        }
                    }

                    // 4. Child Quick Tools & Screen Recording / Sharing Controls
                    item {
                        ChildSafetyControlsCard(
                            screenActive = screenStatus.active,
                            screenMode = screenStatus.mode,
                            cameraActive = cameraCheckInActive,
                            onStartCamera = ::startCameraCheckIn,
                            onStartLiveScreen = { launchScreenCapture(ScreenCaptureMode.LIVE_SHARE) },
                            onStartVideoRecord = { launchScreenCapture(ScreenCaptureMode.VIDEO_RECORD) },
                            onStopScreen = { ScreenCaptureService.stop(context) },
                            onQuickReply = { text -> viewModel.sendQuickReply(activeLink, text) }
                        )
                    }

                    // 5. Messages from Parent
                    item {
                        Text(
                            text = "رسائل وتنبيهات الوالد",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
                    if (messages.isEmpty()) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "ستظهر هنا الرسائل التي يرسلها الوالد من هاتفه فوراً.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            ChildMessageItem(
                                message = msg,
                                onAcknowledge = {
                                    viewModel.acknowledgeMessage(activeLink.id, msg.id)
                                }
                            )
                        }
                    }

                    // 6. Local Saved Screen Recordings
                    if (recordings.isNotEmpty()) {
                        item {
                            Text(
                                text = "تسجيلات الشاشة المحفوظة (${recordings.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        items(recordings, key = { it.absolutePath }) { file ->
                            SavedRecordingRow(
                                file = file,
                                onPlay = {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "video/mp4")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(playIntent)
                                    } catch (_: Exception) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("تم حفظ الفيديو في: ${file.name}")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog && activeLink != null) {
        ParentPinDialog(
            expectedPin = activeLink.parentPin,
            onDismiss = { showPinDialog = false },
            onUnlocked = {
                showPinDialog = false
                onSwitchRole()
            }
        )
    }
}

@Composable
private fun PairingCodeCard(
    code: String?,
    isPaired: Boolean,
    childName: String?,
    isGenerating: Boolean,
    onCopy: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isPaired) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isPaired) "الحماية العائلية نشطة (${childName ?: "متصل"})"
                    else "رمز الاقتران الخاص بهاتف الطفل",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (!isPaired) {
                Text(
                    text = "أدخل هذا الرمز في هاتف الوالد لربط الهاتفين فوراً دون الحاجة لحساب إضافي للطفل:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            val formattedCode = (code ?: "------").toCharArray().joinToString("  ")
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_pairing_code_display")
            ) {
                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }

            if (!isPaired) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { code?.let(onCopy) },
                        enabled = !code.isNullOrBlank(),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("نسخ الرمز")
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !isGenerating,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("رمز جديد")
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentRequestCard(
    requestType: ParentRequestType,
    onApprove: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (requestType == ParentRequestType.CAMERA_CHECKIN) {
                        "طلب من الوالد: فحص أمان بالكاميرا"
                    } else {
                        "طلب من الوالد: مشاركة الشاشة بشفافية"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = "سيتم تفعيل المشاركة بوضوح تام مع إشعار ظاهر طوال مدة الجلسة ويمكنك إيقافها في أي وقت.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Button(
                onClick = onApprove,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("approve_parent_request_button")
            ) {
                Text(
                    if (requestType == ParentRequestType.CAMERA_CHECKIN) "موافقة وبدء الكاميرا"
                    else "موافقة وبدء مشاركة الشاشة"
                )
            }
        }
    }
}

@Composable
private fun ChildSafetyControlsCard(
    screenActive: Boolean,
    screenMode: ScreenCaptureMode,
    cameraActive: Boolean,
    onStartCamera: () -> Unit,
    onStartLiveScreen: () -> Unit,
    onStartVideoRecord: () -> Unit,
    onStopScreen: () -> Unit,
    onQuickReply: (String) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("أدوات المشاركة السريعة مع الوالد", style = MaterialTheme.typography.titleMedium)

            if (screenActive) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDC2626))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (screenMode == ScreenCaptureMode.VIDEO_RECORD) {
                                    "جاري تسجيل الشاشة فيديو (MP4) الآن"
                                } else {
                                    "مشاركة الشاشة الحية نشطة الآن مع الوالد"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = onStopScreen,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("إيقاف المشاركة / التسجيل")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onStartCamera,
                        enabled = !cameraActive,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("كاميرا")
                    }
                    OutlinedButton(
                        onClick = onStartLiveScreen,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.ScreenShare, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("بث الشاشة")
                    }
                }
                Button(
                    onClick = onStartVideoRecord,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("child_record_screen_button")
                ) {
                    Icon(Icons.Filled.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تسجيل الشاشة فيديو (MP4)")
                }
            }

            Text("ردود سريعة للوالد:", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("أنا بخير 👍", "وصلت للمنزل 🏠", "أذاكر دروسي 📖")) { reply ->
                    SuggestionChip(
                        onClick = { onQuickReply(reply) },
                        label = { Text(reply) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildMessageItem(
    message: FamilyMessage,
    onAcknowledge: () -> Unit
) {
    val isFromParent = message.senderRole == "parent"
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isFromParent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(message.title, style = MaterialTheme.typography.labelLarge)
            Text(message.body, style = MaterialTheme.typography.bodyLarge)
            if (isFromParent) {
                if (message.acknowledged) {
                    Surface(shape = RoundedCornerShape(50), color = SuccessContainer) {
                        Text(
                            "تم تأكيد القراءة ✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onAcknowledge,
                        modifier = Modifier.height(44.dp).testTag("ack_message_button")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تمت القراءة")
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedRecordingRow(file: File, onPlay: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.VideoFile, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${(file.length() / 1024).coerceAtLeast(1)} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "تشغيل تسجيل الشاشة")
            }
        }
    }
}

@Composable
private fun ParentPinDialog(
    expectedPin: String,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أدخل الرمز السري للوالد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("هذا الجهاز محمي برمز سري وضعه الوالد لتبديل الدور.")
                OutlinedTextField(
                    value = entered,
                    onValueChange = {
                        entered = it.filter(Char::isDigit).take(10)
                        wrong = false
                    },
                    label = { Text("الرمز السري") },
                    isError = wrong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (wrong) {
                    Text("الرمز غير صحيح", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (entered == expectedPin) onUnlocked() else wrong = true
            }) {
                Text("تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
