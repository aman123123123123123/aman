package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FamilyGuardRepository
import com.example.data.FamilyLink
import com.example.data.FamilyMessage
import com.example.data.PreferencesRepository
import com.example.data.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChildViewModel(
    private val repository: FamilyGuardRepository,
    private val preferences: PreferencesRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    private val _isGeneratingCode = MutableStateFlow(false)
    val isGeneratingCode: StateFlow<Boolean> = _isGeneratingCode.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    val childLinkState: StateFlow<UiState<FamilyLink?>> = repository
        .observeFamilyLinks(currentUserId)
        .map<List<FamilyLink>, UiState<FamilyLink?>> { links ->
            UiState.Success(links.firstOrNull { it.childUid == currentUserId })
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing child link", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "تعذر التحقق من حالة الاقتران"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<FamilyMessage>>> = childLinkState
        .flatMapLatest { linkUi ->
            val link = (linkUi as? UiState.Success)?.data
            if (link == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(link.id, currentUserId)
                    .map<List<FamilyMessage>, UiState<List<FamilyMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing child messages", rootError)
                        emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل الرسائل"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        ensurePairingCode()
    }

    fun ensurePairingCode() {
        viewModelScope.launch {
            val saved = preferences.observeSavedPairingCode(currentUserId).firstOrNull()
            if (!saved.isNullOrBlank()) {
                _pairingCode.value = saved
            } else {
                regeneratePairingCode()
            }
        }
    }

    fun regeneratePairingCode() {
        viewModelScope.launch {
            _isGeneratingCode.value = true
            val oldCode = _pairingCode.value
            if (!oldCode.isNullOrBlank()) {
                repository.deletePairingCode(oldCode)
            }
            val res = repository.generatePairingCode()
            _isGeneratingCode.value = false
            res.onSuccess { code ->
                _pairingCode.value = code
                preferences.setSavedPairingCode(currentUserId, code)
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر إنشاء رمز الاقتران"
            }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun acknowledgeMessage(linkId: String, messageId: String) {
        viewModelScope.launch {
            repository.acknowledgeMessage(linkId, messageId).onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر تأكيد القراءة"
            }
        }
    }

    fun sendQuickReply(link: FamilyLink, statusText: String) {
        viewModelScope.launch {
            repository.sendMessage(
                link = link,
                senderRole = "child",
                title = "تحديث من ${link.childName}",
                body = statusText
            ).onSuccess {
                _feedbackMessage.value = "تم إرسال التحديث إلى الوالد"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر إرسال التحديث"
            }
        }
    }

    fun updateTelemetry(
        linkId: String,
        batteryPercent: Int,
        charging: Boolean,
        statusText: String? = null,
        cameraSharingActive: Boolean? = null,
        clearActiveRequest: Boolean = false
    ) {
        viewModelScope.launch {
            repository.updateChildTelemetry(
                linkId = linkId,
                batteryPercent = batteryPercent,
                charging = charging,
                statusText = statusText,
                cameraSharingActive = cameraSharingActive,
                clearActiveRequest = clearActiveRequest
            )
        }
    }

    fun uploadCameraFrame(link: FamilyLink, base64: String) {
        viewModelScope.launch {
            repository.uploadCheckinFrame(
                link = link,
                kind = "camera",
                imageBase64 = base64,
                caption = "لقطة فحص الكاميرا"
            )
        }
    }

    private companion object {
        const val TAG = "ChildVM"
    }
}
