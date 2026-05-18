package com.ceo3.docs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        containerColor = Color(0xFFF7F8F8)
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Files & Folders",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.Black)
                }
            }

            // Tags Row
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.tags) { tag ->
                    val isSelected = state.selectedTag == tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color.Black else Color.White)
                            .clickable { viewModel.selectTag(tag) }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = tag,
                            color = if (isSelected) Color.White else Color.Black,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File List
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(state.items) { item ->
                    if (item.isFolder) {
                        FolderItem(item = item, onClick = { /* Navigate into folder */ })
                    } else {
                        FileListItem(
                            item = item,
                            onClick = { onDocumentClick(item.id) }
                        )
                    }
                }
                
                // Add dummy items if empty for UI showcasing
                if (state.items.isEmpty()) {
                    item { FolderItem(FileItem("d1", "Taxes 2024", true, 4), {}) }
                    item { FolderItem(FileItem("d2", "Travel Documents", true, 2), {}) }
                    item { FileListItem(FileItem("d3", "Contract_final.pdf", false), {}) }
                }
            }
        }
    }
}

@Composable
fun FolderItem(item: FileItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEBE7CE))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Folder, contentDescription = "Folder", tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                Text("${item.itemCount} items", fontSize = 13.sp, color = Color.Black.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun FileListItem(item: FileItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF7F8F8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Description, contentDescription = "File", tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                Text("PDF • Just now", fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}
