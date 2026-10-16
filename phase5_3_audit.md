# PishiToDo — Phase 5.3 Architecture Audit

> **Document Version**: 1.0.0  
> **Status**: COMPLETED ARCHITECTURE AUDIT  
> **Date**: August 2026  
> **Author**: Antigravity Core Architecture Team  
> **Scope**: Advanced Productivity Intelligence Feasibility & System Audit  
> **Baseline State**: Room Schema v6, Backup Format v3, 121 Tests Passing (100%), lintDebug Clean, Release APK ≈ 3.88 MB (< 4MB Budget), 12 Languages, RTL Verified, 100% Offline-First.

---

## 1. Executive Summary

This architecture audit evaluates five potential productivity intelligence capabilities for **PishiToDo Phase 5.3**:
1. **Capability A: Smart Task Suggestions** (Overdue cleanup, unscheduled high-priority detection, neglected tasks, quick-win checklist reminders).
2. **Capability B: Advanced Natural Language Smart Search** (Intent-driven queries in Persian and English without cloud LLMs).
3. **Capability C: Task Dependencies** (Blocked/unblocked task graph).
4. **Capability D: Routine / Habit-like Tasks** (Streak & consistency metrics for recurring tasks).
5. **Capability E: Calendar Intelligence** (Week planning strip, daily load indicators, quick rescheduling).

### Core Audit Verdict:
- **Capabilities A, B, D, and E (selected scope)** are **100% FEASIBLE** with **ZERO database schema migrations** (preserving Room Schema v6), **ZERO backup format breaking changes** (preserving Format v3), **ZERO external cloud/AI dependencies** (100% offline-first), and an estimated combined APK footprint impact of **< 45 KB** (comfortably within the sub-4MB budget).
- **Capability C (Task Dependencies)** is **EXPLICITLY REJECTED / ARCHITECTURALLY DEFERRED**. Reusing `parentTaskId` for dependencies is unsafe, while introducing a directed acyclic graph (DAG) table would force a breaking Room migration (v6 → v7) and Backup format bump (v3 → v4) with minimal personal productivity value.

---

## 2. Comprehensive Codebase Component Audit

The following table summarizes the direct code-level inspection of the 24 required components:

| Component | File Path | Current Role & Architectural State | Phase 5.3 Impact & Readiness |
| :--- | :--- | :--- | :--- |
| **`TaskEntity`** | `data/local/entity/TaskEntity.kt` | Room table `tasks` (v6). Fields: `id`, `title`, `description`, `notes`, `isCompleted`, `completedAt`, `priority`, `categoryId`, `parentTaskId`, `dueDate`, `reminderTime`, `orderIndex`, `recurrenceRule`, `recurrenceAnchor`, `createdAt`, `updatedAt`. | **NO CHANGE**. All proposed intelligence features derive signals from these existing fields. |
| **`Task`** | `domain/model/Task.kt` | Core domain model. Contains `subtaskCount`, `completedSubtaskCount`, `tags`, `subtaskProgress`, `isAllSubtasksCompleted`. | **NO CHANGE / EXTENSIBLE**. Safe for adding non-persisted domain computed properties if needed. |
| **`TaskDao`** | `data/local/dao/TaskDao.kt` | Correlated Room subqueries for subtask counts; text search queries with Persian/Arabic normalizations. | **NO CHANGE**. Search and filtering use existing reactive flows. |
| **`TaskRepository`** | `domain/repository/TaskRepository.kt` | Repository contract exposing reactive `Flow<List<Task>>`, raw queries, and transactional updates. | **READY**. Directly supports smart filter and suggestion use cases. |
| **`TaskUseCases`** | `domain/usecase/task/TaskUseCases.kt` | Facade data class providing domain use cases to ViewModels. | **EXTEND**. Wire new `GetSmartSuggestionsUseCase` and `GetSmartFilteredTasksUseCase`. |
| **`AppContainer`** | `core/di/AppContainer.kt` | Manual DI container providing repositories, use cases, and ViewModel factories. | **EXTEND**. Instantiate and wire new use cases cleanly. |
| **`TaskListViewModel`** | `ui/screens/tasklist/TaskListViewModel.kt` | Orchestrates search, filter chips, smart filter counts, sorting, and task mutations. | **EXTEND**. Consume smart search and suggestion flows. |
| **`TaskListScreen`** | `ui/screens/tasklist/TaskListScreen.kt` | Main list UI with search bar, smart filter chips, FAB, task items, and swipe actions. | **EXTEND**. Render dismissible smart suggestion banner/carousel and NLP search feedback chips. |
| **`TaskDetailViewModel`** | `ui/screens/taskdetail/TaskDetailViewModel.kt` | Manages single-task observation, subtasks, attachments, and completions. | **EXTEND**. Provide routine streak insights for recurring tasks. |
| **`TaskDetailScreen`** | `ui/screens/taskdetail/TaskDetailScreen.kt` | Task details, checklist card with progress bar, attachments, notes, and actions. | **EXTEND**. Display recurring routine streak badge. |
| **`TaskEditorViewModel`** | `ui/screens/taskeditor/TaskEditorViewModel.kt` | Validates task creation/editing, tag toggles, date/time pickers. | **READY**. No breaking modifications required. |
| **`TaskEditorScreen`** | `ui/screens/taskeditor/TaskEditorScreen.kt` | Editor UI with priority selector, checklist builder, tags, recurrence, and reminders. | **READY**. Supports all current and planned inputs. |
| **`CalendarViewModel`** | `ui/screens/calendar/CalendarViewModel.kt` | Manages selected date, current month, and task streams. | **EXTEND**. Add week planning state and daily density calculation. |
| **`CalendarScreen`** | `ui/screens/calendar/CalendarScreen.kt` | Month grid with Jalali/Gregorian conversion and day task stream. | **EXTEND**. Add 7-day horizontal planning strip and load density indicators. |
| **`EisenhowerMatrixViewModel`** | `ui/screens/matrix/EisenhowerMatrixViewModel.kt` | Partitions tasks into Q1-Q4 quadrants with 1-day snooze and completion actions. | **READY**. Perfectly complements smart suggestions. |
| **`EisenhowerMatrixScreen`** | `ui/screens/matrix/EisenhowerMatrixScreen.kt` | 2x2 grid view with priority/urgency segmentation and empty states. | **READY**. High stability, no regressions. |
| **`RecurrenceEngine`** | `domain/util/RecurrenceEngine.kt` | Anchor-based recurrence calculation with month-end and leap-year clamping. | **NO DEFECTS FOUND**. Completely verified and intact. |
| **`ReminderManager`** | `worker/reminder/ReminderManager.kt` | WorkManager scheduling/cancellation for exact/inexact notifications. | **READY**. Pure decision model is safe. |
| **`ReminderWorker`** | `worker/reminder/ReminderWorker.kt` | Pure decision function `shouldNotify(task)` for notification trigger. | **READY**. Unchanged. |
| **`LocalTaskParser`** | `domain/parser/LocalTaskParser.kt` | Language router (Persian vs. English) and confidence scoring engine. | **EXTEND / REUSE**. Reuse token extractors for smart search query parsing. |
| **`PersianTaskParser`** | `domain/parser/persian/PersianTaskParser.kt` | Persian NLP parser (Jalali dates, relative days, times, priorities, tags, checklist). | **REUSE**. Export token matching helpers for Persian smart search. |
| **`EnglishTaskParser`** | `domain/parser/english/EnglishTaskParser.kt` | English NLP parser (ISO/relative dates, 12h/24h times, priorities, tags, checklist). | **REUSE**. Export token matching helpers for English smart search. |
| **`ParsedTask`** | `domain/model/ParsedTask.kt` | Data transfer object for NLP-extracted task parameters. | **EXTEND**. Can encapsulate search intent parameters. |
| **`SmartTaskPreviewCard`** | `ui/components/SmartTaskPreviewCard.kt` | Composable card rendering live NLP extraction chips with 1-tap edits. | **READY**. Benchmark pattern for suggestion cards. |
| **`BackupUseCases`** | `domain/usecase/backup/BackupUseCases.kt` | Versioned v3 JSON export/import with 2-pass ID remapping and cycle checks. | **PROTECTED**. No changes; v3 format preserved. |
| **`PishiDatabase`** | `data/local/PishiDatabase.kt` | Room database (Version 6). SQLite migrations v1→v6. | **PROTECTED**. Schema v6 and migrations remain frozen. |

---

## 3. Detailed Investigation of Candidate Capabilities

### 3.1 Candidate A: Smart Task Suggestions (Recommended P0)

#### Architectural Mechanism:
Smart task suggestions analyze uncompleted tasks reactively on `Dispatchers.Default` using deterministic scoring heuristics:
1. **Overdue Triage**: Active tasks where `dueDate < startOfToday`. Action: Snooze to today, snooze +1 day, or mark complete.
2. **Unscheduled High-Priority**: Active tasks with `priority == HIGH` and `dueDate == null` created > 2 days ago. Action: Set due date to today/tomorrow.
3. **Stale / Neglected Tasks**: Active tasks with no updates in > 14 days. Action: Review, reschedule, or eliminate.
4. **Wrap-up Checklists**: Tasks with `subtaskCount >= 3` and `completedSubtaskCount >= (subtaskCount - 1)`. Action: Quick-open to complete final step.

#### Evaluation against 13 Key Questions:
1. *Schema change?* **No**. Computed purely from existing attributes (`dueDate`, `createdAt`, `updatedAt`, `priority`, `subtaskCount`, `completedSubtaskCount`).
2. *Backup v3 affected?* **No**. In-memory derived state.
3. *Migrations affected?* **No**.
4. *APK size impact?* **~12 KB** (Domain model, use case, Compose suggestion item/carousel).
5. *New dependency?* **None**.
6. *100% offline?* **Yes**.
7. *Reuse existing domain?* **Yes** (`TaskRepository`, `TaskUseCases`, `RescheduleTaskUseCase`).
8. *Business logic in ViewModels?* **No**. Entire evaluation encapsulated in `GetSmartSuggestionsUseCase`.
9. *Localization keys?* **Yes** (~8 new keys with 100% parity across 12 languages).
10. *RTL impact?* Standard Compose mirroring for suggestion cards.
11. *Edge cases:* Zero uncompleted tasks, all tasks completed, dismissing suggestions (cached session-level dismiss list in DataStore or ViewModel memory).
12. *Tests required:* Unit tests for each suggestion rule, prioritization ordering, and dismiss logic.
13. *Worth complexity?* **High Value, Low Complexity**.

---

### 3.2 Candidate B: Advanced Smart Search (Natural Language Queries) (Recommended P0)

#### Architectural Mechanism:
Transforms the search bar into an intelligent natural-language query engine using local token matching (zero cloud AI):
- English examples:
  - `important tasks this week` → `TaskFilter(priorities = [HIGH], dateFilter = THIS_WEEK)`
  - `overdue tasks` → `TaskFilter(status = OVERDUE)`
  - `shopping tasks` → `searchQuery = "shopping"`
  - `today's tasks` → `TaskFilter(dateFilter = TODAY)`
  - `recurring tasks` → `TaskFilter(isRecurringOnly = true)`
  - `tasks without a due date` → `TaskFilter(dateFilter = NO_DATE)`
- Persian examples:
  - `کارهای مهم این هفته` → `TaskFilter(priorities = [HIGH], dateFilter = THIS_WEEK)`
  - `کارهای عقب افتاده` → `TaskFilter(status = OVERDUE)`
  - `کارهای خرید` → `searchQuery = "خرید"`
  - `کارهای امروز` → `TaskFilter(dateFilter = TODAY)`
  - `کارهای تکرارشونده` → `TaskFilter(isRecurringOnly = true)`
  - `کارهای بدون تاریخ` → `TaskFilter(dateFilter = NO_DATE)`

#### Architectural Pipeline:
```
User Search Input
       │
       ▼
SmartSearchParser (Local Rule Engine)
       │
       ├── Matches Intent Tokens (Date, Priority, Status, Recurrence, Tags)
       └── Extracts Residual Search Query (Keywords)
       │
       ▼
SmartSearchQuery(filter: TaskFilter, keyword: String, detectedChips: List<SearchChip>)
       │
       ▼
GetFilteredTasksUseCase / GetSmartFilteredTasksUseCase
       │
       ▼
Reactive Task Stream Flow<List<Task>>
```

#### Evaluation against 13 Key Questions:
1. *Schema change?* **No**.
2. *Backup v3 affected?* **No**.
3. *Migrations affected?* **No**.
4. *APK size impact?* **~15 KB** (Token parsing tables, intent mapper, UI search chip feedback).
5. *New dependency?* **None**.
6. *100% offline?* **Yes**.
7. *Reuse existing domain?* **Yes** (`GetFilteredTasksUseCase`, `PersianNormalizer`, `DigitNormalizer`).
8. *Business logic in ViewModels?* **No**. Placed in `SmartSearchParser` and `GetSmartFilteredTasksUseCase`.
9. *Localization keys?* **Yes** (~6-10 new string keys for search chip badges).
10. *RTL impact?* Fully mirrors for Persian search chips and query matching.
11. *Edge cases:* Mixed Persian/English queries, partial query matches, empty result fallbacks, pure keyword searches.
12. *Tests required:* Parity tests across all sample queries in English and Persian; fallback keyword matching tests.
13. *Worth complexity?* **High Value, Low Complexity**.

---

### 3.3 Candidate C: Task Dependencies (Explicitly REJECTED)

#### Deep Architectural Analysis:
The audit investigated whether lightweight task dependencies (e.g., Task A blocks Task B) could be supported.

1. **Can `parentTaskId` be reused?**
   - **NO**. `parentTaskId` represents a 1-level parent-child checklist hierarchy.
   - Reusing `parentTaskId` for dependencies causes:
     - **Semantic Collision**: Subtasks would be treated as blockers.
     - **Cascade Delete Destruction**: If Task A blocks Task B, deleting Task A would delete Task B instead of unblocking it.
     - **Subtask Count Pollution**: Task B would increment Task A's subtask count and distort progress indicators.
     - **Backup Format v3 Breakage**: 2-pass import relies on `parentTaskId` representing containment, not arbitrary cross-task DAGs.

2. **Can a dedicated table be added?**
   - A dedicated `task_dependencies (blockerId, dependentId)` table would require:
     - Room migration **v6 → v7**.
     - Backup format bump **v3 → v4**.
     - Runtime cycle-detection algorithms (Tarjan's or Kahn's topological sort).
     - State synchronization (uncompleting a blocker must unblock or re-lock dependents).
   - This violates the core design principle: *Personal task managers must stay light, rapid, and friction-free*.

#### Verdict:
**REJECTED**. Task dependencies introduce enterprise PM bloat, schema migration risks, and backup incompatibility for negligible personal productivity benefit.

---

### 3.4 Candidate D: Routine / Habit-like Tasks (Recommended P1)

#### Architectural Mechanism:
Leverage existing `recurrenceRule`, `recurrenceAnchor`, and `completedAt` to compute lightweight routine consistency without adding habit-tracker bloat:
- **Current Streak Counter**: Number of consecutive recurrence intervals completed on time.
- **Consistency Score**: Percentage of recurrence occurrences completed over the last 30 days.
- **Routines Filter Chip**: Group all recurring active tasks in a dedicated smart filter tab.

#### Evaluation against 13 Key Questions:
1. *Schema change?* **No**.
2. *Backup v3 affected?* **No**.
3. *Migrations affected?* **No**.
4. *APK size impact?* **~8 KB**.
5. *New dependency?* **None**.
6. *100% offline?* **Yes**.
7. *Reuse existing domain?* **Yes** (`RecurrenceEngine`, `Task.completedAt`).
8. *Business logic in ViewModels?* **No**. Encapsulated in `GetTaskStreakUseCase`.
9. *Localization keys?* **Yes** (~4 string keys: `streak_count`, `routines_title`, etc.).
10. *RTL impact?* Mirroring numbers and streak badges.
11. *Edge cases:* Reset streak on missed interval; one-off non-recurring tasks return streak 0.
12. *Tests required:* Unit tests for streak calculation across daily, weekly, monthly, and interval recurrences.
13. *Worth complexity?* **Medium Value, Low Complexity**.

---

### 3.5 Candidate E: Calendar Intelligence (Recommended P1 / P2)

#### Architectural Mechanism:
Enhance the existing `CalendarScreen` with two high-craft, lightweight features:
1. **7-Day Planning Strip (Week View)**: A horizontally scrollable week bar showing dates with completion dots, allowing users to tap across the week without navigating full-month grids.
2. **Daily Task Load Density**: Small indicator dots on calendar day cells (1 dot: light load, 2 dots: moderate, 3 dots / colored dot: heavy load).
3. **1-Tap Quick Reschedule**: Drag or date-picker sheet to reschedule tasks to the selected day.

#### Features Excluded to Prevent Bloat:
- *24-Hour Time-Blocking Grid*: Excluded due to heavy layout calculations, touch collision with Compose list scroll, and clutter on small mobile screens.
- *External Cloud Calendar Sync*: Excluded to preserve 100% offline privacy and zero OAuth/Google Play Services dependencies.

#### Evaluation against 13 Key Questions:
1. *Schema change?* **No**.
2. *Backup v3 affected?* **No**.
3. *Migrations affected?* **No**.
4. *APK size impact?* **~14 KB**.
5. *New dependency?* **None**.
6. *100% offline?* **Yes**.
7. *Reuse existing domain?* **Yes** (`CalendarViewModel`, `JalaliCalendarConverter`, `RescheduleTaskUseCase`).
8. *Business logic in ViewModels?* **No**.
9. *Localization keys?* **Yes** (~4 string keys: `week_view`, `load_light`, etc.).
10. *RTL impact?* Horizontal week pager and calendar cells correctly invert in RTL.
11. *Edge cases:* Month boundaries, leap years, Jalali month transitions.
12. *Tests required:* `CalendarViewModelTest` week navigation and load aggregation tests.
13. *Worth complexity?* **High Value, Low Complexity**.

---

## 4. Verification Suite Results Baseline

The verification suite was run against the unmodified codebase before establishing this audit:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                       BASELINE VERIFICATION EXECUTION                        │
├──────────────────────┬────────────────────────┬──────────────┬───────────────┤
│ Gradle Task          │ Command                │ Exit Code    │ Result Summary│
├──────────────────────┼────────────────────────┼──────────────┼───────────────┤
│ Unit & Arch Tests    │ ./gradlew.bat test     │ 0 (SUCCESS)  │ 121/121 PASS  │
│ Android Lint         │ ./gradlew.bat lintDebug│ 0 (SUCCESS)  │ 0 Errors      │
│ Release Build & R8   │ ./gradlew.bat assemble │ 0 (SUCCESS)  │ 3.88 MB APK   │
└──────────────────────┴────────────────────────┴──────────────┴───────────────┘
```

- **Room Schema Version**: Version 6 (`8b05c9e93e8a62f1c72527712826ec40`).
- **Backup Format**: Version 3.
- **APK Size**: 4,068,254 bytes (~3.88 MB).

---

## 5. Audit Conclusions

1. **Safety & Feasibility**: Phase 5.3 can be built with **0 schema changes**, **0 backup breaking changes**, **0 cloud dependencies**, and **< 45 KB APK footprint**.
2. **Scope Recommendation**:
   - **Phase 5.3.1**: Smart Task Suggestions & Proactive Triage
   - **Phase 5.3.2**: Advanced Natural Language Smart Search (Persian & English)
   - **Phase 5.3.3**: Calendar 7-Day Planning Strip & Routine Streak Insights
3. **Explicitly Rejected**: Task Dependencies (DAG table).
