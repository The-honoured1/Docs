package com.ceo3.docs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                // In a real app, copy this file to internal storage and save to DB
                // For now, we mock navigating to editor
                onNavigateToEditor("imported_doc")
            }
        }
    )

    Scaffold(
        containerColor = Color(0xFFF7F8F8)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline, 
                    contentDescription = "Help", 
                    modifier = Modifier.size(28.dp).clickable { }
                )
                Box {
                    Icon(
                        imageVector = Icons.Outlined.Notifications, 
                        contentDescription = "Notifications", 
                        modifier = Modifier.size(28.dp).clickable { }
                    )
                    // Notification badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                }
            }

            // Grid Layout
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        modifier = Modifier.weight(1f).aspectRatio(0.85f),
                        title = "Scan",
                        subtitle = "Documents, ID card, Measure, Count, Passport...",
                        icon = Icons.Filled.DocumentScanner,
                        backgroundColor = Color(0xFFEBE7CE),
                        onClick = onNavigateToScanner
                    )
                    ActionCard(
                        modifier = Modifier.weight(1f).aspectRatio(0.85f),
                        title = "Edit",
                        subtitle = "Sign, Add text, Add images, Markup, Hide, Recognize...",
                        icon = Icons.Filled.EditSquare,
                        backgroundColor = Color(0xFFFFDF70),
                        onClick = { documentPickerLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain")) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        modifier = Modifier.weight(1f).aspectRatio(0.85f),
                        title = "Convert",
                        subtitle = "pdf, jpg, doc, txt, xls, ppt",
                        icon = Icons.Filled.Output,
                        backgroundColor = Color(0xFFBCE3A6),
                        onClick = { documentPickerLauncher.launch(arrayOf("*/*")) }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("Search", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true
                )
                
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB84D))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = "Filter", tint = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            // Recent files list mock data for UI visual completion
            val displayDocs = if (state.recentDocs.isNotEmpty()) {
                state.recentDocs
            } else {
                listOf(
                    DocumentModel("1", "Strategy-Pitch-Final.xls", "XLS", "Just now", false),
                    DocumentModel("2", "user-journey-01.jpg", "JPG", "1h ago", false),
                    DocumentModel("3", "Invoice-oct-2024.doc", "DOC", "2h ago", false)
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(displayDocs) { doc ->
                    RecentFileCard(doc = doc, onClick = { onNavigateToEditor(doc.id) })
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.Black)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color.Black.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RecentFileCard(doc: DocumentModel, onClick: () -> Unit) {
    val bgColor = when (doc.type.uppercase()) {
        "XLS", "XLSX" -> Color(0xFFF3F0E6)
        "JPG", "PNG" -> Color(0xFFEEF6E8)
        "DOC", "DOCX" -> Color(0xFFFDF7E3)
        else -> Color.White
    }
    
    val iconColor = when (doc.type.uppercase()) {
        "XLS", "XLSX" -> Color(0xFFA19C83)
        "JPG", "PNG" -> Color(0xFF7CB342)
        "DOC", "DOCX" -> Color(0xFFFFB300)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .width(130.dp)
            .height(150.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = doc.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = doc.type,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}
