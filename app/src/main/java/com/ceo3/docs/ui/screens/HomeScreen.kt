package com.ceo3.docs.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ceo3.docs.data.local.DocumentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// UI State representing the Home screen
data class HomeUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val searchResults: List<DocumentEntity> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false
)

// Viewmodel representing the Home screen business logic
class HomeViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(application).documentDao()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        refreshDocuments()
    }

    fun refreshDocuments() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            dao.getAllDocuments().collectLatest { dbList ->
                val deviceList = scanDeviceDocuments(getApplication())
                val combined = (dbList + deviceList).sortedByDescending { it.lastModified }
                _uiState.value = _uiState.value.copy(
                    documents = combined
                )
                if (_uiState.value.searchQuery.isNotEmpty()) {
                    performSearch(_uiState.value.searchQuery)
                }
            }
        }
    }

    private fun scanDeviceDocuments(context: android.content.Context): List<DocumentEntity> {
        val list = mutableListOf<DocumentEntity>()
        val extensions = listOf("pdf", "docx", "doc", "txt")
        val scanDirs = mutableListOf<File>()

        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads != null && downloads.exists()) {
                scanDirs.add(downloads)
            }
        } catch (e: Exception) {}

        try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docs != null && docs.exists()) {
                scanDirs.add(docs)
            }
        } catch (e: Exception) {}

        // Fallbacks
        val fallbackDownloads = File("/storage/emulated/0/Download")
        if (fallbackDownloads.exists()) scanDirs.add(fallbackDownloads)
        val fallbackDocs = File("/storage/emulated/0/Documents")
        if (fallbackDocs.exists()) scanDirs.add(fallbackDocs)

        for (dir in scanDirs) {
            try {
                val files = dir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile && file.extension.lowercase() in extensions) {
                            val uniqueId = file.absolutePath
                            if (list.none { it.id == uniqueId }) {
                                list.add(
                                    DocumentEntity(
                                        id = uniqueId,
                                        title = file.nameWithoutExtension,
                                        type = file.extension.uppercase(),
                                        lastModified = file.lastModified(),
                                        isPinned = false,
                                        tags = "External,${dir.name}",
                                        accentTheme = "classic",
                                        accentColor = "blue"
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to scan folder ${dir.absolutePath}", e)
            }
        }
        return list
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            isSearching = query.isNotEmpty()
        )
        performSearch(query)
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        val filtered = _uiState.value.documents.filter { doc ->
            doc.title.contains(query, ignoreCase = true) ||
            doc.type.contains(query, ignoreCase = true) ||
            doc.tags.contains(query, ignoreCase = true)
        }
        _uiState.value = _uiState.value.copy(searchResults = filtered)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEditor: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToDonate: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Launcher for selecting external system files on the fly
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            onNavigateToEditor(it.toString())
        }
    }

    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                        PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Trigger document refresh upon load or permission change
    LaunchedEffect(hasStoragePermission) {
        viewModel.refreshDocuments()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            // ── Sleek Compact Header ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Docs",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Support icon that navigates to DonateScreen
                IconButton(
                    onClick = onNavigateToDonate,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Support & Donate",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── Search Bar ───────────────────────────────────────────────────
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = { Text("Search your files...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            )

            // LazyColumn to display actions, support card, and lists of files
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (state.isSearching) {
                    item {
                        Text(
                            text = "Search Results",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (state.searchResults.isEmpty()) {
                        item {
                            NoneEmptyState(text = "No files match your search")
                        }
                    } else {
                        items(state.searchResults) { doc ->
                            DocumentListItem(
                                document = doc,
                                onClick = { onNavigateToEditor(doc.id) }
                            )
                        }
                    }
                } else {
                    // Quick Action Cards
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ActionCard(
                                title = "Scan Document",
                                subtitle = "Camera OCR & PDF",
                                icon = Icons.Filled.DocumentScanner,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToScanner
                            )
                            ActionCard(
                                title = "Open / Edit File",
                                subtitle = "PDF, DOCX, TXT",
                                icon = Icons.Filled.FileOpen,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    filePickerLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "text/plain"
                                        )
                                    )
                                }
                            )
                        }
                    }

                    // Open Source Donation Banner
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                                    RoundedCornerShape(20.dp)
                                  )
                                .clickable { onNavigateToDonate() }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CardGiftcard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Support Open Source Development",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Keep all tools 100% free and ad-free.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Recent Files Section
                    item {
                        Text(
                            text = "Recent Files",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (state.documents.isEmpty()) {
                        item {
                            NoneEmptyState(text = "None")
                        }
                    } else {
                        items(state.documents.take(12)) { doc ->
                            DocumentListItem(
                                document = doc,
                                onClick = { onNavigateToEditor(doc.id) }
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun DocumentListItem(
    document: DocumentEntity,
    onClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val dateStr = formatter.format(Date(document.lastModified))

    val icon = when (document.type.uppercase()) {
        "PDF" -> Icons.Filled.PictureAsPdf
        "DOCX", "DOC" -> Icons.Filled.Description
        else -> Icons.Filled.TextSnippet
    }

    val iconColor = when (document.type.uppercase()) {
        "PDF" -> Color(0xFFE57373)
        "DOCX", "DOC" -> Color(0xFF64B5F6)
        else -> Color(0xFFFFB74D)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = document.type.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (document.tags.contains("External")) {
                    val folder = document.tags.substringAfter("External,").uppercase()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = folder,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "•",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dateStr,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}
