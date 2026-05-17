package com.ceo3.docs.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
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

data class DocumentModel(val id: String, val title: String, val type: String, val lastModified: String, val isPinned: Boolean)

class HomeViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(application).documentDao()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            dao.getAllDocuments().collect { docs ->
                _uiState.value = HomeUiState(
                    pinnedDocs = docs.filter { it.isPinned }.map { it.toModel() },
                    recentDocs = docs.filter { !it.isPinned }.map { it.toModel() }
                )
            }
        }
    }

    private fun com.ceo3.docs.data.local.DocumentEntity.toModel(): DocumentModel {
        return DocumentModel(id, title, type, "Last modified: $lastModified", isPinned)
    }
}

data class HomeUiState(
    val pinnedDocs: List<DocumentModel> = emptyList(),
    val recentDocs: List<DocumentModel> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEditor: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Docs", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* TODO: Search */ }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToScanner,
                icon = { Icon(Icons.Filled.Description, contentDescription = "Scan") },
                text = { Text("Scan") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            if (state.pinnedDocs.isNotEmpty()) {
                item {
                    Text("Pinned", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.pinnedDocs) { doc ->
                            DocumentCard(doc = doc, onClick = { onNavigateToEditor(doc.id) })
                        }
                    }
                }
            }

            item {
                Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(state.recentDocs) { doc ->
                DocumentListItem(doc = doc, onClick = { onNavigateToEditor(doc.id) })
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun DocumentCard(doc: DocumentModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(120.dp, 160.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(doc.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(doc.type, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun DocumentListItem(doc: DocumentModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Description, 
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("${doc.type} • ${doc.lastModified}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
