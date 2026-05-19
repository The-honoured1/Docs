package com.ceo3.docs.ui.screens

import android.widget.Toast
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ceo3.docs.data.local.DocumentEntity
import com.ceo3.docs.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class FilesUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val filteredDocuments: List<DocumentEntity> = emptyList(),
    val selectedFolder: String? = null,
    val selectedTag: String? = null
)

class FilesViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(application).documentDao()
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllDocuments().collectLatest { dbList ->
                _uiState.value = _uiState.value.copy(
                    documents = dbList,
                    filteredDocuments = filterList(dbList, _uiState.value.selectedFolder, _uiState.value.selectedTag)
                )
            }
        }
    }

    fun selectFolder(folderName: String?) {
        val folder = if (_uiState.value.selectedFolder == folderName) null else folderName
        _uiState.value = _uiState.value.copy(
            selectedFolder = folder,
            filteredDocuments = filterList(_uiState.value.documents, folder, _uiState.value.selectedTag)
        )
    }

    fun selectTag(tagName: String?) {
        val tag = if (_uiState.value.selectedTag == tagName) null else tagName
        _uiState.value = _uiState.value.copy(
            selectedTag = tag,
            filteredDocuments = filterList(_uiState.value.documents, _uiState.value.selectedFolder, tag)
        )
    }

    private fun filterList(list: List<DocumentEntity>, folder: String?, tag: String?): List<DocumentEntity> {
        var result = list
        if (folder != null) {
            result = result.filter { it.tags.contains(folder, ignoreCase = true) }
        }
        if (tag != null) {
            result = result.filter { it.tags.contains(tag, ignoreCase = true) }
        }
        return result
    }

    fun togglePin(doc: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertDocument(doc.copy(isPinned = !doc.isPinned))
        }
    }
}

data class FolderItem(
    val name: String,
    val color: Color,
    val count: Int,
    val isShared: Boolean = false,
    val isStarred: Boolean = false
)

data class TagItem(
    val name: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onDocumentClick: (String) -> Unit,
    viewModel: FilesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Folders", "Documents")

    var showPermissionExplanationDialog by remember { mutableStateOf(false) }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scanAndImportFromDownloads(context, viewModel)
        } else {
            Toast.makeText(context, "Storage permission is required to read Downloads folder.", Toast.LENGTH_SHORT).show()
        }
    }

    val foldersList = listOf(
        FolderItem("Work", Color(0xFFF59E0B), 12, isShared = true, isStarred = true),
        FolderItem("Personal", Color(0xFF10B981), 8),
        FolderItem("Clients", Color(0xFF8B5CF6), 6, isShared = true),
        FolderItem("Archive", Color(0xFF6B7280), 20),
        FolderItem("Downloads", Color(0xFF3B82F6), 0)
    )

    val tagsList = listOf(
        TagItem("Important", Color(0xFFEF4444)),
        TagItem("Finance", Color(0xFF10B981)),
        TagItem("Marketing", Color(0xFF3B82F6)),
        TagItem("Personal", Color(0xFF8B5CF6)),
        TagItem("2024", Color(0xFFF59E0B))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Files",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { Toast.makeText(context, "Search Files", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { Toast.makeText(context, "Sort / Filter", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Filled.Tune, contentDescription = "Sort/Filter", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { Toast.makeText(context, "Grid / List layout", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Filled.GridView, contentDescription = "Layout", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // --- Tabs ---
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) },
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 100.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Folders Grid Section ---
            if (selectedTab == 0 || selectedTab == 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Folders",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "View all",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                Toast.makeText(context, "All folders", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                foldersList.chunked(2).forEach { rowFolders ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowFolders.forEach { folder ->
                                val isSelected = state.selectedFolder == folder.name
                                FolderGridItem(
                                    item = folder,
                                    isSelected = isSelected,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (folder.name == "Downloads") {
                                            val hasManagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                Environment.isExternalStorageManager()
                                            } else {
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            }

                                            if (hasManagePermission) {
                                                scanAndImportFromDownloads(context, viewModel)
                                            } else {
                                                showPermissionExplanationDialog = true
                                            }
                                        } else {
                                            viewModel.selectFolder(folder.name)
                                        }
                                    }
                                )
                            }
                            if (rowFolders.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // --- Tags Horizontal Scroll Section ---
            if (selectedTab == 0) {
                item {
                    Text(
                        text = "Tags",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(tagsList) { tag ->
                            val isSelected = state.selectedTag == tag.name
                            TagPillItem(
                                tag = tag,
                                isSelected = isSelected,
                                onClick = { viewModel.selectTag(tag.name) }
                            )
                        }
                        item {
                            // "+ Add tag" Button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { Toast.makeText(context, "Add new tag", Toast.LENGTH_SHORT).show() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Add tag",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // --- Files List Section ---
            if (selectedTab == 0 || selectedTab == 2) {
                item {
                    Text(
                        text = if (state.selectedFolder != null || state.selectedTag != null) "Filtered Files" else "Files",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    )
                }

                if (state.filteredDocuments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No files in this filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(state.filteredDocuments) { doc ->
                        FileRowItem(
                            doc = doc,
                            onClick = { onDocumentClick(doc.id) },
                            onStarToggle = { viewModel.togglePin(doc) }
                        )
                    }
                }
        if (showPermissionExplanationDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionExplanationDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Access Phone Downloads", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text(
                        "To view, import, and manage offline PDFs, presentation slides, and documents from your device's Downloads directory and local storage, the app requires 'All Files Access' permission.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionExplanationDialog = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            } else {
                                legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant Permission", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionExplanationDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun FolderGridItem(
    item: FolderItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) item.color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) item.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(item.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = item.color,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${item.count} items" + if (item.isShared) " · Shared" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (item.isStarred) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Starred",
                tint = Color(0xFFF59E0B),
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.Top)
            )
        } else {
            IconButton(
                onClick = {
                    Toast.makeText(context, "${item.name} options", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TagPillItem(
    tag: TagItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) tag.color.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) tag.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tag.color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = tag.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) tag.color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FileRowItem(
    doc: DocumentEntity,
    onClick: () -> Unit,
    onStarToggle: () -> Unit
) {
    val (icon, iconBg, iconTint) = when (doc.type.uppercase()) {
        "PDF"        -> Triple(Icons.Filled.PictureAsPdf, AccentRose.copy(alpha = 0.12f), AccentRose)
        "DOCX","DOC" -> Triple(Icons.Filled.Description, AccentSky.copy(alpha = 0.12f), AccentSky)
        "XLSX","XLS" -> Triple(Icons.Filled.GridOn, AccentEmerald.copy(alpha = 0.12f), AccentEmerald)
        "PPT","PPTX" -> Triple(Icons.Filled.Slideshow, AccentAmber.copy(alpha = 0.12f), AccentAmber)
        else         -> Triple(Icons.Filled.TextSnippet, Color(0xFF6B7280).copy(alpha = 0.12f), Color(0xFF6B7280))
    }

    val timeLabel = when (doc.id) {
        "project_proposal" -> "Just now"
        "q2_financial_report" -> "2h ago"
        "budget_overview" -> "Yesterday"
        "meeting_notes" -> "2d ago"
        else -> "Just now"
    }

    val sizeLabel = when (doc.id) {
        "project_proposal" -> "1.2 MB"
        "q2_financial_report" -> "2.4 MB"
        "budget_overview" -> "850 KB"
        "meeting_notes" -> "320 KB"
        else -> "24 KB"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${doc.title}.${doc.type.lowercase()}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$timeLabel · $sizeLabel",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onStarToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (doc.isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Pin/Star",
                    tint = if (doc.isPinned) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = { /* File options menu */ }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun scanAndImportFromDownloads(context: android.content.Context, viewModel: FilesViewModel) {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (downloadsDir.exists() && downloadsDir.isDirectory) {
        val files = downloadsDir.listFiles() ?: emptyArray()
        val supportedExtensions = listOf("pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "png", "jpg", "jpeg")
        
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            var importedCount = 0
            val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(context).documentDao()
            files.forEach { file ->
                val ext = file.extension.lowercase()
                if (file.isFile && ext in supportedExtensions) {
                    val docName = file.name
                    val cleanId = "download_${file.nameWithoutExtension}"
                    val existing = dao.getDocumentById(cleanId)
                    if (existing == null) {
                        try {
                            val destFile = java.io.File(context.filesDir, "doc_${cleanId}.${ext}")
                            file.inputStream().use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            val doc = DocumentEntity(
                                id = cleanId,
                                title = docName,
                                type = ext.uppercase(),
                                lastModified = file.lastModified(),
                                isPinned = false,
                                tags = "Downloads,Imported",
                                accentTheme = "classic",
                                accentColor = "blue"
                            )
                            dao.insertDocument(doc)
                            importedCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            
            // Switch back to Main thread for visual feedback
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (importedCount > 0) {
                    Toast.makeText(context, "Imported $importedCount new files from phone Downloads!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "No new documents found in phone Downloads.", Toast.LENGTH_SHORT).show()
                }
                viewModel.selectFolder("Downloads")
            }
        }
    } else {
        Toast.makeText(context, "Downloads folder not accessible.", Toast.LENGTH_SHORT).show()
    }
}

