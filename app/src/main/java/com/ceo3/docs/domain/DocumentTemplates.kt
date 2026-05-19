package com.ceo3.docs.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class TemplateItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val bgColor: Color,
    val defaultText: String
)

object DocumentTemplates {
    val templates = listOf(
        TemplateItem(
            id = "resume",
            title = "Resume",
            description = "Professional curriculum vitae layout",
            icon = Icons.Filled.Badge,
            iconColor = Color(0xFF3B82F6), // Blue
            bgColor = Color(0xFFEFF6FF),
            defaultText = """# [Your Full Name]
[Job Title / Profession]
[Email Address] | [Phone Number] | [Location/LinkedIn]

## PROFESSIONAL SUMMARY
A concise, high-impact summary of your career objectives, key achievements, and core strengths. Keep it to 3-4 sentences. E.g., "Results-driven software engineer with 5+ years of experience specializing in building responsive mobile architectures and optimizing application performance."

## WORK EXPERIENCE
### Senior Software Engineer - Tech Solutions Inc.
*June 2024 - Present | San Francisco, CA*
- Designed and implemented clean Jetpack Compose layouts, reducing codebase size by 25%.
- Streamlined database migrations using Room, ensuring 100% data integrity for over 1M active users.
- Led a cross-functional team of 6 engineers to release high-juice user features ahead of schedule.

### Mobile Developer - Innovation Labs LLC
*March 2022 - May 2024 | Boston, MA*
- Built modular application components in Kotlin, improving unit test coverage to 85%.
- Integrated local machine learning services to speed up document image processing by 40%.
- Fixed critical navigation memory leaks, improving session retention times.

## EDUCATION
### Bachelor of Science in Computer Science
*University of Engineering | Graduated 2022*
- Academic Honors: Cum Laude.
- Relevant Coursework: Data Structures, Advanced Mobile Systems, Software Architecture.

## TECHNICAL SKILLS
- **Languages**: Kotlin, Java, Swift, Python, SQL
- **UI & Architecture**: Jetpack Compose, Material Design 3, MVVM, Clean Architecture
- **Libraries**: Room Database, Retrofit, Coroutines, ML Kit OCR
- **Tools**: Android Studio, Git, CI/CD Pipelines, Docker
"""
        ),
        TemplateItem(
            id = "report",
            title = "Report",
            description = "Detailed project or progress report",
            icon = Icons.Filled.Assignment,
            iconColor = Color(0xFFEF4444), // Rose
            bgColor = Color(0xFFFEF2F2),
            defaultText = """# PROJECT PROGRESS REPORT
**Date**: ${java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}
**Prepared By**: [Your Name]
**Project Name**: Docs Application Redesign
**Status**: [Green / Yellow / Red]

## 1. EXECUTIVE SUMMARY
This report outlines key accomplishments, current challenges, and next milestones in the development of our redesigned Docs application. Over the past sprint, the team focused on polishing the UI/UX animations and introducing advanced AI document assistants.

## 2. KEY MILESTONES COMPLETED
- **Aesthetics & UI Polish**: Successfully migrated all navigation headers to dynamic, high-juice gradients and rounded corner geometries.
- **Speed & Navigation**: Resolved top bar back navigation references to allow fluid screen transitions.
- **AI Tools Integration**: Enabled offline document reading through Android TTS and added a Gemini API connection panel.

## 3. KEY METRICS & KPI STATUS
- **App Startup Speed**: 1.2s (Improved from 1.8s, target: < 1.5s)
- **User Satisfaction Score**: 4.8/5.0 (Target: > 4.5)
- **Code Lint Quality**: Pass (Zero warnings in core module)

## 4. CHALLENGES & BLOCKERS
- **External File Permissions**: Managing scoped storage limits on Android 13+ devices requires runtime checks which were added.
- **OCR Memory Usage**: Multi-page document scan flows sometimes experience GC pressure. Mitigation is in testing.

## 5. NEXT STEPS & SPRINT GOALS
- Implement new Templates Library for common document generation.
- Integrate one-tap color theme styling for txt/markdown files.
- Deliver beta release build for QA team evaluation.
"""
        ),
        TemplateItem(
            id = "notes",
            title = "Meeting Notes",
            description = "Agenda, discussion points, action items",
            icon = Icons.Filled.Groups,
            iconColor = Color(0xFFF59E0B), // Amber
            bgColor = Color(0xFFFFFBEB),
            defaultText = """# WEEKLY SYNC MEETING NOTES
**Date**: ${java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}
**Time**: [10:00 AM - 11:00 AM]
**Facilitator**: [Facilitator Name]

## MEETING PARTICIPANTS
- [Participant A]
- [Participant B]
- [Participant C]

## MEETING AGENDA
1. Review previous action items and status
2. Product & Design review of document styling themes
3. QA report on ML Kit OCR reliability
4. Open forum and blockers

## KEY DISCUSSION POINTS
- **Database & Architecture**: The team verified that adding fields to `DocumentEntity` does not break existing local document indexes.
- **Design Decisions**: Approved the 5 default design theme specs: Classic Navy, Modern Mint, Warm Sepia, Charcoal Slate, and Royal Ruby.
- **Release Schedule**: Target release date for the templates update is set for Friday.

## ACTION ITEMS & TASKS
- [ ] **[Name]**: Integrate template cards into the HomeScreen.
- [ ] **[Name]**: Complete the visual Markdown text parser in EditorScreen.
- [ ] **[Name]**: Test PDF exports with custom color margins.
- [ ] **[Name]**: Verify TTS read-aloud works on styled txt views.
"""
        ),
        TemplateItem(
            id = "blank",
            title = "Blank Doc",
            description = "Start a fresh document from scratch",
            icon = Icons.Filled.Description,
            iconColor = Color(0xFF8B5CF6), // Violet
            bgColor = Color(0xFFF5F3FF),
            defaultText = """# New Document
Start typing your document content here...
"""
        )
    )
}
