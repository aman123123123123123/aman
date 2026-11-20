package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.capture.ImageUtils
import com.example.data.CheckinFrame
import com.example.data.FamilyLink
import com.example.data.FamilyMessage
import com.example.data.ParentRequestType
import com.example.data.UiState
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.ParentViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: ParentViewModel,
    onSwitchRole: () -> Unit,
    onSignOut: () -> Unit
) {
    val linksState by viewModel.linksState.collectAsStateWithLifecycle()
    val selectedLink by viewModel.selectedLink.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val checkinsState by viewModel.checkinsState.collectAsStateWithLifecycle()
    val actionBusy by viewModel.actionBusy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    var currentTab by remember { mutableIntStateOf(0) }
    var showPairDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = currentTab != 0) {
        currentTab = 0
    }

    LaunchedEffect(feedback) {
        if (!feedback.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedback!!)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("لوحة الوالد", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "حارس العائلة • متابعة آمنة وشفافة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showPairDialog = true },
                        modifier = Modifier.testTag("open_pair_dialog_button")
                    ) {
                        Icon(Icons.Filled.AddLink, contentDescription = "ربط هاتف طفل")
                    }
                    IconButton(
                        onClick = onSwitchRole,
                        modifier = Modifier.testTag("parent_switch_role_button")
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "تغيير نوع الجهاز")
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("parent_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "تسجيل الخروج")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Filled.Monitor, contentDescription = null) },
                    label = { Text("المتابعة") },
                    modifier = Modifier.testTag("parent_tab_monitor")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Filled.Message, contentDescription = null) },
                    label = { Text("الرسائل") },
                    modifier = Modifier.testTag("parent_tab_messages")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("الإعدادات") },
                    modifier = Modifier.testTag("parent_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                when (val state = linksState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is UiState.Success -> {
                        val links = state.data
                        if (links.isEmpty()) {
                            ParentEmptyState(
                                busy = actionBusy,
                                onPair = { code, name, pin ->
                                    viewModel.pairChild(code, name, pin)
                                }
                            )
                        } else {
                            if (links.size > 1) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(links, key = { it.id }) { link ->
                                        FilterChip(
                                            selected = selectedLink?.id == link.id,
                                            onClick = { viewModel.selectLink(link.id) },
                                            label = { Text(link.childName) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.PhoneAndroid,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            val activeLink = selectedLink ?: links.first()
                            when (currentTab) {
                                0 -> ParentMonitorTab(
                                    link = activeLink,
                                    checkinsState = checkinsState,
                                    actionBusy = actionBusy,
                                    onRequestCheckin = viewModel::requestCheckin
                                )
                                1 -> ParentMessagesTab(
                                    link = activeLink,
                                    messagesState = messagesState,
                                    actionBusy = actionBusy,
                                    onSendMessage = viewModel::sendMessage
                                )
                                2 -> ParentSettingsTab(
                                    link = activeLink,
                                    actionBusy = actionBusy,
                                    onSaveSettings = viewModel::updateChildSettings,
                                    onUnlink = { viewModel.unlinkChild(activeLink.id) },
                                    onOpenPair = { showPairDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPairDialog) {
        PairChildDialog(
            busy = actionBusy,
            onDismiss = { showPairDialog = false },
            onConfirm = { code, name, pin ->
                viewModel.pairChild(code, name, pin) {
                    showPairDialog = false
                }
            }
        )
    }
}

@Composable
private fun ParentEmptyState(
    busy: Boolean,
    onPair: (code: String, nickname: String, pin: String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AddLink,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
        item {
            Text(
                text = "اربط هاتف الطفل الآن",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "افتح التطبيق في هاتف الطفل واختر «أنا الطفل»، ثم اكتب الرمز المكون من 6 أرقام هنا:",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("رمز الطفل (6 أرقام)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inline_pair_code_input")
                    )
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it.take(40) },
                        label = { Text("اسم الطفل (مثال: أحمد)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inline_pair_name_input")
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                        label = { Text("رمز سري لحماية وضع الطفل (اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onPair(code, nickname, pin) },
                        enabled = !busy && code.length == 6,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("empty_pair_child_button")
                    ) {
                        Icon(Icons.Filled.AddLink, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "جاري الربط..." else "ربط هاتف الطفل الآن")
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentMonitorTab(
    link: FamilyLink,
    checkinsState: UiState<List<CheckinFrame>>,
    actionBusy: Boolean,
    onRequestCheckin: (ParentRequestType) -> Unit
) {
    val frames = (checkinsState as? UiState.Success)?.data.orEmpty()
    var selectedFrame by remember(frames) { mutableStateOf(frames.firstOrNull()) }
    val activeRequest = ParentRequestType.fromWire(link.activeRequestType)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = link.childName,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = link.childStatusText.ifBlank { "متصل بالحماية العائلية" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (link.childCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                                    contentDescription = "نسبة البطارية",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${link.childBatteryPercent}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onRequestCheckin(ParentRequestType.CAMERA_CHECKIN) },
                            enabled = !actionBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("request_camera_button")
                        ) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("فحص الكاميرا")
                        }

                        Button(
                            onClick = { onRequestCheckin(ParentRequestType.SCREEN_SHARE) },
                            enabled = !actionBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("request_screen_button")
                        ) {
                            Icon(Icons.Filled.ScreenShare, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مشاركة الشاشة")
                        }
                    }

                    if (activeRequest != ParentRequestType.NONE || link.cameraSharingActive || link.screenSharingActive) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        link.cameraSharingActive -> "الكاميرا نشطة الآن على هاتف ${link.childName}"
                                        link.screenSharingActive -> "مشاركة/تسجيل الشاشة نشط على هاتف ${link.childName}"
                                        activeRequest == ParentRequestType.CAMERA_CHECKIN -> "بانتظار موافقة الطفل على فحص الكاميرا..."
                                        else -> "بانتظار موافقة الطفل على مشاركة الشاشة..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onRequestCheckin(ParentRequestType.NONE) }) {
                                    Text("إلغاء")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "معاينة الفحص المباشر واللقطات",
                        style = MaterialTheme.typography.titleMedium
                    )

                    val displayFrame = selectedFrame ?: frames.firstOrNull()
                    if (displayFrame == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ستظهر هنا لقطات الكاميرا والشاشة فور بدئها من هاتف الطفل.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        val decoded = remember(displayFrame.id, displayFrame.imageBase64) {
                            ImageUtils.decodeBase64ToImageBitmap(displayFrame.imageBase64)
                        }
                        if (decoded != null) {
                            Image(
                                bitmap = decoded,
                                contentDescription = displayFrame.caption,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = displayFrame.caption.ifBlank {
                                    if (displayFrame.kind == "camera") "لقطة كاميرا" else "لقطة شاشة"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatTimestamp(displayFrame.createdAt?.seconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (frames.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(frames, key = { it.id }) { frame ->
                                val thumb = remember(frame.id) {
                                    ImageUtils.decodeBase64ToImageBitmap(frame.imageBase64)
                                }
                                if (thumb != null) {
                                    val isSelected = (selectedFrame?.id ?: frames.first().id) == frame.id
                                    Image(
                                        bitmap = thumb,
                                        contentDescription = frame.caption,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedFrame = frame }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentMessagesTab(
    link: FamilyLink,
    messagesState: UiState<List<FamilyMessage>>,
    actionBusy: Boolean,
    onSendMessage: (String, String) -> Unit
) {
    var messageBody by remember { mutableStateOf("") }
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val presets = listOf(
        "وقت المذاكرة" to "يا ${link.childName}، حان وقت المذاكرة الآن 📚",
        "اتصال سريع" to "تواصل معي الآن عند انتهائك 📞",
        "وقت النوم" to "يرجى إغلاق الهاتف والاستعداد للنوم 🌙"
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إرسال رسالة أو تنبيه إلى ${link.childName}", style = MaterialTheme.typography.titleMedium)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { (label, body) ->
                            SuggestionChip(
                                onClick = { onSendMessage(label, body) },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = messageBody,
                        onValueChange = { messageBody = it.take(500) },
                        label = { Text("اكتب رسالتك هنا...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parent_message_input"),
                        maxLines = 3
                    )

                    Button(
                        onClick = {
                            val trimmed = messageBody.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendMessage("رسالة من الوالد", trimmed)
                                messageBody = ""
                            }
                        },
                        enabled = !actionBusy && messageBody.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("parent_send_message_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("إرسال للهاتف الآخر")
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            item {
                Text(
                    text = "لا توجد رسائل بعد. أرسل أول رسالة ليراها طفلك داخل التطبيق.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(messages, key = { it.id }) { msg ->
                val isFromParent = msg.senderRole == "parent"
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isFromParent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg.title,
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (isFromParent && msg.acknowledged) {
                                Surface(shape = RoundedCornerShape(50), color = SuccessContainer) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "تمت القراءة",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen
                                        )
                                    }
                                }
                            }
                        }
                        Text(msg.body, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = formatTimestamp(msg.createdAt?.seconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentSettingsTab(
    link: FamilyLink,
    actionBusy: Boolean,
    onSaveSettings: (String, String) -> Unit,
    onUnlink: () -> Unit,
    onOpenPair: () -> Unit
) {
    var name by remember(link.id) { mutableStateOf(link.childName) }
    var pin by remember(link.id) { mutableStateOf(link.parentPin) }
    var confirmUnlink by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("إعدادات جهاز الطفل وقفل الحماية", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("اسم الطفل") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                        label = { Text("الرمز السري لقفل وضع الطفل (اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onSaveSettings(name, pin) },
                        enabled = !actionBusy,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("حفظ الإعدادات")
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenPair,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.AddLink, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إضافة جهاز طفل آخر")
            }
        }

        item {
            OutlinedButton(
                onClick = { confirmUnlink = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("فك ارتباط هذا الجهاز")
            }
        }
    }

    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text("فك ارتباط ${link.childName}؟") },
            text = { Text("سيتم إيقاف الربط بين الهاتفين حتى يتم إدخال رمز اقتران جديد.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnlink = false
                    onUnlink()
                }) {
                    Text("نعم، فك الارتباط", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnlink = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun PairChildDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (code: String, nickname: String, pin: String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ربط هاتف الطفل برمز الاقتران") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "أدخل الرمز المكون من 6 أرقام الظاهر في شاشة هاتف الطفل.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("رمز الاقتران (6 أرقام)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pair_code_input")
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(40) },
                    label = { Text("اسم الطفل (مثال: أحمد)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pair_child_name_input")
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text("رمز سري لقفل وضع الطفل (اختياري)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code, nickname, pin) },
                enabled = !busy && code.length == 6,
                modifier = Modifier.testTag("confirm_pair_button")
            ) {
                Text(if (busy) "جاري الربط..." else "ربط الآن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

private fun formatTimestamp(seconds: Long?): String {
    if (seconds == null || seconds <= 0L) return "الآن"
    val formatter = SimpleDateFormat("hh:mm a", Locale("ar"))
    return formatter.format(java.util.Date(seconds * 1000L))
}
