package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CheckinFrame
import com.example.data.FamilyGuardRepository
import com.example.data.FamilyLink
import com.example.data.FamilyMessage
import com.example.data.ParentRequestType
import com.example.data.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ParentViewModel(
    private val repository: FamilyGuardRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedLinkId = MutableStateFlow<String?>(null)
    private val _actionBusy = MutableStateFlow(false)
    val actionBusy: StateFlow<Boolean> = _actionBusy.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    val linksState: StateFlow<UiState<List<FamilyLink>>> = repository
        .observeFamilyLinks(currentUserId)
        .map<List<FamilyLink>, UiState<List<FamilyLink>>> { links ->
            UiState.Success(links.filter { it.parentUid == currentUserId })
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing family links", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل الأجهزة المرتبطة"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val selectedLink: StateFlow<FamilyLink?> = combine(linksState, _selectedLinkId) { state, selectedId ->
        val links = (state as? UiState.Success)?.data.orEmpty()
        links.firstOrNull { it.id == selectedId } ?: links.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    val messagesState: StateFlow<UiState<List<FamilyMessage>>> = selectedLink
        .flatMapLatest { link ->
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
                        Log.w(TAG, "Error observing messages", rootError)
                        emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل الرسائل"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val checkinsState: StateFlow<UiState<List<CheckinFrame>>> = selectedLink
        .flatMapLatest { link ->
            if (link == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeCheckins(link.id, currentUserId)
                    .map<List<CheckinFrame>, UiState<List<CheckinFrame>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing check-in frames", rootError)
                        emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل لقطات الفحص"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun selectLink(linkId: String) {
        _selectedLinkId.value = linkId
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun pairChild(code: String, childNickname: String, parentPin: String, onPaired: () -> Unit = {}) {
        viewModelScope.launch {
            _actionBusy.value = true
            val result = repository.pairChildByCode(code, childNickname, parentPin)
            _actionBusy.value = false
            result.onSuccess { newLinkId ->
                _selectedLinkId.value = newLinkId
                _feedbackMessage.value = "تم ربط جهاز الطفل بنجاح!"
                onPaired()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر ربط الجهاز بالرمز المدخل"
            }
        }
    }

    fun requestCheckin(type: ParentRequestType) {
        val link = selectedLink.value ?: return
        viewModelScope.launch {
            _actionBusy.value = true
            val result = repository.requestChildCheckin(link.id, type)
            _actionBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = when (type) {
                    ParentRequestType.CAMERA_CHECKIN -> "تم إرسال طلب فحص الكاميرا إلى هاتف ${link.childName}"
                    ParentRequestType.SCREEN_SHARE -> "تم إرسال طلب مشاركة/تسجيل الشاشة إلى هاتف ${link.childName}"
                    ParentRequestType.NONE -> "تم إلغاء الطلب النشط"
                }
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر إرسال الطلب"
            }
        }
    }

    fun sendMessage(title: String, body: String) {
        val link = selectedLink.value ?: return
        viewModelScope.launch {
            _actionBusy.value = true
            val result = repository.sendMessage(
                link = link,
                senderRole = "parent",
                title = title,
                body = body
            )
            _actionBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "تم إرسال الرسالة إلى ${link.childName}"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر إرسال الرسالة"
            }
        }
    }

    fun updateChildSettings(childName: String, parentPin: String) {
        val link = selectedLink.value ?: return
        viewModelScope.launch {
            _actionBusy.value = true
            val result = repository.updateParentSettings(link.id, childName, parentPin)
            _actionBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "تم حفظ إعدادات الوالد والرمز السري"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر حفظ الإعدادات"
            }
        }
    }

    fun unlinkChild(linkId: String) {
        viewModelScope.launch {
            _actionBusy.value = true
            val result = repository.unlinkChild(linkId)
            _actionBusy.value = false
            result.onSuccess {
                _selectedLinkId.value = null
                _feedbackMessage.value = "تم فك ارتباط الجهاز"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر فك الارتباط"
            }
        }
    }

    private companion object {
        const val TAG = "ParentVM"
    }
}
