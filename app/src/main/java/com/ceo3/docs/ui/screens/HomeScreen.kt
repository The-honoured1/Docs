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
import androidx.compose.material.icons.outlined.*
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
    onNavigateToTools: () -> Unit,
    viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                onNavigateToEditor(it.toString())
            }
        }
    )

    Scaffold(
        containerColor = com.ceo3.docs.ui.theme.BackgroundLight
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // Greeting Text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Text(
                    text = "Welcome,",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.Gray
                )
                Text(
                    text = "What would you like\nto do?",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.Black
                )
            }

            // Donation Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .clickable { /* TODO: Open Donation Link */ },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = com.ceo3.docs.ui.theme.AccentPurple)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Donate",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Free Forever, No Ads",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "We monetize through donations for poor children in Africa. Tap to support us.",
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )
                    }
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
                        modifier = Modifier.weight(1f).aspectRatio(0.95f),
                        title = "Scan",
                        subtitle = "Documents, ID cards...",
                        icon = Icons.Outlined.DocumentScanner,
                        backgroundColor = com.ceo3.docs.ui.theme.SoftYellow,
                        onClick = onNavigateToScanner
                    )
                    ActionCard(
                        modifier = Modifier.weight(1f).aspectRatio(0.95f),
                        title = "Edit",
                        subtitle = "Sign, add text, mark...",
                        icon = Icons.Outlined.EditSquare,
                        backgroundColor = com.ceo3.docs.ui.theme.BrightYellow,
                        onClick = { documentPickerLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain")) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        modifier = Modifier.weight(1f).aspectRatio(0.95f),
                        title = "Convert",
                        subtitle = "PDF, DOCX, JPG, TX...",
                        icon = Icons.Outlined.Output,
                        backgroundColor = com.ceo3.docs.ui.theme.SoftGreen,
                        onClick = { documentPickerLauncher.launch(arrayOf("*/*")) }
                    )
                    ActionCard(
                        modifier = Modifier.weight(1f).aspectRatio(0.95f),
                        title = "Tools",
                        subtitle = "Merge, split, compress...",
                        icon = Icons.Outlined.Build,
                        backgroundColor = com.ceo3.docs.ui.theme.SoftOrange,
                        onClick = onNavigateToTools
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp).weight(1f)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (searchQuery.isEmpty()) {
                            Text("Search files", color = Color.Gray, fontSize = 16.sp)
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.Close, 
                        contentDescription = "Clear Search", 
                        tint = Color.Gray,
                        modifier = Modifier
                            .padding(end = 20.dp)
                            .size(20.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            val displayDocs = state.recentDocs.filter { 
                searchQuery.isEmpty() || it.title.contains(searchQuery, ignoreCase = true) 
            }
            
            if (displayDocs.isNotEmpty() || searchQuery.isNotEmpty()) {
                Text(
                    text = "Recent files",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(displayDocs) { doc ->
                        RecentFileCard(doc = doc, onClick = { onNavigateToEditor(doc.id) })
                    }
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
            modifier = Modifier.padding(20.dp).fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color.Black.copy(alpha = 0.6f),
                textAlign = TextAlign.Start,
                lineHeight = 14.sp,
                maxLines = 2,
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
            .width(140.dp)
            .height(180.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = doc.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Start,
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
