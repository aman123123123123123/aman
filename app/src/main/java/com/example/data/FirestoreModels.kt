package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class DeviceRole(val storageKey: String) {
    UNSET("unset"),
    PARENT("parent"),
    CHILD("child");

    companion object {
        fun fromKey(raw: String?): DeviceRole =
            entries.firstOrNull { it.storageKey == raw } ?: UNSET
    }
}

enum class ParentRequestType(val wireValue: String) {
    NONE("none"),
    CAMERA_CHECKIN("camera_checkin"),
    SCREEN_SHARE("screen_share");

    companion object {
        fun fromWire(raw: String?): ParentRequestType =
            entries.firstOrNull { it.wireValue == raw } ?: NONE
    }
}

@Keep
@IgnoreExtraProperties
data class PairingCodeDoc(
    @DocumentId val code: String = "",
    val childUid: String = "",
    val deviceLabel: String = "",
    val status: String = "pending",
    val parentUid: String? = null,
    val linkId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FamilyLink(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val childName: String = "",
    val pairingCode: String = "",
    val parentPin: String = "",
    val activeRequestType: String = ParentRequestType.NONE.wireValue,
    val activeRequestAt: Timestamp? = null,
    val childBatteryPercent: Long = 100L,
    val childCharging: Boolean = false,
    val childStatusText: String = "",
    val cameraSharingActive: Boolean = false,
    val screenSharingActive: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FamilyMessage(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val senderUid: String = "",
    val senderRole: String = "parent",
    val title: String = "",
    val body: String = "",
    val acknowledged: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CheckinFrame(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val kind: String = "camera",
    val imageBase64: String = "",
    val caption: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
