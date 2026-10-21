# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.3] - 2026-10-22

### Changed
- New launcher icon: peach checkbox design (teal rounded checkbox with an orange check on a soft peach plate), matching the Productive Day palette.

## [1.3.2] - 2026-10-22

### Fixed
- Header date label now follows the app's calendar system: Jalali (Solar Hijri) when the calendar type is Jalali (the default for Persian, e.g. "پنجشنبه ۳۰ مهر"), Hijri (Islamic) for Arabic locales (e.g. "الخميس ١١ جمادى الأولى"), Gregorian otherwise. Previously it always showed the Gregorian date.

## [1.3.1] - 2026-10-21

### Fixed
- Task-list header merged into a single top app bar: the orange date label and the app title now live in the app bar's title slot, removing the empty app-bar band and the oversized gap above the search bar (search now sits directly under the bar).
- The extended "+ Add Task" FAB no longer permanently covers task cards: the task list (both sorted and manual-reorder modes) now has 88dp bottom content padding so every item can scroll clear of the FAB.
- Android 13+ system per-app language picker now lists the app's 12 languages (added `res/xml/locales_config.xml` + `android:localeConfig`).

### Internal
- CI (Build APK workflow) now runs the unit-test suite (`testDebugUnitTest`) before building; previously no workflow executed the tests at all.

## [1.3.0] - 2026-10-21

### Changed
- Full "Productive Day" restyle (design built in Penpot): teal `#0F3D4E` + orange `#F5A83B` + peach `#FCE7C8` palette replaces Violet Pulse in both light and teal-tinted dark themes.
- Bundled typefaces: Poppins for Latin locales, Vazirmatn for Persian (fa) — selected automatically by locale in the theme.
- Home header: small orange date label above the big app-title heading.
- New hero card at the top of the task list: teal card with today's completion ring (orange), "X of Y tasks completed" status and a "View Task" button that applies the Today filter.
- Task cards: 24dp corners; tasks due today get the peach container (deep warm brown in dark); category chip is peach-light with an orange dot; checkbox is now a rounded square — orange fill with a white check when completed.
- Orange FAB, orange selected filter chips and bottom-nav selection, white search/quick-add fields, teal calendar header with an orange selected day.

### Fixed
- String parity: added the missing `nav_tasks_title` key to the 10 non-fa locales so all 12 locales match the base strings exactly.

## [1.2.2] - 2026-10-19

### Fixed
- Compact task-list header: replaced `LargeTopAppBar` with a single-row `TopAppBar`, removing the large empty gap above the title.

## [1.2.1] - 2026-10-19

### Fixed
- Empty state no longer hides behind the "+ Add Task" FAB: the empty-state content now has 96dp bottom clearance.
- Bottom navigation labels are single-line; the "Productivity Stats" tab is shortened to "Stats" («آمار») and the first tab is renamed from "What ToDo" to "Tasks" («کارها»).
- Removed the duplicate microphone button from the task-list top bar (it triggered the same voice input as the mic inside the quick-add field); the remaining top-bar icons already carry proper content descriptions.

## [1.2.0] - 2026-10-19

### Changed
- Full visual restyle inspired by the "Todo App" Figma design: near-black dark theme (`#0E0E12`) with vivid purple accent (`#8B5CF6`); light theme is a clean lavender-gray with the same purple primary. Style-only change — every feature, screen, and string works as before.
- Task rows are now rounded 20dp cards (`surfaceContainerHigh`); completed tasks are dimmed with strikethrough titles; checkbox tinted by the purple primary.
- Quick-add input is a rounded filled field echoing the design (rounded container + purple focus ring).
- Card corner radius scale bumped: `Shapes.medium` 16dp → 20dp.

## [1.1.1] - 2026-10-17

### Fixed
- App renamed to **What ToDo** (all locales); launcher icon recompressed losslessly.
- Reminders now have an exact-delivery path: AlarmManager `setExactAndAllowWhileIdle` when `SCHEDULE_EXACT_ALARM` is granted (WorkManager remains the fallback), plus a boot receiver so exact reminders survive device restarts. A reminder scheduled in the past now cancels its stale work instead of leaving it behind.
- Completing a recurring task preserves the user's reminder offset (e.g. "1 hour before due") on the spawned occurrence instead of resetting it to the due moment.
- Backup import no longer closes Room's shared database connection mid-transaction (import used to fail at commit and left the database singleton with a closed connection).
- DataStore flows (theme / language / calendar) now fall back to defaults on corrupted prefs instead of crashing at startup.
- Inserting a task no longer races on `orderIndex` (read-then-write moved into a single Room transaction); spawning the next occurrence of a recurring task runs in one transaction and reports errors instead of swallowing them silently.
- Enum values read from the DB degrade gracefully (unknown value → default) instead of crashing every table read.
- Deleting a task also cancels its subtasks' scheduled reminders.
- Birthday "day before" reminder fires at the same local time on the previous calendar day (no more 1-hour DST drift).
- Manual drag & drop ordering actually works (stable gesture key + keyed list items).
- Quick-add parsing is debounced and runs off the main thread; search uses local state with debounced writes (no more whole-screen recomposition per keystroke).
- Eisenhower matrix colors follow the app's own theme (dark/light override) instead of the system setting.
- Persian digits accepted in the recurrence interval dialog (Confirm no longer stuck disabled); digits shown in Persian UI are localized throughout (statistics, badges, streaks, birthdays, matrix, planning strip, previews).
- Hardcoded English user-facing strings moved to resources (EN + FA); accessibility labels added to icon buttons, day cells, color swatches; checkbox has proper checkbox semantics; small touch targets enlarged to 48dp; numeric keyboard for the interval field.
- Tests: migration 6→7 now covered; tautological recurrence tests rewritten against real production code; strict placeholder-type checks and fa key-parity in the string test; new tests for categories, birthdays, reminder-worker decisions; placeholder Example tests removed; stale test name fixed; error path added to reorder-subtasks test.
- Build: hardcoded localhost proxy removed from gradle.properties (re-add per-machine if needed); dead `com.google.android.material` dependency removed; test dependency versions moved into the version catalog; ProGuard rules narrowed to what Room/Gson actually need; commented-out release signing template added (not activated).

### Notes
- Without `SCHEDULE_EXACT_ALARM` permission the app falls back to inexact WorkManager delivery (reminders may arrive late under Doze).
- Translations for all 11 locales are kept in the APK (no `resConfigs`), trading APK size for full language coverage.

## [1.1.0] - 2026-10-11

### Added
- Jalali (Solar Hijri) calendar, selectable in Settings → Appearance & language (defaults to Jalali for Persian). Calendar grid, 7-day strip, task/detail/editor dates, smart preview and birthdays follow the choice; switching converts every displayed date in both directions (stored data is unchanged). Includes a Jalali date picker.
- New list filters: Tomorrow, This week, This month, Medium priority, Low priority ("this week/month" follow the chosen calendar).
- Settings are grouped into pages: Appearance & language, Organization, Notifications, Data, About.
- Category management (add / rename / recolor / change icon / delete) in Settings → Organization.
- Task notes field in the editor and detail screen.
- Attachments can be opened; image attachments show a thumbnail; delete asks for confirmation.
- Notification status and a shortcut to the system notification settings.
- Day-before birthday notification now has its own text.

### Fixed
- Jalali conversion used a 2820-year formula that mis-handled some years (e.g. 1404 treated as leap); replaced with the break-table algorithm.
- Editing a task no longer resets notes, completion state, parent (subtask became a root task), manual order, creation date or recurrence anchor.
- Manual drag & drop ordering works (new tasks used to share orderIndex 0).
- Birthday reminders repeat every year (they fired once and were never rescheduled) and no longer vanish when today's reminder time has passed.
- Backup format v4: includes birthdays and task completion time; restore merges identical categories and re-arms reminders.
- Typed text / voice transcription is carried into the editor instead of being discarded.
- Voice shortcut opens voice input; widget refreshes on data changes.
- Completing a recurring task twice (undo) no longer creates duplicate occurrences.
- Per-app language now works on Android 7–12; deep links are not replayed after rotation.

 - 2026-08-01

### Added
- Core task management with subtasks, categories, priorities, and due dates.
- Material 3 UI (100% Compose) with light/dark/system themes and dynamic color.
- Full Persian (fa-IR) localization and RTL support.
- Drift-free recurrence engine (Daily, Weekly, Monthly, Yearly) with month-end and leap-year handling.
- Statistics computed from real data: completion rate, 7-day trend, category distribution.
- Calendar with locale-aware week start and per-day task overview.
- Search-as-you-type with Persian text normalization.
- Filtering (All/Active/Completed/Overdue) and date scopes (Today/Tomorrow/Upcoming).
- Sorting by manual order, creation date, due date, or priority; drag-and-drop reorder.
- Voice input for quick task creation.
- File attachments with persisted URI permissions.
- Reminders via WorkManager with Android 13+ permission handling.
- Versioned JSON export/import with transactional restore and full validation.
- Offline-first architecture using Room and DataStore.

### Fixed
- Room migrations rebuilt so every historical upgrade preserves data.
- Reminder pipeline wired end-to-end (schedule on save, cancel on delete).
- Import no longer overwrites existing data or breaks foreign-key relationships.
- Notification deep link opens the correct task.
- Unused resources, dead code, and template files removed.
- Lint baseline removed; `abortOnError` enabled.

### Known Limitations
- Release builds are unsigned (sign with your own key).
- No cloud synchronization in this version.

### Future Plans
- Google Drive / Dropbox backup and sync.
- Home screen widget for quick task access.
- Desktop version using Compose Multiplatform.
