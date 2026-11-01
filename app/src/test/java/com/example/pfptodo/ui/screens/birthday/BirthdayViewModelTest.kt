package com.example.pfptodo.ui.screens.birthday

import android.content.ContentResolver
import android.net.Uri
import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Birthday
import com.example.pfptodo.domain.repository.BirthdayRepository
import com.example.pfptodo.domain.usecase.birthday.AddBirthday
import com.example.pfptodo.domain.usecase.birthday.BirthdayUseCases
import com.example.pfptodo.domain.usecase.birthday.DeleteBirthday
import com.example.pfptodo.domain.usecase.birthday.GetAllBirthdays
import com.example.pfptodo.domain.usecase.birthday.GetBirthdayById
import com.example.pfptodo.domain.usecase.birthday.GetUpcomingBirthdays
import com.example.pfptodo.domain.usecase.birthday.UpdateBirthday
import com.example.pfptodo.worker.reminder.BirthdayReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

private class FakeBirthdayRepository : BirthdayRepository {
    val rows = linkedMapOf<Long, Birthday>()
    private var nextId = 1L
    var deleteResult: Result<Unit> = Result.Success(Unit)

    override fun getAllBirthdays(): Flow<List<Birthday>> = flowOf(rows.values.toList())
    override suspend fun getAllBirthdaysRaw(): List<Birthday> = rows.values.toList()
    override suspend fun getBirthdayById(id: Long): Result<Birthday?> = Result.Success(rows[id])
    override suspend fun getByContactId(contactId: String): Result<Birthday?> =
        Result.Success(rows.values.firstOrNull { it.contactId == contactId })

    override suspend fun insertBirthday(birthday: Birthday): Result<Long> {
        val id = nextId++
        rows[id] = birthday.copy(id = id)
        return Result.Success(id)
    }

    override suspend fun updateBirthday(birthday: Birthday): Result<Unit> {
        rows[birthday.id] = birthday
        return Result.Success(Unit)
    }

    override suspend fun deleteBirthday(birthday: Birthday): Result<Unit> {
        if (deleteResult is Result.Success) rows.remove(birthday.id)
        return deleteResult
    }

    override suspend fun deleteById(id: Long): Result<Unit> {
        rows.remove(id)
        return Result.Success(Unit)
    }
}

private class FakeReminderManager : BirthdayReminderManager {
    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()
    override fun scheduleReminder(birthday: Birthday, replace: Boolean) {
        scheduled.add(birthday.id)
    }

    override fun cancelReminder(birthdayId: Long) {
        cancelled.add(birthdayId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BirthdayViewModelTest {

    private lateinit var repository: FakeBirthdayRepository
    private lateinit var reminderManager: FakeReminderManager
    private lateinit var viewModel: BirthdayViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeBirthdayRepository()
        reminderManager = FakeReminderManager()
        viewModel = BirthdayViewModel(
            BirthdayUseCases(
                getAllBirthdays = GetAllBirthdays(repository),
                getUpcomingBirthdays = GetUpcomingBirthdays(repository),
                getBirthdayById = GetBirthdayById(repository),
                addBirthday = AddBirthday(repository),
                updateBirthday = UpdateBirthday(repository),
                deleteBirthday = DeleteBirthday(repository)
            ),
            reminderManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- M-18(a): no fabricated 1/1 for contacts without a birthday ----

    @Test
    fun `contact without a birthday opens the editor with unknown month and day`() {
        viewModel.openEditorForNew(
            ImportedContact(name = "Sara", month = null, day = null, year = null, contactId = "c9")
        )
        val state = viewModel.editorState.value
        assertEquals("Sara", state.name)
        assertNull(state.month)
        assertNull(state.day)
        assertTrue(viewModel.isEditorOpen.value)
    }

    @Test
    fun `save is blocked while month and day are unknown`() {
        viewModel.openEditorForNew(
            ImportedContact(name = "Sara", month = null, day = null, year = null, contactId = "c9")
        )
        viewModel.save()
        assertEquals("bad_month", viewModel.editorState.value.error)
        assertTrue(repository.rows.isEmpty())
        assertTrue(viewModel.isEditorOpen.value)
        assertTrue(reminderManager.scheduled.isEmpty())
    }

    @Test
    fun `save succeeds once the user picks a date`() {
        viewModel.openEditorForNew(
            ImportedContact(name = "Sara", month = null, day = null, year = null, contactId = "c9")
        )
        viewModel.onMonthChange(5)
        viewModel.onDayChange(6)
        viewModel.save()
        val saved = repository.rows.values.single()
        assertEquals(5, saved.month)
        assertEquals(6, saved.day)
        assertEquals("c9", saved.contactId)
        assertFalse(viewModel.isEditorOpen.value)
        assertEquals(listOf(saved.id), reminderManager.scheduled)
    }

    @Test
    fun `contact with a birthday prefills its real date`() {
        viewModel.openEditorForNew(
            ImportedContact(name = "Ali", month = 2, day = 29, year = 1992, contactId = "c3")
        )
        val state = viewModel.editorState.value
        assertEquals(2, state.month)
        assertEquals(29, state.day)
        assertEquals(1992, state.birthYear)
        assertTrue(state.yearKnown)
    }

    // ---- M-19: picking a contact is handled by the ViewModel ----

    @Test
    fun `unreadable picked contact still opens a blank editor`() {
        // A mock resolver answers null cursors, so the reader degrades to null —
        // the editor must open empty instead of crashing or doing IO on the caller.
        val resolver: ContentResolver = mock()
        val uri: Uri = mock()
        viewModel.onContactPicked(resolver, uri)
        // The read hops to Dispatchers.IO; wait briefly for the editor to open.
        val deadline = System.currentTimeMillis() + 5_000
        while (!viewModel.isEditorOpen.value && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertTrue(viewModel.isEditorOpen.value)
        assertNull(viewModel.editorState.value.month)
        assertNull(viewModel.editorState.value.day)
    }

    // ---- M-21: reminder is cancelled only when the delete succeeds ----

    @Test
    fun `failed delete keeps the reminder and surfaces an error`() {
        val birthday = Birthday(id = 7, name = "Sara", month = 5, day = 6)
        repository.rows[7] = birthday
        repository.deleteResult = Result.Error(IllegalStateException("db gone"))

        viewModel.delete(birthday)

        assertTrue(reminderManager.cancelled.isEmpty())
        assertEquals("delete_failed", viewModel.error.value)
        assertTrue(repository.rows.containsKey(7L))
    }

    @Test
    fun `successful delete cancels the reminder`() {
        val birthday = Birthday(id = 7, name = "Sara", month = 5, day = 6)
        repository.rows[7] = birthday

        viewModel.delete(birthday)

        assertEquals(listOf(7L), reminderManager.cancelled)
        assertNull(viewModel.error.value)
        assertFalse(repository.rows.containsKey(7L))
    }
}
