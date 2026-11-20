package com.example.data

import android.content.Context
import android.os.Build
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class FamilyGuardRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب جوجل قبل الوصول للبيانات.")

    fun observeFamilyLinks(uid: String): Flow<List<FamilyLink>> = flow {
        require(uid.isNotBlank()) { "User ID must not be blank" }
        val path = "family_links"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FamilyLink::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observePairingCode(code: String): Flow<PairingCodeDoc?> = flow {
        val uid = requireUserId()
        require(code.matches(Regex("^[0-9]{6}$"))) { "Invalid pairing code" }
        val path = "pairingCodes/$code"
        emitAll(
            db.collection("pairingCodes").document(code)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            PairingCodeDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeMessages(linkId: String, uid: String): Flow<List<FamilyMessage>> = flow {
        require(linkId.isNotBlank() && uid.isNotBlank())
        val path = "family_links/$linkId/messages"
        emitAll(
            db.collection("family_links").document(linkId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FamilyMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeCheckins(linkId: String, uid: String): Flow<List<CheckinFrame>> = flow {
        require(linkId.isNotBlank() && uid.isNotBlank())
        val path = "family_links/$linkId/checkins"
        emitAll(
            db.collection("family_links").document(linkId).collection("checkins")
                .whereArrayContains("memberUids", uid)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CheckinFrame::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun generatePairingCode(deviceLabel: String? = null): Result<String> {
        var docPath = "pairingCodes"
        return try {
            val uid = requireUserId()
            val label = (deviceLabel ?: Build.MODEL ?: "هاتف الطفل")
                .trim()
                .take(40)
                .ifEmpty { "هاتف الطفل" }

            repeat(5) {
                val code = Random.nextInt(100000, 1000000).toString()
                docPath = "pairingCodes/$code"
                val ref = db.collection("pairingCodes").document(code)
                val existing = ref.get().await()
                if (!existing.exists()) {
                    val payload = mapOf(
                        "childUid" to uid,
                        "deviceLabel" to label,
                        "status" to "pending",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    ref.set(payload).await()
                    return Result.success(code)
                }
            }
            Result.failure(IllegalStateException("تعذر إنشاء رمز اقتران جديد، حاول مرة أخرى"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePairingCode(code: String): Result<Unit> {
        val docPath = "pairingCodes/$code"
        return try {
            requireUserId()
            if (!code.matches(Regex("^[0-9]{6}$"))) return Result.success(Unit)
            db.collection("pairingCodes").document(code).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun pairChildByCode(codeInput: String, childNickname: String, parentPin: String): Result<String> {
        val code = codeInput.trim()
        var currentPath = "pairingCodes/$code"
        return try {
            val parentUid = requireUserId()
            require(code.matches(Regex("^[0-9]{6}$"))) { "أدخل رمز اقتران مكون من 6 أرقام صحيحة" }
            val cleanName = childNickname.trim().ifEmpty { "جهاز الطفل" }.take(40)
            val cleanPin = parentPin.trim().take(10)

            val codeRef = db.collection("pairingCodes").document(code)
            val snapshot = codeRef.get().await()
            if (!snapshot.exists()) {
                return Result.failure(IllegalArgumentException("لم يتم العثور على هذا الرمز. تأكد من ظهوره في هاتف الطفل."))
            }
            val codeDoc = snapshot.toObject(PairingCodeDoc::class.java)
                ?: return Result.failure(IllegalStateException("بيانات الرمز غير صالحة"))
            if (codeDoc.status != "pending") {
                return Result.failure(IllegalStateException("تم استخدام هذا الرمز مسبقاً. اطلب رمزاً جديداً من هاتف الطفل."))
            }

            val linkId = "link_${code}_${System.currentTimeMillis()}"
            codeRef.update(
                mapOf(
                    "status" to "claimed",
                    "parentUid" to parentUid,
                    "linkId" to linkId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            currentPath = "family_links/$linkId"
            val members = listOf(parentUid, codeDoc.childUid).distinct()
            val linkPayload = mapOf(
                "parentUid" to parentUid,
                "childUid" to codeDoc.childUid,
                "memberUids" to members,
                "childName" to cleanName,
                "pairingCode" to code,
                "parentPin" to cleanPin,
                "activeRequestType" to ParentRequestType.NONE.wireValue,
                "childBatteryPercent" to 100L,
                "childCharging" to false,
                "childStatusText" to "تم الربط بنجاح",
                "cameraSharingActive" to false,
                "screenSharingActive" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("family_links").document(linkId).set(linkPayload).await()
            Result.success(linkId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun requestChildCheckin(linkId: String, requestType: ParentRequestType): Result<Unit> {
        val docPath = "family_links/$linkId"
        return try {
            requireUserId()
            require(linkId.isNotBlank())
            val payload = mapOf(
                "activeRequestType" to requestType.wireValue,
                "activeRequestAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("family_links").document(linkId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateParentSettings(linkId: String, childName: String, parentPin: String): Result<Unit> {
        val docPath = "family_links/$linkId"
        return try {
            requireUserId()
            val cleanName = childName.trim().ifEmpty { "جهاز الطفل" }.take(40)
            val cleanPin = parentPin.trim().take(10)
            db.collection("family_links").document(linkId).update(
                mapOf(
                    "childName" to cleanName,
                    "parentPin" to cleanPin,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun unlinkChild(linkId: String): Result<Unit> {
        val docPath = "family_links/$linkId"
        return try {
            requireUserId()
            db.collection("family_links").document(linkId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateChildTelemetry(
        linkId: String,
        batteryPercent: Int,
        charging: Boolean,
        statusText: String? = null,
        cameraSharingActive: Boolean? = null,
        screenSharingActive: Boolean? = null,
        clearActiveRequest: Boolean = false
    ): Result<Unit> {
        val docPath = "family_links/$linkId"
        return try {
            requireUserId()
            val updates = mutableMapOf<String, Any>(
                "childBatteryPercent" to batteryPercent.coerceIn(0, 100).toLong(),
                "childCharging" to charging,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (statusText != null) {
                updates["childStatusText"] = statusText.take(120)
            }
            if (cameraSharingActive != null) {
                updates["cameraSharingActive"] = cameraSharingActive
            }
            if (screenSharingActive != null) {
                updates["screenSharingActive"] = screenSharingActive
            }
            if (clearActiveRequest) {
                updates["activeRequestType"] = ParentRequestType.NONE.wireValue
            }
            db.collection("family_links").document(linkId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        link: FamilyLink,
        senderRole: String,
        title: String,
        body: String
    ): Result<Unit> {
        val docRef = db.collection("family_links").document(link.id).collection("messages").document()
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().ifEmpty { "رسالة عائلية" }.take(80)
            val cleanBody = body.trim().take(500)
            require(cleanBody.isNotEmpty()) { "لا يمكن إرسال رسالة فارغة" }
            val payload = mapOf(
                "parentUid" to link.parentUid,
                "childUid" to link.childUid,
                "memberUids" to link.memberUids,
                "senderUid" to uid,
                "senderRole" to senderRole,
                "title" to cleanTitle,
                "body" to cleanBody,
                "acknowledged" to (senderRole == "child"),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeMessage(linkId: String, messageId: String): Result<Unit> {
        val docPath = "family_links/$linkId/messages/$messageId"
        return try {
            requireUserId()
            db.collection("family_links").document(linkId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "acknowledged" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun uploadCheckinFrame(
        link: FamilyLink,
        kind: String,
        imageBase64: String,
        caption: String
    ): Result<Unit> {
        val docRef = db.collection("family_links").document(link.id).collection("checkins").document()
        return try {
            requireUserId()
            require(kind == "camera" || kind == "screen")
            require(imageBase64.isNotBlank() && imageBase64.length <= 250_000)
            val payload = mapOf(
                "parentUid" to link.parentUid,
                "childUid" to link.childUid,
                "memberUids" to link.memberUids,
                "kind" to kind,
                "imageBase64" to imageBase64,
                "caption" to caption.take(120),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    private fun logStreamError(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val ex = firestoreError ?: (error as? Exception) ?: return
        if (auth.currentUser?.uid == uid) {
            handleFirestoreError(ex, op, path)
        }
    }
}
