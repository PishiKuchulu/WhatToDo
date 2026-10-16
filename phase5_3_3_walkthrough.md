# Phase 5.3.3 — Calendar Intelligence & Routine Streaks Walkthrough

## Summary of Accomplishments

In **Phase 5.3.3**, we implemented the **Calendar Intelligence & Routine Streaks** suite for **PishiToDo**, fully adhering to offline-first, deterministic Clean Architecture principles:

1. **7-Day Planning Strip**:
   - Added a compact, horizontally scrollable 7-day planning strip ([`SevenDayPlanningStrip.kt`](file:///e:/5/Antigravity/PishiToDo/app/src/main/java/com/example/pfptodo/ui/screens/calendar/SevenDayPlanningStrip.kt)) covering **today and the next 6 days**.
   - Fully aware of Persian / Solar Hijri (Jalali) dates (`JalaliCalendarConverter`) in Persian locale (`fa`) and standard Gregorian dates in other locales.
   - Shows day name, date number, scheduled task count badge, load density indicator dot, and selected day visual highlight.
   - Synchronizes smoothly with `CalendarViewModel` and `CalendarGrid`.

2. **Daily Workload Density Classification**:
   - Implemented [`CalculateDailyLoadDensityUseCase`](file:///e:/5/Antigravity/PishiToDo/app/src/main/java/com/example/pfptodo/domain/usecase/calendar/CalculateDailyLoadDensityUseCase.kt) with an explicit, deterministic scoring algorithm:
     - `loadScore = scheduledTaskCount + (highPriorityUncompletedCount * 1) + (overdueCountIfToday * 1)`
     - `NONE`: `loadScore == 0`
     - `LOW`: `loadScore in 1..2`
     - `MEDIUM`: `loadScore in 3..4`
     - `HIGH`: `loadScore >= 5`
   - Visualized via subtle color-coded indicator dots across `CalendarDay` and `DayPlanningCard`.

3. **Routine / Streak Intelligence**:
   - Implemented [`CalculateRoutineStreaksUseCase`](file:///e:/5/Antigravity/PishiToDo/app/src/main/java/com/example/pfptodo/domain/usecase/calendar/CalculateRoutineStreaksUseCase.kt) to compute streak and consistency metrics directly from existing `Task` recurrence rules and completion history without altering Room Schema v6 or adding database tables.
   - Evaluates:
     - `currentStreak`: Consecutive recurring periods completed up to the active cycle.
     - `bestStreak`: Maximum recorded consecutive streak.
     - `totalCompleted`: Total lifetime completed occurrences.
     - `consistencyRate`: Ratio of completed vs expected occurrences over the routine's active lifespan.
     - `isCompletedForCurrentPeriod`: Indicates whether today/current period's recurring task has been satisfied.
   - Displayed in [`RoutineStreakSection.kt`](file:///e:/5/Antigravity/PishiToDo/app/src/main/java/com/example/pfptodo/ui/screens/calendar/RoutineStreakSection.kt) with Material 3 styling.

4. **12-Language Localization Parity**:
   - Added all 10 calendar and streak string keys across all 12 locales (`values/`, `values-fa/`, `values-ar/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-pt/`, `values-ru/`, `values-tr/`, `values-zh-rCN/`).

---

## Exact Algorithms

### 1. Workload Density Algorithm
```text
For date D and task list T:
1. scheduledTasks = tasks where dueDate.toLocalDate() == D
2. scheduledCount = scheduledTasks.size
3. highPriorityCount = scheduledTasks.count { priority == HIGH and not isCompleted }
4. overdueCount = if (D == today) active tasks where dueDate < startOfToday else 0
5. compositeScore = scheduledCount + highPriorityCount + overdueCount

Classification:
- compositeScore == 0  -> NONE (no visual badge)
- compositeScore in 1..2 -> LOW (Green dot #4CAF50)
- compositeScore in 3..4 -> MEDIUM (Amber dot #FF9800)
- compositeScore >= 5    -> HIGH (Red dot #E53935)
```

### 2. Routine Streak Algorithm
```text
For each routine series (grouped by recurrenceAnchor or title + rule):
1. Collect all completed occurrences sorted by completedAt/dueDate.
2. Group occurrences into distinct calendar periods based on rule (Daily -> LocalDate, Weekly -> ISO week, Monthly -> Month).
3. Compute consecutive chain:
   - If period(i+1) follows period(i) within allowed step, streak++.
   - Else, streak resets to 1.
4. Check current status:
   - If last completion is in current period -> currentStreak = runningStreak, isCompletedForCurrentPeriod = true
   - If last completion is in previous period (within 1 cycle) -> currentStreak = runningStreak, isCompletedForCurrentPeriod = false
   - If gap > 1 cycle -> currentStreak = 0 (broken streak), isCompletedForCurrentPeriod = false
5. bestStreak = max(all streak chains).
6. consistencyRate = distinctCompletedPeriods / expectedPeriodsInSpan.
```

---

## Verification & Quality Certification

| Verification Gate | Result | Notes |
| :--- | :--- | :--- |
| **Unit Tests** | **178 / 178 PASS (0 failures)** | Added 21 new tests for 7-day planning, density & streaks |
| **String Parity** | **100% Parity (12 locales)** | Verified with `StringResourceParityTest` |
| **lintDebug** | **0 errors, 0 warnings** | PASS |
| **assembleRelease** | **BUILD SUCCESSFUL** | PASS |
| **Release APK Size** | **4,133,286 bytes (~3.94 MB)** | Strictly below 4.00 MB target |
| **Room Schema** | **Schema v6 (Unchanged)** | 0 schema mutations, 0 table alterations |
| **Backup Format** | **Backup v3 (Unchanged)** | 100% backward/forward compatible |
| **Dependencies** | **100% Offline / Deterministic** | 0 external libraries, 0 cloud/LLM calls |

---

## Known Limitations

1. **Historical Granularity for Legacy Tasks**: Tasks completed prior to the implementation of recurrence anchoring rely on recorded `completedAt` timestamps and task recurrence rules. If an older backup did not preserve `completedAt`, those specific legacy entries gracefully fall back to `dueDate` or 0-streak degradation without crashes.
2. **Same-Day Multiple Completions**: Completing multiple occurrences of a daily routine on the same day counts as 1 day towards the streak to prevent artificial streak inflation.
