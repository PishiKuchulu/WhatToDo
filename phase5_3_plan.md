# PishiToDo — Phase 5.3 Implementation Plan
## Advanced Productivity Intelligence

> **Document Version**: 1.0.0  
> **Status**: ARCHITECTURE SPECIFICATION & IMPLEMENTATION ROADMAP (PENDING APPROVAL)  
> **Date**: August 2026  
> **Author**: Antigravity Core Architecture Team  
> **Prerequisites**: Phase 5.2.1 Stability Audit PASSED  
> **Rule**: DO NOT MODIFY PRODUCTION CODE UNTIL EXPLICITLY APPROVED.

---

## 1. Current Architecture Assessment

PishiToDo operates on an offline-first Clean Architecture baseline with strict boundary enforcement:
- **Presentation Layer**: Jetpack Compose (Material 3), uni-directional data flow (UDF), `StateFlow` orchestration in ViewModels.
- **Domain Layer**: Pure Kotlin models (`Task`, `Category`, `Tag`, `Attachment`, `ParsedTask`, `EisenhowerMatrix`), isolated business use cases, and regex/token NLP parsers.
- **Data Layer**: Room Database v6 (`tasks`, `categories`, `tags`, `attachments`, `task_tag_cross_ref`), repository pattern, atomic v3 JSON backup engine.
- **Background & Work Layer**: WorkManager with pure decision workers (`ReminderWorker`).

All 121 unit and instrumented tests are passing, Android lint (`lintDebug`) has 0 errors, release APK is **3.88 MB** (comfortably under the 4.0 MB ceiling), and 12-language localization with Persian RTL is 100% verified.

---

## 2. Feature-by-Feature Feasibility Analysis

| Candidate Feature | Feasibility | DB Schema Impact | Backup v3 Impact | APK Impact | Offline Capable | Recommendation |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **A. Smart Task Suggestions** | **100%** | **None (v6)** | **None (v3)** | ~12 KB | **100%** | **ADOPT (Phase 5.3.1)** |
| **B. Advanced Smart Search (NL)** | **100%** | **None (v6)** | **None (v3)** | ~15 KB | **100%** | **ADOPT (Phase 5.3.2)** |
| **C. Task Dependencies** | **0% (Unsafe)** | **Breaking (v7)** | **Breaking (v4)** | ~35 KB | 100% | **EXPLICITLY REJECT** |
| **D. Routines & Habit-like Streaks**| **100%** | **None (v6)** | **None (v3)** | ~8 KB | **100%** | **ADOPT (Phase 5.3.3)** |
| **E. Calendar Week Strip & Density**| **100%** | **None (v6)** | **None (v3)** | ~14 KB | **100%** | **ADOPT (Phase 5.3.3)** |

---

## 3. Recommended Phase 5.3 Scope

The recommended Phase 5.3 scope focuses on **high-leverage, local-first intelligence** that makes users significantly more productive without adding bloat or schema risk:

1. **Smart Task Suggestions & Proactive Triage**:
   - Overdue Task Triage (1-tap snooze to today, +1 day, or mark complete).
   - Unscheduled High-Priority Alert (suggest due date for neglected critical tasks).
   - Wrap-up Checklist Prompt (surface tasks where 80%+ subtasks are complete).
   - Dismissible, unobtrusive suggestion carousel on the main screen.

2. **Advanced Natural Language Smart Search**:
   - Bi-lingual intent parsing in **Persian** and **English** (zero cloud AI).
   - Translates queries like `کارهای مهم این هفته`, `کارهای عقب افتاده`, `کارهای خرید`, `important tasks this week`, `overdue tasks`, `recurring tasks` into structured `TaskFilter` + keyword matching.
   - Interactive search chips displaying the parsed filter criteria with 1-tap clear.

3. **Routine Tracking & Calendar Planning**:
   - Recurrence streak and consistency insights on recurring tasks.
   - 7-Day Planning Strip on the Calendar screen for rapid weekly task scheduling.
   - Calendar day task load density indicators (light / moderate / heavy load dots).

---

## 4. Features Explicitly Rejected & Why

### ❌ Task Dependencies (DAG System)
- **Why Rejected**:
  1. **Schema Breakage**: A proper dependency system requires a dedicated `task_dependencies` junction table, forcing a breaking Room migration (`v6 → v7`) and backup format bump (`v3 → v4`).
  2. **Unsafe Re-use**: Attempting to reuse `parentTaskId` for dependencies violates the 1-level checklist hierarchy, corrupts cascade delete behavior, distorts subtask counts, and breaks 2-pass backup import.
  3. **Productivity Mismatch**: PishiToDo is a personal task manager; dependency graphs introduce enterprise PM bloat (Gantt/Jira complexity) that hinders quick personal capture.

### ❌ 24-Hour Multi-Column Time-Blocking Grid
- **Why Rejected**: Causes gesture conflicts with vertical list scrolling in Jetpack Compose, introduces excessive UI layout overhead, and clutters phone displays. A lightweight 7-day planning strip provides 90% of the planning utility at 10% of the complexity.

### ❌ External Cloud Calendar Sync
- **Why Rejected**: Requires Google Play Services / OAuth cloud infrastructure, violating the 100% local, offline-first privacy guarantee.

---

## 5. Database Impact Analysis

- **Room Schema Version**: **STRICTLY REMAINS VERSION 6**.
- **DDL Changes**: **ZERO (0)**.
- **Entities & Tables**: `tasks`, `categories`, `tags`, `attachments`, `task_tag_cross_ref` remain untouched.
- **Query Performance**: Smart search and suggestion use cases run entirely in memory on `Dispatchers.Default` over existing Room `Flow<List<Task>>` streams, avoiding expensive or unindexed database queries.

---

## 6. Backup v3 Impact Analysis

- **Backup Format Version**: **STRICTLY REMAINS VERSION 3**.
- **JSON Schema**: Intact. No new fields or tables added to backup payloads.
- **Compatibility**: 100% backward and forward compatibility with existing v1, v2, and v3 backups.

---

## 7. APK-Size Impact Estimate

- **Estimated Incremental Footprint**: **~35 – 45 KB**.
  - Smart Suggestion Domain Use Cases & Models: ~10 KB
  - Smart Search Natural Language Token Matcher: ~15 KB
  - Compose UI Cards, Week Strip, & Indicators: ~15 KB
  - String Resources across 12 Locales: ~4 KB
- **Projected Final Release APK Size**: **~3.92 MB** (Safely within the `< 4.00 MB` target).

---

## 8. Dependency Impact

- **New Third-Party Libraries**: **ZERO (0)**.
- **No Cloud AI / LLM SDKs**: Natural language parsing is accomplished via pure Kotlin token extractors and regular expressions.
- **No Heavy Chart / Visualization Libraries**: Lightweight Compose Canvas / Surface indicators.

---

## 9. Domain-Layer Design

```
                     ┌───────────────────────────────────────────────────────────┐
                     │                       DOMAIN LAYER                        │
                     └─────────────────────────────┬─────────────────────────────┘
                                                   │
         ┌─────────────────────────────────────────┼─────────────────────────────────────────┐
         ▼                                         ▼                                         ▼
┌───────────────────────────────┐ ┌───────────────────────────────┐ ┌───────────────────────────────┐
│  GetSmartSuggestionsUseCase   │ │   SmartSearchQueryParser      │ │     GetTaskStreakUseCase      │
├───────────────────────────────┤ ├───────────────────────────────┤ ├───────────────────────────────┤
│ Rules Engine:                 │ │ Intent Token Extractors:      │ │ Computes:                     │
│ - Overdue Triage              │ │ - Date: TODAY, WEEK, OVERDUE  │ │ - Current Streak (days/weeks) │
│ - Unscheduled High Priority   │ │ - Priority: HIGH, MEDIUM, LOW │ │ - Completion Consistency %    │
│ - Stale Task Review           │ │ - Status: ACTIVE, COMPLETED   │ │ - Anchor-aligned completion   │
│ - Checklist Wrap-up           │ │ - Recurrence: RECURRING ONLY  │ │   history                     │
│ Emits: Flow<List<Suggestion>> │ │ Emits: SmartSearchQuery       │ │ Emits: TaskStreakInfo         │
└───────────────────────────────┘ └───────────────────────────────┘ └───────────────────────────────┘
```

### 9.1 `GetSmartSuggestionsUseCase`
```kotlin
data class TaskSuggestion(
    val id: String,
    val type: SuggestionType,
    val task: Task,
    val titleRes: Int,
    val descriptionRes: Int,
    val suggestedAction: SuggestionAction
)

enum class SuggestionType { OVERDUE_CLEANUP, UNSCHEDULED_HIGH_PRIORITY, STALE_REVIEW, CHECKLIST_WRAPUP }
enum class SuggestionAction { SNOOZE_TODAY, SNOOZE_TOMORROW, SET_DUE_DATE, COMPLETE_TASK, OPEN_TASK }
```

### 9.2 `SmartSearchQueryParser`
```kotlin
data class SmartSearchQuery(
    val rawQuery: String,
    val residualText: String,
    val filter: TaskFilter,
    val matchedIntentChips: List<SearchIntentChip>
)

data class SearchIntentChip(
    val labelRes: Int,
    val type: ChipType
)
```

---

## 10. UI / UX Design

### 10.1 Smart Suggestion Carousel (`TaskListScreen`)
- Compact, dismissible horizontal card positioned directly below the top filter chips.
- Features an icon (e.g. `AutoAwesome`), clear suggestion title, quick action button (e.g. "Snooze to Today" / "Schedule"), and a dismiss button (`Close`).
- Animated transition (`slideInVertically` + `fadeIn`) when suggestions appear; dismissals stored in ViewModel state.

### 10.2 Natural Language Smart Search Bar (`TaskListScreen`)
- When user types (e.g. `کارهای مهم این هفته` or `important tasks this week`), the search bar dynamically renders smart filter chip badges below the input.
- User can see what criteria were parsed (e.g. `[Priority: High]` `[Due: This Week]`).
- Tapping a chip's remove icon removes that specific filter token from the search query.

### 10.3 7-Day Week Planning Strip (`CalendarScreen`)
- Placed between the month calendar header and the day task list.
- Displays the 7 days of the current selected week with day abbreviations, dates, and active task count dots.
- 1-tap navigation to jump between days with animated selection pills.

### 10.4 Routine Streak Badge (`TaskDetailScreen` & `TaskItem`)
- Recurring tasks display a subtle flame/streak pill (e.g. `🔥 5 weeks in a row`).

---

## 11. Localization Requirements (12 Languages)

New string keys to add with 100% key parity across all 12 locales (`values`, `values-fa`, `values-ar`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-ko`/`values-zh-rCN`, `values-pt`, `values-ru`, `values-tr`):

```xml
<!-- Smart Suggestions -->
<string name="suggestion_overdue_title">Clean up overdue tasks</string>
<string name="suggestion_overdue_desc">%1$d tasks need your attention</string>
<string name="suggestion_unscheduled_title">Schedule high-priority task</string>
<string name="suggestion_unscheduled_desc">%1$s has no due date set</string>
<string name="suggestion_checklist_title">Finish checklist</string>
<string name="suggestion_checklist_desc">%1$d of %2$d subtasks completed</string>
<string name="action_reschedule_today">Reschedule for Today</string>
<string name="action_schedule_tomorrow">Schedule Tomorrow</string>

<!-- Smart Search Intent Chips -->
<string name="search_chip_this_week">This Week</string>
<string name="search_chip_no_date">No Date</string>
<string name="search_chip_recurring">Recurring</string>

<!-- Routine & Calendar Planning -->
<string name="streak_count">%1$d in a row</string>
<string name="week_view_title">Week of %1$s</string>
```

---

## 12. RTL & Bidirectional Layout Considerations

- All suggestion carousels, week strips, and search chip badges will use standard start/end Compose alignment with automatic mirroring for Persian (`fa`) and Arabic (`ar`).
- Jalali calendar conversions and Persian digit representations will be preserved across the 7-day planning strip.

---

## 13. Security & Privacy Considerations

- **100% Local Processing**: NLP tokenization and suggestion scoring execute strictly on-device in application memory.
- **Zero Network Traffic**: No third-party AI APIs, cloud logging, or analytics.

---

## 14. Test Strategy

1. **Unit Tests (`app/src/test`)**:
   - `GetSmartSuggestionsUseCaseTest`: Verify suggestion triggers across overdue, unscheduled, and checklist states.
   - `SmartSearchQueryParserTest`: Test natural language queries in English and Persian (e.g. `کارهای مهم این هفته`, `today's tasks`, `shopping tasks`, `overdue tasks`).
   - `GetTaskStreakUseCaseTest`: Test streak calculations for daily, weekly, monthly, and interval recurrence rules.
   - `StringResourceParityTest`: Automated 12-language key and format placeholder parity.
2. **UI & ViewModel Tests**:
   - `TaskListViewModelSmartSearchTest`: Verify reactive query debouncing and filter intent combination.
   - `CalendarViewModelWeekPlanningTest`: Verify 7-day strip selection and load density computations.

---

## 15. Implementation Order

1. **Step 1: Domain Models & Logic**:
   - Create `TaskSuggestion.kt`, `SmartSearchQuery.kt`, `TaskStreakInfo.kt`.
   - Implement `GetSmartSuggestionsUseCase.kt`, `SmartSearchQueryParser.kt`, `GetTaskStreakUseCase.kt`.
2. **Step 2: String Localization (12 Languages)**:
   - Add new string keys to all 12 `strings.xml` resource folders.
   - Run `StringResourceParityTest` to ensure 100% parity.
3. **Step 3: AppContainer & DI Wiring**:
   - Wire new use cases into `TaskUseCases` and `AppContainer`.
4. **Step 4: Presentation & UI Integration**:
   - Integrate smart search intent chips and suggestion carousel into `TaskListScreen` & `TaskListViewModel`.
   - Add 7-day planning strip and load density indicators into `CalendarScreen` & `CalendarViewModel`.
   - Add streak badge to `TaskDetailScreen`.
5. **Step 5: Verification & Quality Gates**:
   - Run `./gradlew test`, `./gradlew lintDebug`, and `./gradlew assembleRelease`.
   - Confirm APK remains `< 4.00 MB`.

---

## 16. Risk Assessment & Mitigations

| Risk | Severity | Mitigation Strategy |
| :--- | :---: | :--- |
| **Search Parsing False Positives** | Low | Fallback to substring keyword search if no structured intent is detected. Residual text search is always preserved. |
| **Suggestion Clutter** | Low | Limit active suggestions to maximum 3 high-priority items. Provide 1-tap dismiss. |
| **APK Footprint Creep** | Low | Pure Kotlin implementation with zero dependencies keeps additions under 45 KB. |
| **Localization Divergence** | Low | Automated `StringResourceParityTest` enforces strict 12-language 1-to-1 parity before merging. |

---

## 17. Definition of Done

- [ ] All 3 recommended capabilities implemented cleanly in Domain and Presentation layers.
- [ ] Room Schema v6 hash remains identical (`8b05c9e93e8a62f1c72527712826ec40`).
- [ ] Backup Format v3 export/import passes all validation tests.
- [ ] 12 language resource folders have 100% string parity.
- [ ] Persian RTL and Jalali conversions verified.
- [ ] Test count increases with new unit tests for all new use cases; 100% tests passing.
- [ ] `lintDebug` passes with 0 errors.
- [ ] Release APK is verified `< 4.00 MB`.

---

## 18. Recommended Sub-Phases for Step-by-Step Execution

- **Sub-phase 5.3.1**: Smart Task Suggestions & Proactive Triage Engine.
- **Sub-phase 5.3.2**: Advanced Natural Language Smart Search (Persian & English).
- **Sub-phase 5.3.3**: Calendar 7-Day Planning Strip & Routine Streak Insights.
