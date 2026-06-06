package com.example.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessage
import com.example.data.ChatThread
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Collect variables from ViewModel
    val allThreads by viewModel.allThreads.collectAsStateWithLifecycle()
    val activeThread by viewModel.activeThread.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    
    // Search states
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }

    // Text To Speech setup
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var spokenMessageId by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(context) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialized successfully
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    // Speech-To-Text recognizer intent
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                viewModel.setInputText(spokenText)
            }
        }
    }

    // Document export launchers
    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { viewModel.exportActiveThreadToTxt(it, context) }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportActiveThreadToJson(it, context) }
    }

    // Settings dialog controllers
    var editModelName by remember { mutableStateOf("gemini-3.5-flash") }
    var editTemp by remember { mutableFloatStateOf(0.7f) }
    var editPrompt by remember { mutableStateOf("") }
    var editNameInput by remember { mutableStateOf(userName) }

    // Synchronize settings states when opening Dialog
    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog && activeThread != null) {
            editModelName = activeThread!!.modelName
            editTemp = activeThread!!.temperature
            editPrompt = activeThread!!.systemInstruction
            editNameInput = userName
        }
    }

    // Modal Drawer wrapper
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 2.dp
            ) {
                Spacer(modifier = Modifier.statusBarsPadding())
                
                // Sidebar Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    val activeUser by viewModel.activeUserProfile.collectAsStateWithLifecycle()
                    val allUsers by viewModel.allUserProfiles.collectAsStateWithLifecycle()
                    var isProfilesExpanded by remember { mutableStateOf(false) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isProfilesExpanded = !isProfilesExpanded }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        // Display active user avatar
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val activePic = activeUser?.profilePicturePath
                            if (activePic != null && java.io.File(activePic).exists()) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(model = java.io.File(activePic)),
                                    contentDescription = "Active Avatar",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val initial = if (!userName.isNullOrBlank()) userName.first().uppercase() else "P"
                                Text(
                                    text = initial,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeUser?.username ?: "Pranjal Guest",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Premium Profile",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isProfilesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Collapsible multi-user profile switcher list
                    AnimatedVisibility(
                        visible = isProfilesExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = "SWITCH USER PROFILE",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            allUsers.forEach { user ->
                                val isChosen = user.id == activeUser?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isChosen) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            viewModel.selectUserProfile(user)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val pic = user.profilePicturePath
                                        if (pic != null && java.io.File(pic).exists()) {
                                            androidx.compose.foundation.Image(
                                                painter = coil.compose.rememberAsyncImagePainter(model = java.io.File(pic)),
                                                contentDescription = "User Avatar",
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            val initial = if (user.username.isNotBlank()) user.username.first().uppercase() else "P"
                                            Text(
                                                text = initial,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = user.username,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isChosen) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        IconButton(
                                            onClick = { viewModel.deleteUserProfile(user) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Profile",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Profile creation form inline
                            var newProfileName by remember { mutableStateOf("") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextField(
                                    value = newProfileName,
                                    onValueChange = { newProfileName = it },
                                    placeholder = { Text("Add new user profile...", fontSize = 11.sp) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    textStyle = TextStyle(fontSize = 11.sp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (newProfileName.isNotBlank()) {
                                            viewModel.createUserProfile(newProfileName.trim())
                                            newProfileName = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Create",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // "New Chat" Command button
                    Button(
                        onClick = {
                            viewModel.createNewThread()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_chat_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "New")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start New Chat", fontWeight = FontWeight.Bold)
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 1.dp)

                // Saved Chats ListView
                Text(
                    text = "CONVERSATION HISTORY",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allThreads) { thread ->
                        val isSelected = activeThread?.id == thread.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    viewModel.selectThread(thread)
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                                contentDescription = "Chat",
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Editable/Displayable chat title
                            var isEditingTitle by remember { mutableStateOf(false) }
                            var editTitleText by remember { mutableStateOf(thread.title) }

                            if (isEditingTitle) {
                                TextField(
                                    value = editTitleText,
                                    onValueChange = { editTitleText = it },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    textStyle = TextStyle(fontSize = 13.sp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            viewModel.renameThread(thread, editTitleText)
                                            isEditingTitle = false
                                        }) {
                                            Icon(Icons.Default.Check, "Save", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    text = thread.title,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                if (isSelected) {
                                    // Title edit controls
                                    IconButton(
                                        onClick = { isEditingTitle = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                                    }
                                }
                            }

                            // Delete button
                            IconButton(
                                onClick = { viewModel.deleteThread(thread) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                // Sidebar Footer / Clear Command
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.clearAllChats() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Clear All Histories", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "AI",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Pranjal AI",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                        Text(
                                            text = when (activeThread?.modelName) {
                                                "gemini-3.1-pro-preview" -> "Pro Reasoning"
                                                else -> "Fast Response"
                                            },
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        },
                        actions = {
                            // Quick light/dark theme toggle
                            val isDarkThemeActive by viewModel.isDarkMode.collectAsStateWithLifecycle()
                            IconButton(
                                onClick = { viewModel.setDarkMode(!isDarkThemeActive) },
                                modifier = Modifier.testTag("theme_quick_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isDarkThemeActive) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Theme",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            // Search toggle
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                )
                            }
                            // Custom settings toggle
                            IconButton(onClick = { viewModel.toggleSettingsDialog(true) }) {
                                Icon(Icons.Default.Tune, contentDescription = "Config", tint = MaterialTheme.colorScheme.onBackground)
                            }
                            // More Options Dropdown for Exporting Chat History
                            var isMoreMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { isMoreMenuExpanded = true },
                                    modifier = Modifier.testTag("export_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Options",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                DropdownMenu(
                                    expanded = isMoreMenuExpanded,
                                    onDismissRequest = { isMoreMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Export as Plain Text (.txt)") },
                                        leadingIcon = { 
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            ) 
                                        },
                                        onClick = {
                                            isMoreMenuExpanded = false
                                            if (activeMessages.isEmpty()) {
                                                Toast.makeText(context, "No messages to export", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val cleanTitle = activeThread?.title?.replace(Regex("[^a-zA-Z0-9_]"), "_") ?: "chat"
                                                exportTxtLauncher.launch("${cleanTitle}_export.txt")
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export as JSON (.json)") },
                                        leadingIcon = { 
                                            Icon(
                                                imageVector = Icons.Default.Code,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            ) 
                                        },
                                        onClick = {
                                            isMoreMenuExpanded = false
                                            if (activeMessages.isEmpty()) {
                                                Toast.makeText(context, "No messages to export", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val cleanTitle = activeThread?.title?.replace(Regex("[^a-zA-Z0-9_]"), "_") ?: "chat"
                                                exportJsonLauncher.launch("${cleanTitle}_export.json")
                                            }
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background
                        )
                    )

                    // Inline Search Drawer
                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search keyword across all chats...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                leadingIcon = { Icon(Icons.Default.Search, "Query", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (searchQuery.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Found ${searchResults.size} results:",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 200.dp)
                                ) {
                                    items(searchResults) { result ->
                                        Card(
                                            onClick = {
                                                // Load appropriate thread containing speech
                                                viewModel.allThreads.value.find { it.id == result.threadId }?.let { targetThread ->
                                                    viewModel.selectThread(targetThread)
                                                    isSearchActive = false
                                                }
                                            },
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = if (result.role == "user") "You asked" else "Pranjal response",
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                                    Text(
                                                        text = format.format(Date(result.timestamp)),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = result.text,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 12.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 1.dp)
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding() // Adjust for keyboard raising
            ) {
                // If thread message string is empty, render beautiful starter page
                if (activeMessages.isEmpty()) {
                    WelcomeLayout(
                        userName = userName,
                        onStarterSelected = { starter ->
                            viewModel.setInputText(starter)
                            viewModel.sendMessage()
                        }
                    )
                } else {
                    // Chat messages list
                    val scrollState = rememberLazyListState()
                    LaunchedEffect(activeMessages.size) {
                        if (activeMessages.isNotEmpty()) {
                            scrollState.animateScrollToItem(activeMessages.size - 1)
                        }
                    }

                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp), // Space for input row
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(activeMessages, key = { it.id }) { message ->
                            MessageItemRow(
                                message = message,
                                isLast = message == activeMessages.last(),
                                isSpeaking = spokenMessageId == message.id,
                                onCopyClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Pranjal Message", message.text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied content to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                onSpeakToggle = {
                                    if (spokenMessageId == message.id) {
                                        tts?.stop()
                                        spokenMessageId = null
                                    } else {
                                        spokenMessageId = message.id
                                        tts?.speak(message.text, TextToSpeech.QUEUE_FLUSH, null, "MsgSpeak")
                                    }
                                },
                                onDeleteClick = {
                                    viewModel.deleteChatMessage(message.id)
                                }
                            )
                        }

                        if (isGenerating) {
                            item {
                                MessageLoadingIndicatorRow()
                            }
                        }
                    }
                }

                // Lower message writing zone
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.background
                                ),
                                startY = 0f,
                                endY = 40f
                            )
                        )
                        .navigationBarsPadding()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Input text row
                    TextField(
                        value = inputText,
                        onValueChange = { viewModel.setInputText(it) },
                        placeholder = { Text("Ask Pranjal AI anything...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                            .testTag("chat_input_field"),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Pranjal AI...")
                                    }
                                    try {
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Voice search not supported on device.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Speech input", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        maxLines = 4
                    )

                    // Glow button sending prompt
                    IconButton(
                        onClick = { viewModel.sendMessage() },
                        enabled = inputText.isNotBlank() && !isGenerating,
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(if (inputText.isNotBlank()) 4.dp else 0.dp, CircleShape)
                            .background(
                                if (inputText.isNotBlank() && !isGenerating)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            .testTag("send_prompt_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Settings Customizing dialog
    if (showSettingsDialog) {
        Dialog(onDismissRequest = { viewModel.toggleSettingsDialog(false) }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Customize Pranjal AI",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Theme Mode Selector Option
                    Text("App Theme Mode", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    val isDarkThemeEnabled by viewModel.isDarkMode.collectAsStateWithLifecycle()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { viewModel.setDarkMode(!isDarkThemeEnabled) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkThemeEnabled) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = "Theme Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = if (isDarkThemeEnabled) "Dark Mode" else "Light Mode",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isDarkThemeEnabled) "Optimized for eye-comfort in low light" else "Clear visibility in bright environments",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Switch(
                            checked = isDarkThemeEnabled,
                            onCheckedChange = { viewModel.setDarkMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Export Chat Section
                    Text("Export Chat Session", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // TXT Export Button
                        Button(
                            onClick = {
                                if (activeMessages.isEmpty()) {
                                    Toast.makeText(context, "No messages to export", Toast.LENGTH_SHORT).show()
                                } else {
                                    val cleanTitle = activeThread?.title?.replace(Regex("[^a-zA-Z0-9_]"), "_") ?: "chat"
                                    exportTxtLauncher.launch("${cleanTitle}_export.txt")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("export_txt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("As Text (.txt)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // JSON Export Button
                        Button(
                            onClick = {
                                if (activeMessages.isEmpty()) {
                                    Toast.makeText(context, "No messages to export", Toast.LENGTH_SHORT).show()
                                } else {
                                    val cleanTitle = activeThread?.title?.replace(Regex("[^a-zA-Z0-9_]"), "_") ?: "chat"
                                    exportJsonLauncher.launch("${cleanTitle}_export.json")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("export_json_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("As JSON (.json)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // User Profile settings & Avatar
                    Text("User Profile & Avatar", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val activeUser by viewModel.activeUserProfile.collectAsStateWithLifecycle()
                        val avatarLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetContent()
                        ) { uri ->
                            uri?.let { viewModel.saveProfilePicture(it) }
                        }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { avatarLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            val picPath = activeUser?.profilePicturePath
                            if (picPath != null && java.io.File(picPath).exists()) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(model = java.io.File(picPath)),
                                    contentDescription = "User Avatar",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = "Upload Avatar",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Profile Icon",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap the circle to upload a custom avatar picture.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("User Profile Name", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    TextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Model Selection
                    Text("Intelligence Engine", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Flash Engine card
                        Card(
                            onClick = { editModelName = "gemini-3.5-flash" },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (editModelName == "gemini-3.5-flash") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (editModelName == "gemini-3.5-flash") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "⚡ Flash Engine (gemini-3.5-flash)",
                                    color = if (editModelName == "gemini-3.5-flash") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Fast responses. Excellent for everyday dialogs, creative tasks, and tutoring.",
                                    color = if (editModelName == "gemini-3.5-flash") MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Pro Reasoning card
                        Card(
                            onClick = { editModelName = "gemini-3.1-pro-preview" },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (editModelName == "gemini-3.1-pro-preview") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (editModelName == "gemini-3.1-pro-preview") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "🧠 Pro Reasoning (gemini-3.1-pro-preview)",
                                    color = if (editModelName == "gemini-3.1-pro-preview") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Deep processing. Factual recall, code blocks, complex reasoning, and math equations.",
                                    color = if (editModelName == "gemini-3.1-pro-preview") MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Combined Dual Engine
                        Card(
                            onClick = { editModelName = "combined-dual" },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (editModelName == "combined-dual") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (editModelName == "combined-dual") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "🔮 Combined Dual Engine (Fusing Flash + Pro)",
                                    color = if (editModelName == "combined-dual") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Runs Factual and Creative models concurrently, merging findings into an extraordinary synthesized assistance.",
                                    color = if (editModelName == "combined-dual") MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Temperature slider
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Creativity Temperature", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(String.format("%.1f", editTemp), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                    Slider(
                        value = editTemp,
                        onValueChange = { editTemp = it },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // System Instruction Prompt
                    Text("Role System Instructions", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    TextField(
                        value = editPrompt,
                        onValueChange = { editPrompt = it },
                        textStyle = TextStyle(fontSize = 12.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Dialog bottom save controls
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { viewModel.toggleSettingsDialog(false) }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updateActiveUserProfile(editNameInput)
                                viewModel.updateThreadConfig(
                                    modelName = editModelName,
                                    temperature = editTemp,
                                    systemInstruction = editPrompt
                                )
                                viewModel.toggleSettingsDialog(false)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --- Comcomposable sub-items ---

@Composable
fun WelcomeLayout(
    userName: String,
    onStarterSelected: (String) -> Unit
) {
    val starterPrompts = listOf(
        Pair("Write code for snake game in Kotlin", Icons.Default.Code),
        Pair("Suggest creative content slogans for Pranjal AI", Icons.Default.TipsAndUpdates),
        Pair("Help me optimize database query responses", Icons.Default.Storage),
        Pair("Draft a thank you letter for a colleague", Icons.Default.Email)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Aesthetic Material 3 Theme-bounded Logo Container
        Box(
            modifier = Modifier
                .shadow(6.dp, RoundedCornerShape(24.dp))
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(24.dp)
                )
                .size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = "AI Logo",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome, $userName",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Get answers, write elegant script blocks, or brainstorm ideas with Pranjal AI's custom intelligence.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .widthIn(max = 420.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "TAP A STARTER PROMPT",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Starter Prompt Tiles Grid
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            starterPrompts.forEach { (prompt, icon) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onStarterSelected(prompt) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = "Prompt icon", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = prompt,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItemRow(
    message: ChatMessage,
    isLast: Boolean,
    isSpeaking: Boolean,
    onCopyClick: () -> Unit,
    onSpeakToggle: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Professional AI Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(2.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "AI",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Message speech bubble card
        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 24.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 24.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 24.dp
                ),
                border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .shadow(1.dp, RoundedCornerShape(24.dp))
                    .widthIn(max = 310.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (isUser) {
                        Text(
                            text = message.text,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    } else {
                        // Render formatted Markdown layout for AI outputs (headers, bolding, code cards)
                        FormattedMessageText(text = message.text)
                    }
                }
            }

            // Message Actions row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            ) {
                IconButton(onClick = onCopyClick, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                }
                IconButton(onClick = onSpeakToggle, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                        contentDescription = "Speech",
                        tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(10.dp))
            // User avatar (soft container style)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                     imageVector = Icons.Default.Person,
                     contentDescription = "User",
                     tint = MaterialTheme.colorScheme.onPrimaryContainer,
                     modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MessageLoadingIndicatorRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.onBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "AI",
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.widthIn(max = 240.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thinking",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                // Pulse dots
                PulseDot(delay = 0)
                PulseDot(delay = 150)
                PulseDot(delay = 300)
            }
        }
    }
}

@Composable
fun PulseDot(delay: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delay),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = scale), CircleShape)
    )
}

// --- Markdown Parsers ---

@Composable
fun FormattedMessageText(text: String) {
    if (!text.contains("```")) {
        NormalMarkdownText(rawText = text)
        return
    }

    // Split text into normal parts vs code block parts
    val parts = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // Odd segments are actual code blocks! Re-split language tag if present
                val lines = part.trim().split("\n")
                val langToken = lines.firstOrNull()?.trim() ?: "Code"
                val hasCodeLang = langToken.length < 15 && !langToken.contains(" ")
                val codeContent = if (hasCodeLang) {
                    lines.drop(1).joinToString("\n")
                } else {
                    part
                }
                CodeBlockCard(
                    langToken = if (hasCodeLang) langToken else "Code Block",
                    codeText = codeContent
                )
            } else {
                // Alternate segments are standard description segments
                if (part.isNotBlank()) {
                    NormalMarkdownText(rawText = part)
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(
    langToken: String,
    codeText: String
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
    ) {
        Column {
            // terminal window header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // red, yellow, green macOS terminal points
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF5F56), CircleShape))
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFBD2E), CircleShape))
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF27C93F), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = langToken.uppercase(Locale.ROOT),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Copy button
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Pranjal Code", codeText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy code", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                }
            }

            // Monospace code text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = codeText.trim(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun NormalMarkdownText(rawText: String) {
    val lines = rawText.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmedLine = line.trim()
            when {
                // Bullet List Rows
                trimmedLine.startsWith("* ") || trimmedLine.startsWith("- ") -> {
                    val cleanText = trimmedLine.substring(2)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp, end = 8.dp)
                                .size(5.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Text(
                            text = parseInlineBoldString(cleanText),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
                
                // Header rows
                trimmedLine.startsWith("### ") -> {
                    Text(
                        text = trimmedLine.substring(4),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                trimmedLine.startsWith("## ") -> {
                    Text(
                        text = trimmedLine.substring(3),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                trimmedLine.startsWith("# ") -> {
                    Text(
                        text = trimmedLine.substring(2),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                }

                // Regular message lines
                else -> {
                    if (trimmedLine.isNotBlank()) {
                        Text(
                            text = parseInlineBoldString(trimmedLine),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// Inline bold parser helper
fun parseInlineBoldString(text: String) = buildAnnotatedString {
    var cursor = 0
    while (cursor < text.length) {
        val nextPair = text.indexOf("**", cursor)
        if (nextPair == -1) {
            append(text.substring(cursor))
            break
        }
        
        // Append raw pre-bold segment
        append(text.substring(cursor, nextPair))
        
        val closingPair = text.indexOf("**", nextPair + 2)
        if (closingPair == -1) {
            append("**")
            cursor = nextPair + 2
        } else {
            // Append styled bold text chunk (Color.Unspecified lets it inherit parent text color appropriately!)
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Unspecified)) {
                append(text.substring(nextPair + 2, closingPair))
            }
            cursor = closingPair + 2
        }
    }
}
