# Phase 5.3.2 Implementation Plan — Advanced Natural Language Smart Search

## 1. Overview & Architecture

Phase 5.3.2 implements deterministic, offline, multilingual **Natural Language Smart Search** for PishiToDo. Users can query tasks using organic Persian and English phrases. The parser decomposes queries into structured filtering parameters (`TaskFilter`) and residual keyword strings, visualizing parsed intent as dismissible Material 3 chips.

```
[Raw Search Input]
        ↓
[ParseNaturalLanguageSearchUseCase]
   ├── Normalize Characters / Digits / ZWNJ (PersianNormalizer & DigitNormalizer)
   ├── Extract Priority (High / Low / Medium)
   ├── Extract Status (Overdue / Completed / Incomplete)
   ├── Extract Date Scope (Today / Tomorrow / This Week / This Month)
   ├── Extract Recurrence (Recurring Only)
   └── Extract Clean Residual Keyword
        ↓
[ParsedSearchQuery] (keyword, filter, active chips)
        ↓
[TaskListViewModel] ── Combine with UI Tags/Sort Order ──> [GetFilteredTasksUseCase] ──> [Task Stream]
```

---

## 2. Proposed Changes

### 2.1 Domain Layer

#### `app/src/main/java/com/example/pfptodo/domain/model/TaskFilter.kt`
- Add `THIS_WEEK` and `THIS_MONTH` to `TaskFilter.DateFilter` enum:
```kotlin
enum class DateFilter {
    ALL, TODAY, TOMORROW, THIS_WEEK, THIS_MONTH, UPCOMING
}
```

#### `app/src/main/java/com/example/pfptodo/domain/model/ParsedSearchQuery.kt` [NEW]
- Define domain models:
```kotlin
data class ParsedSearchQuery(
    val rawQuery: String = "",
    val keyword: String = "",
    val filter: TaskFilter = TaskFilter(),
    val chips: List<SearchFilterChip> = emptyList()
)

data class SearchFilterChip(
    val id: String,
    val labelRes: Int,
    val customLabel: String? = null,
    val type: SearchChipType
)

enum class SearchChipType {
    PRIORITY, STATUS, DATE, RECURRING, KEYWORD
}
```

#### `app/src/main/java/com/example/pfptodo/domain/usecase/task/ParseNaturalLanguageSearchUseCase.kt` [NEW]
- Pure Kotlin deterministic parser for Persian and English search phrases.
- Injects `Clock = Clock.systemDefaultZone()` for testability.
- Evaluates:
  - Persian keywords: *"مهم"*, *"خیلی مهم"*, *"با اولویت بالا"*, *"عقب افتاده"*, *"انجام شده"*, *"انجام نشده"*, *"امروز"*, *"فردا"*, *"این هفته"*, *"این ماه"*, *"تکرارشونده"*, *"کارهای مربوط به"*, etc.
  - English keywords: *"important"*, *"high priority"*, *"overdue"*, *"completed"*, *"incomplete"*, *"today"*, *"tomorrow"*, *"this week"*, *"this month"*, *"recurring"*, *"tasks related to"*, etc.
  - Generates `ParsedSearchQuery` containing the decomposed `TaskFilter`, clean residual `keyword`, and visual `SearchFilterChip` list.

#### `app/src/main/java/com/example/pfptodo/domain/usecase/task/GetFilteredTasksUseCase.kt`
- Update date filtering logic to support `DateFilter.THIS_WEEK` and `DateFilter.THIS_MONTH` with Jalali / Gregorian calendar awareness.

#### `app/src/main/java/com/example/pfptodo/domain/usecase/task/TaskUseCases.kt`
- Add `val parseNaturalLanguageSearch: ParseNaturalLanguageSearchUseCase` to the `TaskUseCases` facade.

### 2.2 Dependency Injection Layer

#### `app/src/main/java/com/example/pfptodo/core/di/AppContainer.kt`
- Instantiate and provide `ParseNaturalLanguageSearchUseCase()` in `TaskUseCases`.

### 2.3 ViewModel & UI Layer

#### `app/src/main/java/com/example/pfptodo/ui/screens/tasklist/TaskListViewModel.kt`
- Process `debouncedSearchQuery` through `taskUseCases.parseNaturalLanguageSearch(query)`.
- Feed `parsedSearch.keyword` to `taskUseCases.getFilteredTasks`.
- Merge `parsedSearch.filter` with `_filter` in `filteredTasksFlow`.
- Expose `parsedSearchQuery` in `TaskListUiState`.
- Provide `onRemoveSearchChip(chip: SearchFilterChip)` to remove intent from the search query.

#### `app/src/main/java/com/example/pfptodo/ui/screens/tasklist/TaskListScreen.kt`
- Render active `SearchFilterChip` elements directly under the `SearchBar` when `uiState.parsedSearch.chips.isNotEmpty()`.
- Support Persian RTL mirrored icons and dismiss interactions.

### 2.4 Localization Layer

#### `app/src/main/res/values*/strings.xml`
- Add localized strings for search filter chip labels across all 12 locales:
  - `search_chip_important`
  - `search_chip_low_priority`
  - `search_chip_overdue`
  - `search_chip_completed`
  - `search_chip_incomplete`
  - `search_chip_today`
  - `search_chip_tomorrow`
  - `search_chip_this_week`
  - `search_chip_this_month`
  - `search_chip_recurring`

---

## 3. Verification Plan

### 3.1 Unit Testing
- Create `ParseNaturalLanguageSearchUseCaseTest.kt` with comprehensive test coverage:
  - All Persian single and multi-token triggers.
  - All English single and multi-token triggers.
  - Combination queries (e.g. *"کارهای مهم این هفته"*, *"important incomplete tasks this week"*).
  - Residual keyword extraction (e.g. *"کارهای مربوط به سفر"* → `"سفر"`).
  - Stopword and container phrase stripping.
  - ZWNJ (`\u200C`) and character normalization.
  - Empty, whitespace, punctuation, and unsupported query fallback.
  - Mixed language queries.
- Update `GetFilteredTasksUseCaseTest.kt` to verify `THIS_WEEK` and `THIS_MONTH` date ranges.
- Update `TaskListViewModelTest.kt` to verify reactive search intent flow and chip removal.
- Run `StringResourceParityTest` to ensure 100% 12-language parity.

### 3.2 Build & Verification Gates
1. `./gradlew test` (0 failures across all unit tests).
2. `./gradlew lintDebug` (0 errors).
3. `./gradlew assembleRelease` (Release APK < 4.00 MB).
