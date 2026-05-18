package com.ceo3.docs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FileItem(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val itemCount: Int = 0,
    val sizeLabel: String = "",
    val dateLabel: String = ""
)

class FilesViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(application).documentDao()
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState

    init {
        viewModelScope.launch {
            dao.getAllDocuments().collect { docs ->
                val tags = docs
                    .flatMap { it.tags.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .ifEmpty { listOf("All", "School", "Work", "Finance", "Personal") }

                val items = docs.map { doc ->
                    FileItem(
                        id        = doc.id,
                        name      = doc.title,
                        isFolder  = false,
                        sizeLabel = doc.type,
                        dateLabel = android.text.format.DateUtils.getRelativeTimeSpanString(
                            doc.lastModified
                        ).toString()
                    )
                }
                _uiState.value = _uiState.value.copy(tags = tags, items = items)
            }
        }
    }

    fun selectTag(tag: String) {
        _uiState.value = _uiState.value.copy(selectedTag = if (_uiState.value.selectedTag == tag) null else tag)
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            dao.deleteDocument(id)
        }
    }
}

data class FilesUiState(
    val tags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val items: List<FileItem> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onDocumentClick: (String) -> Unit,
    viewModel: FilesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val displayItems = state.items.filter { item ->
        (searchQuery.isEmpty() || item.name.contains(searchQuery, ignoreCase = true)) &&
                (state.selectedTag == null || state.selectedTag == "All")
    }

    // Confirm-delete dialog
    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            containerColor   = MaterialTheme.colorScheme.surface,
            title = {
                Text("Delete document?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will permanently remove the file.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDocument(id)
                    pendingDeleteId = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // ── Header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSearch) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search files…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { showSearch = false; searchQuery = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        "Files & Folders",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { showSearch = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── Tag filter row ────────────────────────────────────────────
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.tags) { tag ->
                    val selected = state.selectedTag == tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { viewModel.selectTag(tag) }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = tag,
                            color = if (selected) MaterialTheme.colorScheme.background
                                    else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Summary label ─────────────────────────────────────────────
            Text(
                "${displayItems.size} document${if (displayItems.size != 1) "s" else ""}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            // ── File list ─────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)
            ) {
                // Dummy data when empty
                val showDummy = displayItems.isEmpty() && searchQuery.isEmpty()
                if (showDummy) {
                    val dummies = listOf(
                        FileItem("d1", "Taxes 2024",           true,  4, "",     ""),
                        FileItem("d2", "Travel Documents",     true,  2, "",     ""),
                        FileItem("d3", "Contract_final.pdf",  false, 0, "PDF",  "Just now"),
                        FileItem("d4", "Meeting_notes.txt",   false, 0, "TXT",  "Yesterday"),
                        FileItem("d5", "Invoice_May_2026.pdf",false, 0, "PDF",  "2 days ago")
                    )
                    items(dummies) { item ->
                        if (item.isFolder) {
                            FolderItem(item = item, onClick = {})
                        } else {
                            FileListItem(
                                item    = item,
                                onClick = { onDocumentClick(item.id) },
                                onDelete = {}
                            )
                        }
                    }
                } else {
                    items(displayItems, key = { it.id }) { item ->
                        if (item.isFolder) {
                            FolderItem(item = item, onClick = {})
                        } else {
                            FileListItem(
                                item    = item,
                                onClick = { onDocumentClick(item.id) },
                                onDelete = { pendingDeleteId = item.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Folder card ──────────────────────────────────────────────────────────────

@Composable
fun FolderItem(item: FileItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = "Folder",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "${item.itemCount} items",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── File card ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListItem(item: FileItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val typeColor = when (item.sizeLabel.uppercase()) {
        "PDF"       -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        "TXT"       -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        "XLS","XLSX"-> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
        else        -> MaterialTheme.colorScheme.surface
    }
    val iconTint = when (item.sizeLabel.uppercase()) {
        "PDF"       -> MaterialTheme.colorScheme.error
        "TXT"       -> MaterialTheme.colorScheme.primary
        "XLS","XLSX"-> MaterialTheme.colorScheme.tertiary
        else        -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(typeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = "File",
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.sizeLabel.isNotEmpty()) {
                        TypeBadge(item.sizeLabel)
                    }
                    if (item.dateLabel.isNotEmpty()) {
                        Text(
                            item.dateLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: String) {
    val bg = when (type.uppercase()) {
        "PDF"        -> MaterialTheme.colorScheme.error
        "TXT"        -> MaterialTheme.colorScheme.primary
        "XLS","XLSX" -> MaterialTheme.colorScheme.tertiary
        else         -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            type.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = bg
        )
    }
}
