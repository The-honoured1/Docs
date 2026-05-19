package com.ceo3.docs.ui.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Sync
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class HomeUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val searchResults: List<DocumentEntity> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false
)

class HomeViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(application).documentDao()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        refreshDocuments()
    }

    fun refreshDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllDocuments().collectLatest { dbList ->
                if (dbList.isEmpty()) {
                    seedDefaultDocuments()
                    return@collectLatest
                }
                val combined = dbList.sortedByDescending { it.lastModified }
                _uiState.value = _uiState.value.copy(documents = combined)
                if (_uiState.value.searchQuery.isNotEmpty()) {
                    performSearch(_uiState.value.searchQuery)
                }
            }
        }
    }

    private fun seedDefaultDocuments() {
        val context = getApplication<android.app.Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            // 1. Project Proposal.docx
            val file1 = File(context.filesDir, "doc_project_proposal.docx")
            if (!file1.exists()) {
                file1.writeText("""# Project Proposal

## 1. Introduction
This proposal outlines the scope, objectives, and implementation plan for our project. Our goal is to deliver a scalable and efficient solution that meets your business needs.

## 2. Objectives
- Improve operational efficiency
- Reduce costs by 20%
- Enhance user experience
- Ensure scalability and security

## 3. Timeline
The project will be completed in the following phases:""")
            }
            val doc1 = DocumentEntity(
                id = "project_proposal",
                title = "Project Proposal",
                type = "DOCX",
                lastModified = now,
                isPinned = true,
                tags = "Work,Marketing,Important",
                accentTheme = "classic",
                accentColor = "blue"
            )
            dao.insertDocument(doc1)

            // 2. Q2 Financial Report.pdf
            val file2 = File(context.filesDir, "doc_q2_financial_report.pdf")
            if (!file2.exists()) {
                file2.writeText("Q2 Financial Report Content Placeholder")
            }
            val doc2 = DocumentEntity(
                id = "q2_financial_report",
                title = "Q2 Financial Report",
                type = "PDF",
                lastModified = now - 2 * 3600 * 1000,
                isPinned = false,
                tags = "Finance,2024",
                accentTheme = "classic",
                accentColor = "blue"
            )
            dao.insertDocument(doc2)

            // 3. Budget Overview.xlsx
            val file3 = File(context.filesDir, "doc_budget_overview.xlsx")
            if (!file3.exists()) {
                file3.writeText("Budget Overview spreadsheet content")
            }
            val doc3 = DocumentEntity(
                id = "budget_overview",
                title = "Budget Overview",
                type = "XLSX",
                lastModified = now - 24 * 3600 * 1000,
                isPinned = false,
                tags = "Work,Finance",
                accentTheme = "classic",
                accentColor = "blue"
            )
            dao.insertDocument(doc3)

            // 4. Meeting Notes.txt
            val file4 = File(context.filesDir, "doc_meeting_notes.txt")
            if (!file4.exists()) {
                file4.writeText("""# Meeting Notes

- Review previous action items and status
- Product & Design review of document styling themes
- QA report on ML Kit OCR reliability
- Open forum and blockers""")
            }
            val doc4 = DocumentEntity(
                id = "meeting_notes",
                title = "Meeting Notes",
                type = "TXT",
                lastModified = now - 2 * 24 * 3600 * 1000,
                isPinned = false,
                tags = "Personal,Marketing",
                accentTheme = "classic",
                accentColor = "blue"
            )
            dao.insertDocument(doc4)
        }
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

    fun createBlankDocument(onComplete: (String) -> Unit) {
        val context = getApplication<android.app.Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val docId = UUID.randomUUID().toString()
            val file = File(context.filesDir, "doc_${docId}.docx")
            try {
                file.writeText("# Untitled Document\n\nStart typing here...")
                val entity = DocumentEntity(
                    id = docId,
                    title = "Untitled Document",
                    type = "DOCX",
                    lastModified = System.currentTimeMillis(),
                    isPinned = false,
                    tags = "Work",
                    accentTheme = "classic",
                    accentColor = "blue"
                )
                dao.insertDocument(entity)
                withContext(Dispatchers.Main) {
                    onComplete(docId)
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to create blank document", e)
            }
        }
    }

    fun importFileFromUri(uri: Uri, fileName: String, extension: String, onComplete: (String) -> Unit) {
        val context = getApplication<android.app.Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = UUID.randomUUID().toString()
                val file = File(context.filesDir, "doc_${docId}.${extension.lowercase()}")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                val entity = DocumentEntity(
                    id = docId,
                    title = fileName.substringBeforeLast("."),
                    type = extension.uppercase(),
                    lastModified = System.currentTimeMillis(),
                    isPinned = false,
                    tags = "External",
                    accentTheme = "classic",
                    accentColor = "blue"
                )
                dao.insertDocument(entity)
                withContext(Dispatchers.Main) {
                    onComplete(docId)
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to import file", e)
            }
        }
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
    var showPdfToolsSheet by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
                var name: String? = null
                if (uri.scheme == "content") {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index != -1) name = it.getString(index)
                        }
                    }
                }
                if (name == null) {
                    name = uri.path
                    val cut = name?.lastIndexOf('/') ?: -1
                    if (cut != -1) name = name?.substring(cut + 1)
                }
                return name
            }

            val fileName = getFileNameFromUri(context, selectedUri) ?: "imported_document.docx"
            val extension = fileName.substringAfterLast(".", "docx")
            viewModel.importFileFromUri(selectedUri, fileName, extension) { newId ->
                onNavigateToEditor(newId)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // --- Header Section ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 56.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Good morning, Alex",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Edit, create, and manage your documents",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Synced pill status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFE6F4EA))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = "Synced",
                                tint = Color(0xFF137333),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Synced",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF137333)
                            )
                        }

                        // User profile picture placeholder
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE5E7EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AM",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }
                }
            }

            // --- Search Bar ---
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = {
                        Text(
                            "Search documents",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 20.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                )
            }

            // --- Content Switching ---
            if (state.isSearching) {
                item {
                    Text(
                        text = "Search Results · ${state.searchResults.size} found",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                    )
                }

                if (state.searchResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No documents found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(state.searchResults) { doc ->
                        DocumentRowItem(doc = doc, onClick = { onNavigateToEditor(doc.id) })
                    }
                }
            } else {
                // --- Recent Documents ---
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Documents",
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
                                Toast.makeText(context, "Explore Files tab for all documents", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                if (state.documents.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    items(state.documents.take(4)) { doc ->
                        DocumentRowItem(doc = doc, onClick = { onNavigateToEditor(doc.id) })
                    }
                }

                // --- Quick Actions ---
                item {
                    Text(
                        text = "Quick Actions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 20.dp, bottom = 12.dp)
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            QuickActionCard(
                                title = "New Doc",
                                description = "Create a blank document",
                                icon = Icons.Filled.Add,
                                iconBg = Color(0xFFE8F0FE),
                                iconTint = Color(0xFF1A73E8),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.createBlankDocument { newId ->
                                        onNavigateToEditor(newId)
                                    }
                                }
                            )
                            QuickActionCard(
                                title = "Scan to Text",
                                description = "Scan and extract text",
                                icon = Icons.Filled.DocumentScanner,
                                iconBg = Color(0xFFE6F4EA),
                                iconTint = Color(0xFF137333),
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToScanner
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            QuickActionCard(
                                title = "PDF Tools",
                                description = "Convert, merge, compress",
                                icon = Icons.Filled.PictureAsPdf,
                                iconBg = Color(0xFFFCE8E6),
                                iconTint = Color(0xFFC5221F),
                                modifier = Modifier.weight(1f),
                                onClick = { showPdfToolsSheet = true }
                            )
                            QuickActionCard(
                                title = "Import File",
                                description = "Import from device or cloud",
                                icon = Icons.Filled.CloudUpload,
                                iconBg = Color(0xFFF3E8FF),
                                iconTint = Color(0xFF7C3AED),
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
                }
            }
        }

        // --- PDF Tools Dialog / Bottom Sheet Simulation ---
        if (showPdfToolsSheet) {
            AlertDialog(
                onDismissRequest = { showPdfToolsSheet = false },
                title = { Text("PDF Tools") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select a utility:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPdfToolsSheet = false
                                    Toast.makeText(context, "Merge PDFs selected", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Merge, contentDescription = null, tint = AccentRose)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Merge PDFs", fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPdfToolsSheet = false
                                    Toast.makeText(context, "Compress PDF selected", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Compress, contentDescription = null, tint = AccentRose)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Compress PDF size", fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPdfToolsSheet = false
                                    Toast.makeText(context, "Convert to Word selected", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = AccentRose)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Convert PDF to Word", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPdfToolsSheet = false }) {
                        Text("Close")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun DocumentRowItem(
    doc: DocumentEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val (icon, iconBg, iconTint) = when (doc.type.uppercase()) {
        "PDF"        -> Triple(Icons.Filled.PictureAsPdf, AccentRose.copy(alpha = 0.12f), AccentRose)
        "DOCX","DOC" -> Triple(Icons.Filled.Description, AccentSky.copy(alpha = 0.12f), AccentSky)
        "XLSX","XLS" -> Triple(Icons.Filled.GridOn, AccentEmerald.copy(alpha = 0.12f), AccentEmerald)
        else         -> Triple(Icons.Filled.TextSnippet, AccentAmber.copy(alpha = 0.12f), AccentAmber)
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
            .padding(horizontal = 24.dp, vertical = 6.dp)
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
        // Doc type icon container
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

        // Title and description
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
                text = "Edited $timeLabel · $sizeLabel",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Relative time label and options
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = timeLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            IconButton(
                onClick = {
                    Toast.makeText(context, "Options for ${doc.title}", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
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

@Composable
fun QuickActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
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
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = description,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 2,
                lineHeight = 12.sp,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp)
        )
    }
}
