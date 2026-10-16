# Phase 5.2: Subtasks & Checklist System — Architecture Audit & Implementation Plan

## 1. Executive Summary

This document provides the complete architecture audit, data model evaluation, and implementation specification for **Phase 5.2: Subtasks & Checklist System** in PishiToDo.

The primary objective is to deliver a seamless, lightweight, and modern checklist/subtask experience inspired by premium task managers (e.g., Things 3, Todoist, TickTick), while maintaining:
- **Zero Database Migrations** (100% DB-safe on Room v6)
- **Zero Backup Breaking Changes** (100% format v3 safe with 2-pass remapping)
- **Small APK Size** (< 4.0 MB target)
- **Offline-First & Strict Privacy**
- **12-Language Parity & Full Persian RTL Support**

---

## 2. Storage Strategy Evaluation: Option A vs Option B

| Evaluation Dimension | Option A: Separate `subtasks` Table | Option B: Self-Referencing `parentTaskId` in `tasks` (Recommended) |
| :--- | :--- | :--- |
| **Room Schema Impact** | ❌ **BREAKING**: Requires Room migration v6 → v7 (`CREATE TABLE subtasks`, drop old columns/indices). Risk of migration failure on user devices. | ✅ **100% DB SAFE**: `TaskEntity` already has `parentTaskId: Long? = null` with `ForeignKey(onDelete = CASCADE)` and `Index("parentTaskId")` in Room v6. |
| **Backup Compatibility** | ❌ **BREAKING**: Format v3 would need to change to v4, breaking older backups or requiring complex fallback parsers. | ✅ **100% SAFE**: Format v3 already serializes `parentTaskId` and includes 2-pass ID remapping in `BackupUseCases`. |
| **Query Performance** | ⚠️ Requires `JOIN` or multiple distinct queries for every task list render. | ✅ Optimized single-pass subqueries in `TaskDao.getAllMainTasks()` already compute `subtaskCount` and `completedSubtaskCount`. |
| **Cascade Deletions** | ⚠️ Must wire SQLite FK or manual repository cleanup. | ✅ Handled natively at the SQLite engine level via `ForeignKey.CASCADE`. |
| **Code Cleanliness** | ❌ Requires duplicating entities, DAOs, repositories, models, and use cases for subtasks. | ✅ Unified task pipeline; subtasks are first-class tasks constrained by domain rules. |
| **Depth Control** | Enforced by table schema. | ✅ Enforced strictly in Domain Layer (1-level maximum depth). |

### Conclusion
**Option B is selected**. It provides a 100% database-safe implementation with zero schema migrations, zero backup risk, and maximum code reuse.

---

## 3. Architecture & Domain Design

### 3.1 1-Level Depth Constraint (Anti-Nesting Rule)
To avoid infinite recursion and visual clutter:
- **Main Task**: `parentTaskId == null`.
- **Subtask**: `parentTaskId != null`.
- **Constraint**: A task where `parentTaskId != null` cannot be assigned as a parent for other tasks. Attempting to add a subtask to an existing subtask will throw an `IllegalArgumentException` in the domain use case.

### 3.2 Domain Use Cases
1. `GetSubtasksUseCase(parentId: Long): Flow<List<Task>>` (Already available as `TaskUseCases.getSubtasks`).
2. `CreateSubtaskUseCase(parentId: Long, title: String): Result<Long>`:
   - Validates `title.isNotBlank()`.
   - Validates that `parentTask.parentTaskId == null` (enforcing max depth = 1).
   - Computes next `orderIndex` (`max(subtasks.orderIndex) + 1`).
   - Inserts task with `parentTaskId = parentId`.
3. `CreateSubtasksBatchUseCase(parentId: Long, titles: List<String>): Result<List<Long>>`:
   - Atomically inserts multiple checklist items (used by Smart Parser and batch paste).
4. `ToggleSubtaskUseCase(subtaskId: Long): Result<Unit>`:
   - Toggles `isCompleted` and updates `completedAt`.
5. `ReorderSubtasksUseCase(parentId: Long, reorderedSubtaskIds: List<Long>): Result<Unit>`:
   - Updates `orderIndex` of subtasks sequentially in a single transaction.
6. `DeleteSubtaskUseCase(subtaskId: Long): Result<Unit>`:
   - Deletes the subtask row.

### 3.3 Progress Indicator Logic
In `Task.kt` domain model:
```kotlin
val subtaskProgress: Float
    get() = if (subtaskCount == 0) 0f else (completedSubtaskCount.toFloat() / subtaskCount.toFloat()).coerceIn(0f, 1f)

val isAllSubtasksCompleted: Boolean
    get() = subtaskCount > 0 && completedSubtaskCount == subtaskCount
```

---

## 4. Smart NLP Recognition for Checklists

### 4.1 Multi-Line Checklist Detection
Support multi-line input in `LocalTaskParser`, `PersianTaskParser`, and `EnglishTaskParser`.

**English Example**:
```text
Prepare trip tomorrow 5pm #Travel:
- Book flight tickets
- Reserve hotel room
- Pack luggage
```

**Persian Example**:
```text
لیست خرید فردا عصر #خانه:
- نان و شیر
- پنیر و تخم مرغ
- میوه
```

### 4.2 Parsing Workflow
1. Split raw text by newlines.
2. **Line 1** (Header): Parsed as the parent task (extracting Title, Date, Time, Priority, Tags, Recurrence).
3. **Subsequent Lines**: Lines starting with checklist markers (`- `, `* `, `• `, `+ `, or `1. `, `2. `) or non-empty indented lines are extracted into `ParsedTask.subtaskTitles: List<String>`.
4. The smart creation bottom sheet displays a preview of the parent task along with the checklist items count and preview chips.
5. Upon confirmation, `CreateSubtasksBatchUseCase` creates all items atomically with the parent task.

---

## 5. UI / UX Design

### 5.1 Task Item Progress in Task List (`TaskItem.kt`)
- When `task.subtaskCount > 0`:
  - Show a stylish compact badge: `[Icon] 3/5 (60%)`.
  - Include a subtle micro progress bar beneath the task title or inline chip with animated progress fill.

### 5.2 Task Detail Screen (`TaskDetailScreen.kt`)
- **Checklist Card**:
  - Header: `Checklist (%1$d/%2$d)` with LinearProgressIndicator.
  - Subtask row: Checkbox, strikethrough animated text, delete button, drag reordering handle.
  - **Quick Add Row**: Inline `OutlinedTextField` / `BasicTextField` with "Add item" placeholder and "Done" action on keyboard to quickly add consecutive items without leaving the keyboard.

### 5.3 Task Editor Screen (`TaskEditorScreen.kt`)
- Checklist builder section allowing users to add/remove/reorder subtasks while composing or editing a task.

### 5.4 RTL & Localization
- Checkbox and progress bar mirrored naturally for Persian and Arabic.
- Strikethrough and check animations follow RTL text direction.

---

## 6. Notification Behavior

- **Rule**: Subtasks represent checklist elements of a parent task. They do **not** trigger independent alarm notifications by default.
- **Parent Notification**: The parent task notification displays the subtask progress in the notification body (e.g. `3/5 subtasks done`).
- Completing a subtask does not cancel parent reminders.

---

## 7. Localization Requirements (12 Languages)

New string keys to add with 100% parity:
- `checklist_title`: Checklist
- `checklist_progress`: %1$d of %2$d completed
- `checklist_add_item_hint`: Add a subtask…
- `checklist_empty`: No subtasks added yet
- `checklist_item_deleted`: Subtask deleted
- `error_max_depth_exceeded`: Cannot add subtasks to a subtask

---

## 8. Step-by-Step Implementation Roadmap (For Step 2 Execution)

1. **Domain Layer**:
   - Add `subtaskProgress` helper to `Task.kt`.
   - Add `subtaskTitles: List<String>` to `ParsedTask.kt`.
   - Create `CreateSubtaskUseCase.kt`, `CreateSubtasksBatchUseCase.kt`, and `ReorderSubtasksUseCase.kt`.
   - Wire new use cases into `TaskUseCases.kt` and `AppContainer.kt`.

2. **NLP Parsers**:
   - Update `PersianTaskParser.kt` and `EnglishTaskParser.kt` to extract multi-line checklist items.
   - Update `LocalTaskParser.kt`.

3. **Presentation & UI**:
   - Enhance `TaskItem.kt` with modern checklist progress indicators.
   - Enhance `TaskDetailScreen.kt` with quick inline subtask creation and animated checklist items.
   - Add checklist section to `TaskEditorScreen.kt`.
   - Update `VoiceInputBottomSheet.kt` / `SmartRecognitionPreview` with subtasks preview.

4. **Localization**:
   - Add strings across all 12 `strings.xml` resource files.

5. **Testing & Verification**:
   - Add unit tests for `CreateSubtaskUseCaseTest`, `ReorderSubtasksUseCaseTest`, and multi-line smart parser.
   - Run `./gradlew.bat test`, `./gradlew.bat lintDebug`, `./gradlew.bat assembleRelease`.
   - Verify sub-4MB APK size and Room schema v6 integrity.
