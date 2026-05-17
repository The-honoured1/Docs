package com.ceo3.docs.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FileItem(val id: String, val name: String, val isFolder: Boolean, val itemCount: Int = 0)

class FilesViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(application).documentDao()
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState

    init {
        viewModelScope.launch {
            dao.getAllDocuments().collect { docs ->
                val allTags = docs.flatMap { it.tags.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                val items = docs.map { doc -> FileItem(doc.id, doc.title, false, 0) }
                _uiState.value = _uiState.value.copy(
                    tags = allTags.ifEmpty { listOf("School", "Work", "Finance", "Personal") },
                    items = items
                )
            }
        }
    }

    fun selectTag(tag: String) {
        _uiState.value = _uiState.value.copy(selectedTag = tag)
        // In a real app, we would re-filter the list here based on the selected tag
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
    viewModel: FilesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files & Folders", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // Tags Row
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.tags) { tag ->
                    FilterChip(
                        selected = state.selectedTag == tag,
                        onClick = { viewModel.selectTag(tag) },
                        label = { Text(tag) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Divider()

            // File List
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.items) { item ->
                    if (item.isFolder) {
                        FolderItem(item = item, onClick = { /* Navigate into folder */ })
                    } else {
                        DocumentListItem(
                            doc = DocumentModel(item.id, item.name, "PDF", "Just now", false),
                            onClick = { onDocumentClick(item.id) }
                        )
                    }
                    Divider(modifier = Modifier.padding(start = 56.dp))
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun FolderItem(item: FileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = "Folder",
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("${item.itemCount} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
