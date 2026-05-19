package com.ceo3.docs.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class TemplateItem(
    val title: String,
    val description: String,
    val type: String, // "DOCX", "TXT", "PDF"
    val category: String, // "Resume & CV", "Business", "Reports"
    val iconColor: Color,
    val content: String
)

class TemplatesViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(application).documentDao()

    fun createDocumentFromTemplate(template: TemplateItem, onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val docId = UUID.randomUUID().toString()
            val context = getApplication<android.app.Application>()
            val file = File(context.filesDir, "doc_${docId}.${template.type.lowercase()}")
            try {
                file.writeText(template.content)
                val entity = DocumentEntity(
                    id = docId,
                    title = template.title,
                    type = template.type,
                    lastModified = System.currentTimeMillis(),
                    isPinned = false,
                    tags = when (template.category) {
                        "Resume & CV" -> "Resume"
                        "Business" -> "Work"
                        "Reports" -> "Report"
                        else -> "General"
                    },
                    accentTheme = "classic",
                    accentColor = when (template.category) {
                        "Resume & CV" -> "indigo"
                        "Business" -> "emerald"
                        "Reports" -> "rose"
                        else -> "blue"
                    }
                )
                dao.insertDocument(entity)
                withContext(Dispatchers.Main) {
                    onComplete(docId)
                }
            } catch (e: Exception) {
                // error handling
            }
        }
    }

    fun createBlankDocument(onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val docId = UUID.randomUUID().toString()
            val context = getApplication<android.app.Application>()
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
                // error handling
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onNavigateToEditor: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TemplatesViewModel = viewModel()
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var isCreating by remember { mutableStateOf(false) }

    val categories = listOf("All", "Resume & CV", "Business", "Reports")

    val templates = remember {
        listOf(
            TemplateItem(
                title = "Professional Resume",
                description = "Clean, modern resume template for software developers and designers.",
                type = "DOCX",
                category = "Resume & CV",
                iconColor = Color(0xFF3B82F6),
                content = """# John Doe - Software Engineer
john.doe@email.com | +1 123 456 7890 | github.com/johndoe

## Professional Summary
Detail-oriented and passionate Software Engineer with 5+ years of experience building scalable web applications. Proficient in Kotlin, Java, and modern mobile/web architectures.

## Experience
### Senior Software Engineer at TechCorp | 2023 - Present
- Led a team of 4 engineers to design and implement a new microservices architecture.
- Reduced API response latency by 35% through database optimization and caching strategies.
- Mentored junior developers and instituted code review best practices.

### Software Engineer at DevSystems | 2021 - 2023
- Developed and maintained multiple customer-facing Android applications.
- Collaborated with UI/UX designers to redesign the user onboarding workflow.

## Education
- B.S. in Computer Science, University of Technology | 2017 - 2021"""
            ),
            TemplateItem(
                title = "Academic CV",
                description = "Formal curriculum vitae layout suitable for researchers and professors.",
                type = "DOCX",
                category = "Resume & CV",
                iconColor = Color(0xFF8B5CF6),
                content = """# Dr. Sarah Jenkins
s.jenkins@university.edu | (555) 019-2834 | Dept of Computer Science

## Education
- Ph.D. in Computer Science, Stanford University, 2020
  Dissertation: "Machine Learning Optimization in Distributed Systems"
- M.S. in Computer Science, Massachusetts Institute of Technology, 2016
- B.S. in Mathematics, University of Chicago, 2014

## Research Interests
Distributed Systems, Artificial Intelligence, High-Performance Computing.

## Publications
- Jenkins, S., & Carter, L. (2022). "Adaptive Scheduling in Cluster Architectures." *Journal of Cloud Computing*, 14(2), 112-128.
- Jenkins, S. (2020). "Distributed Gradient Descent at Scale." *Conference on Systems and Machine Learning*.

## Teaching Experience
- Assistant Professor, State University (2021 - Present)
- Graduate Teaching Assistant, Stanford University (2017 - 2020)"""
            ),
            TemplateItem(
                title = "Business Invoice",
                description = "Standard service invoice template with details, tables, and totals.",
                type = "DOCX",
                category = "Business",
                iconColor = Color(0xFF10B981),
                content = """# INVOICE

**Invoice Number:** INV-2026-001  
**Date:** May 19, 2026  
**Due Date:** June 19, 2026  

---

### From:
**Acme Consulting LLC**  
123 Enterprise Way, Suite 400  
tech@acmeconsulting.io  

### To:
**Client Corporation**  
456 Commerce Boulevard  
billing@clientcorp.com  

---

### Items & Services
| Description | Qty | Unit Price | Total |
| :--- | :--- | :--- | :--- |
| Cloud Infrastructure Migration | 40 hrs | ${'$'}120.00 | ${'$'}4,800.00 |
| Database Performance Tuning | 10 hrs | ${'$'}150.00 | ${'$'}1,500.00 |
| System Architecture Review | 1 | ${'$'}1,200.00 | ${'$'}1,200.00 |

### Summary
- **Subtotal:** ${'$'}7,500.00  
- **Tax (0%):** ${'$'}0.00  
- **Total Due:** **${'$'}7,500.00 USD**  

*Thank you for your business! Please send payment via wire transfer to the details provided in our contract.*"""
            ),
            TemplateItem(
                title = "Project Proposal",
                description = "Structured business project proposal document to win client deals.",
                type = "DOCX",
                category = "Business",
                iconColor = Color(0xFFF59E0B),
                content = """# PROJECT PROPOSAL
**Project Name:** Customer Portal Redevelopment  
**Proposed By:** Tech Solutions Group  
**Date:** May 19, 2026  

---

## 1. Executive Summary
This proposal outlines the strategy to redesign and re-engineer our customer portal. The current system suffers from latency issues and outdated design, causing drop-offs. A modern, mobile-friendly React and Next.js portal will solve these issues.

## 2. Objectives
- Improve load speed by 50%.
- Enable complete self-service for account management.
- Decrease support ticket volume by 20% within 3 months.

## 3. Scope of Work
- **Phase 1: Discovery & UX Design** (Weeks 1-4)
- **Phase 2: Core Engineering & Backend API Integration** (Weeks 5-12)
- **Phase 3: QA & User Acceptance Testing** (Weeks 13-16)

## 4. Resource Allocation
- 1 Lead Architect
- 2 Frontend Developers
- 1 QA Engineer"""
            ),
            TemplateItem(
                title = "Annual Report",
                description = "Corporate annual status report template detailing achievements.",
                type = "DOCX",
                category = "Reports",
                iconColor = Color(0xFFEF4444),
                content = """# ANNUAL REPORT 2025
**Company:** Global Tech Dynamics Inc.  
**Reporting Period:** Jan 1, 2025 - Dec 31, 2025  

---

## Executive Summary
2025 was a record-breaking year for Global Tech Dynamics. Through key product launches and international expansion, our company realized substantial market share gains and record profitability.

## Financial Highlights
- **Total Revenue:** ${'$'}48.2 Million (+18% YoY)
- **Net Income:** ${'$'}8.6 Million (+22% YoY)
- **EBITDA:** ${'$'}12.4 Million
- **Operating Margin:** 24.8%

## Key Achievements
1. **Launch of CloudPlatform 2.0:** Adoption surpassed expectations, with over 500 enterprise clients migrating in Q3 alone.
2. **Expansion to APAC Region:** Established headquarters in Singapore, generating ${'$'}4.5M in regional revenue.
3. **Sustainability Initiative:** Transitioned 100% of our server farms to renewable energy sources ahead of schedule.

## Future Outlook
In 2026, we aim to accelerate investments in artificial intelligence features and scale our enterprise sales organization."""
            )
        )
    }

    val filteredTemplates = templates.filter { item ->
        val matchesSearch = item.title.contains(searchQuery, ignoreCase = true) ||
                item.description.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == "All" || item.category == selectedCategory
        matchesSearch && matchesCategory
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Template Library",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // --- Search Bar ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search templates...",
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                )

                // --- Category Tabs ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val selected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = category,
                                color = if (selected) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // --- Blank Document Action Card ---
                if (searchQuery.isEmpty() && (selectedCategory == "All" || selectedCategory == "Business")) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                isCreating = true
                                viewModel.createBlankDocument { newId ->
                                    isCreating = false
                                    onNavigateToEditor(newId)
                                }
                            }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Start with a Blank Document",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Create a clean DOCX document from scratch.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // --- Templates Grid ---
                if (filteredTemplates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No templates match your search.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(filteredTemplates) { template ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        isCreating = true
                                        viewModel.createDocumentFromTemplate(template) { docId ->
                                            isCreating = false
                                            onNavigateToEditor(docId)
                                        }
                                    }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    // Simulated document mockup top layout
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BackgroundLight)
                                            .padding(8.dp)
                                    ) {
                                        // Visual simulation of a page
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.7f)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(template.iconColor)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.4f)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(template.iconColor.copy(alpha = 0.4f))
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            repeat(4) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(2.dp)
                                                        .background(Color.LightGray.copy(alpha = 0.5f))
                                                )
                                            }
                                        }
                                        // Badges or icons overlay
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(template.iconColor.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = template.type,
                                                color = template.iconColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = template.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = template.description,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Creating Loader Overlay ---
            if (isCreating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(enabled = false) {}, // consume clicks
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Initializing document...", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
