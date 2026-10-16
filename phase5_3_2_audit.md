# Phase 5.3.2 Architecture Audit — Advanced Natural Language Smart Search

## 1. Executive Summary

Phase 5.3.2 introduces **Advanced Natural Language Smart Search** to PishiToDo. This capability enables users to type organic Persian and English query phrases (e.g., *"کارهای مهم این هفته"*, *"overdue tasks"*, *"کارهای مربوط به سفر"*) into the search field and automatically have intent parsed into structured dimensions (priority, status, date scope, recurrence) alongside free-text keywords, with visual dismissible filter chips.

This audit evaluates the codebase's existing infrastructure, validates architectural compliance, and establishes precise integration boundaries without modifying Room Schema v6, Backup Format v3, or introducing cloud/LLM dependencies.

---

## 2. Reusable Existing Capabilities

### 2.1 Domain & Data Filtering
1. **`TaskFilter`**:
   - Reusable fields: `status` (`ALL, ACTIVE, COMPLETED, OVERDUE`), `priorities` (`Set<Priority>`), `tagIds` (`Set<Long>`), `tagFilterMode`, `isRecurringOnly` (`Boolean`).
   - Extension needed: Add `THIS_WEEK` and `THIS_MONTH` to `TaskFilter.DateFilter` to support weekly and monthly scope semantics.
2. **`GetFilteredTasksUseCase`**:
   - Reuses pure in-memory filtering and sorting pipelines with `PersianNormalizer.normalize(query)` for database searches.
   - Supports date boundary semantics (`TODAY`, `TOMORROW`, `UPCOMING`), which will be expanded to calculate `THIS_WEEK` and `THIS_MONTH` respecting local Jalali and Gregorian calendar boundaries.
3. **`TaskDao.searchTasks(query)`**:
   - Performs database-level LIKE matching against title, description, notes, and tags with Arabic/Persian character normalization.

### 2.2 Parser & Normalization Infrastructure
1. **`PersianNormalizer`**:
   - Normalizes Arabic/Persian letter variants (ي→ی, ك→ک, أ/إ/آ→ا, ة→ه) and zero-width characters (`\u200C`, `\u200B`, `\u200E`, `\u200F`) to standard spaces.
2. **`DigitNormalizer`**:
   - Converts Persian/Arabic digits (`۰..۹`) to standard ASCII digits.
3. **`JalaliCalendarConverter`**:
   - Provides pure bidirectional conversion between Solar Hijri (Jalali) and Gregorian `LocalDate` for accurate month and week boundaries.
4. **`Clock` Injection**:
   - Uses `java.time.Clock` for deterministic, edge-case-free date calculations in tests and production.

---

## 3. Natural Language Intent to `TaskFilter` Mapping

The parser extracts intent tokens, removes recognized container/stop phrases, and compiles structured dimensions:

| Natural Language Phrase (FA) | Natural Language Phrase (EN) | Extracted Filter Dimension | Resulting `TaskFilter` Property |
|---|---|---|---|
| مهم / خیلی مهم / با اولویت بالا / فوری | important / high priority / urgent | `Priority.HIGH` | `priorities = setOf(Priority.HIGH)` |
| کم اهمیت / با اولویت پایین | low priority / not urgent | `Priority.LOW` | `priorities = setOf(Priority.LOW)` |
| عقب افتاده / موعد گذشته / گذشته | overdue / late | `Status.OVERDUE` | `status = Status.OVERDUE` |
| انجام شده / تکمیل شده / تمام شده | completed / done / finished | `Status.COMPLETED` | `status = Status.COMPLETED` |
| انجام نشده / تکمیل نشده / ناتمام | incomplete / unfinished / active / pending | `Status.ACTIVE` | `status = Status.ACTIVE` |
| امروز / امروزها | today / today's | `DateFilter.TODAY` | `dateFilter = DateFilter.TODAY` |
| فردا | tomorrow / tomorrow's | `DateFilter.TOMORROW` | `dateFilter = DateFilter.TOMORROW` |
| این هفته / هفته جاری | this week / current week | `DateFilter.THIS_WEEK` | `dateFilter = DateFilter.THIS_WEEK` |
| این ماه / ماه جاری | this month / current month | `DateFilter.THIS_MONTH` | `dateFilter = DateFilter.THIS_MONTH` |
| تکرارشونده / تکراری / دوره‌ای | recurring / repeating / periodic | `isRecurringOnly = true` | `isRecurringOnly = true` |

---

## 4. Coexistence of Keyword Search and Structured Filters

When a query contains both structured intent and free-text content (e.g. *"کارهای مربوط به سفر"* or *"important meeting tasks this week"*):
1. **Container / Stopwords Stripping**:
   - Persian container phrases: *"کارهای مربوط به"*, *"کارهای"*, *"کارها"*, *"کار"*, *"مربوط به"*
   - English container phrases: *"tasks related to"*, *"tasks about"*, *"tasks"*, *"task"*
2. **Intent Tokens Extracted**:
   - Structured tokens (e.g., *"مهم"* → `Priority.HIGH`, *"این هفته"* → `DateFilter.THIS_WEEK`) are recorded and stripped from the working text.
3. **Remaining Clean Keyword**:
   - Any remaining words (e.g. *"سفر"* or *"meeting"*) are sanitized and set as `keyword`.
   - If no text remains after extracting intent and stopwords, `keyword` is set to `""`.
4. **Composed Execution**:
   - `GetFilteredTasksUseCase` executes `searchTasks(keyword)` if `keyword.isNotBlank()`, else `getAllMainTasks()`.
   - The structured `TaskFilter` is evaluated in memory over the resulting stream.

---

## 5. Date Interpretation & Jalali / Gregorian Semantics

### 5.1 "This Week" (`THIS_WEEK`)
- **Persian Week (Saturday to Friday)**:
  - In Iranian/Solar Hijri conventions, the week begins on **Saturday** (`DayOfWeek.SATURDAY`) and ends on **Friday** (`DayOfWeek.FRIDAY`).
  - Calculation: Start of week is the most recent Saturday (or today if Saturday), end of week is start of following Saturday.
- **Gregorian Week (Monday to Sunday)**:
  - Standard ISO-8601 week starts on **Monday** (`DayOfWeek.MONDAY`) and ends on **Sunday** (`DayOfWeek.SUNDAY`).
  - Selected automatically based on language/script detection in the query.

### 5.2 "This Month" (`THIS_MONTH`)
- **Jalali Month**:
  - Converted via `JalaliCalendarConverter.gregorianToJalali(now)` to determine Jalali year and month.
  - Start of month = 1st of current Jalali month converted to Gregorian `LocalDate`.
  - End of month = 1st of next Jalali month converted to Gregorian `LocalDate`.
- **Gregorian Month**:
  - Start of month = `now.withDayOfMonth(1)`.
  - End of month = `now.withDayOfMonth(1).plusMonths(1)`.

---

## 6. Avoiding False Positives & Handling Unsupported Queries

1. **Word-Boundary Matching**:
   - English tokens use regex word boundaries `\b` (e.g., `\bimportant\b`).
   - Persian tokens use whitespace/boundary checks so substrings in unrelated words (e.g. *"امروز"* in a longer word or *"مهم"* in *"مهمان"*) do not falsely trigger.
2. **Graceful Fallback**:
   - If a query has no recognized intent rules (e.g., *"خرید نان"* or *"call mom"*), the parser emits an empty structured filter with `keyword = rawQuery`.
   - The system executes ordinary free-text search without errors or warnings.
3. **Punctuation & Whitespace Resilience**:
   - Trims excess whitespace, removes punctuation (quotes, question marks, commas) from keyword extraction while preserving Persian نیم‌فاصله normalization.

---

## 7. Architectural Constraints Compliance

- **Room Schema v6**: Untouched (0 schema changes).
- **Backup Format v3**: Untouched (0 JSON backup format changes).
- **Migrations**: No new migrations added (stays at v6).
- **Dependencies**: 0 new libraries or AI frameworks; pure local deterministic Kotlin.
- **Reactive Flow**: Preserves `debouncedSearchQuery` combine pipeline (`FIND-01` compliant).
- **Localization**: Maintains strict 12-language parity and Persian RTL layout.
- **APK Budget**: Target <4.00 MB preserved.
