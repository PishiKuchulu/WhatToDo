package com.example.pfptodo.domain.usecase.birthday

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Birthday
import com.example.pfptodo.domain.repository.BirthdayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BirthdayUseCasesTest {

    private lateinit var fakeRepo: FakeBirthdayRepository
    private lateinit var useCases: BirthdayUseCases

    @Before
    fun setUp() {
        fakeRepo = FakeBirthdayRepository()
        useCases = BirthdayUseCases(
            getAllBirthdays = GetAllBirthdays(fakeRepo),
            getUpcomingBirthdays = GetUpcomingBirthdays(fakeRepo),
            getBirthdayById = GetBirthdayById(fakeRepo),
            addBirthday = AddBirthday(fakeRepo),
            updateBirthday = UpdateBirthday(fakeRepo),
            deleteBirthday = DeleteBirthday(fakeRepo)
        )
    }

    // ---- CRUD ----

    @Test
    fun addBirthday_returnsGeneratedId_andAppearsInFlow() = runBlocking {
        val result = useCases.addBirthday(Birthday(name = "Ali", month = 5, day = 10, birthYear = 1990))

        assertTrue(result is Result.Success)
        val id = (result as Result.Success).data
        assertTrue(id > 0L)
        assertEquals(1, useCases.getAllBirthdays().first().size)
    }

    @Test
    fun getBirthdayById_returnsBirthday_whenPresent() = runBlocking {
        val id = (useCases.addBirthday(Birthday(name = "Mina", month = 12, day = 1)) as Result.Success).data

        val result = useCases.getBirthdayById(id)

        assertTrue(result is Result.Success)
        assertEquals("Mina", (result as Result.Success).data?.name)
    }

    @Test
    fun getBirthdayById_returnsNull_whenAbsent() = runBlocking {
        val result = useCases.getBirthdayById(999L)

        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)
    }

    @Test
    fun updateBirthday_persistsChanges() = runBlocking {
        val id = (useCases.addBirthday(Birthday(name = "Old", month = 1, day = 1)) as Result.Success).data

        val result = useCases.updateBirthday(Birthday(id = id, name = "New", month = 1, day = 1))

        assertTrue(result is Result.Success)
        val fetched = useCases.getBirthdayById(id) as Result.Success
        assertEquals("New", fetched.data?.name)
    }

    @Test
    fun updateBirthday_returnsError_forUnknownId() = runBlocking {
        val result = useCases.updateBirthday(Birthday(id = 4242L, name = "Ghost", month = 1, day = 1))

        assertTrue(result is Result.Error)
    }

    @Test
    fun deleteBirthday_removesIt() = runBlocking {
        val id = (useCases.addBirthday(Birthday(name = "Temp", month = 3, day = 3)) as Result.Success).data

        val result = useCases.deleteBirthday(Birthday(id = id, name = "Temp", month = 3, day = 3))

        assertTrue(result is Result.Success)
        assertTrue(useCases.getAllBirthdays().first().isEmpty())
    }

    // ---- ValidateBirthday (pure) ----

    private val validator = ValidateBirthday()
    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun validate_rejectsBlankName() {
        assertEquals("name_blank", validator("   ", 5, 10, null, today))
    }

    @Test
    fun validate_rejectsBadMonth() {
        assertEquals("bad_month", validator("Ali", 0, 10, null, today))
        assertEquals("bad_month", validator("Ali", 13, 10, null, today))
    }

    @Test
    fun validate_rejectsBadDay() {
        assertEquals("bad_day", validator("Ali", 2, 30, null, today))
        assertEquals("bad_day", validator("Ali", 4, 31, null, today))
        assertEquals("bad_day", validator("Ali", 5, 0, null, today))
    }

    @Test
    fun validate_rejectsTooOldBirthYear() {
        assertEquals("bad_year", validator("Ali", 5, 10, 1899, today))
    }

    @Test
    fun validate_rejectsFutureBirthDate() {
        assertEquals("future_date", validator("Ali", 5, 10, 2027, today))
        assertEquals("future_date", validator("Ali", 11, 10, 2026, today))
        assertEquals("future_date", validator("Ali", 10, 18, 2026, today))
    }

    @Test
    fun validate_acceptsValidBirthday() {
        assertNull(validator("Ali", 5, 10, 1990, today))
        assertNull(validator("Ali", 2, 29, null, today))
        assertNull(validator("Ali", 10, 17, 2026, today))
    }

    // ---- GetUpcomingBirthdays (pure projection) ----

    @Test
    fun project_ordersByDaysUntil_andComputesAge() {
        val projection = GetUpcomingBirthdays(fakeRepo).project(
            listOf(
                Birthday(name = "Mina", month = 5, day = 10),
                Birthday(name = "Zara", month = 10, day = 18),
                Birthday(name = "Ali", month = 10, day = 17, birthYear = 1990)
            ),
            today
        )

        assertEquals(listOf("Ali", "Zara", "Mina"), projection.map { it.birthday.name })

        val ali = projection[0]
        assertTrue(ali.isToday)
        assertEquals(0L, ali.daysUntil)
        assertEquals(36, ali.age)

        val zara = projection[1]
        assertEquals(1L, zara.daysUntil)
        assertEquals(LocalDate.of(2026, 10, 18), zara.occurrence)
        assertNull(zara.age)
    }

    @Test
    fun project_handlesLeapDayFallback() {
        val projection = GetUpcomingBirthdays(fakeRepo).project(
            listOf(Birthday(name = "Leap", month = 2, day = 29, birthYear = 2000)),
            today
        )

        val leap = projection.single()
        // 2026 and 2027 are not leap years: the next occurrence is Feb 28, 2027.
        assertEquals(LocalDate.of(2027, 2, 28), leap.occurrence)
        assertEquals(27, leap.age)
    }

    @Test
    fun invoke_flowWrapper_projectsWithClock() = runBlocking {
        fakeRepo.seed(Birthday(name = "Ali", month = 10, day = 17, birthYear = 1990))
        fakeRepo.seed(Birthday(name = "Mina", month = 5, day = 10))
        val fixed = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneId.systemDefault())
        val useCase = GetUpcomingBirthdays(fakeRepo, fixed)

        val upcoming = useCase().first()

        assertEquals(listOf("Ali", "Mina"), upcoming.map { it.birthday.name })
        assertTrue(upcoming.first().isToday)
    }

    // ------------------------------------------------------------------

    private class FakeBirthdayRepository : BirthdayRepository {
        private val birthdays = mutableListOf<Birthday>()
        private val flow = MutableStateFlow<List<Birthday>>(emptyList())
        private var nextId = 1L

        fun seed(birthday: Birthday) {
            val withId = if (birthday.id == 0L) birthday.copy(id = nextId++) else birthday
            nextId = maxOf(nextId, withId.id + 1)
            birthdays.add(withId)
            flow.value = birthdays.toList()
        }

        override fun getAllBirthdays(): Flow<List<Birthday>> = flow

        override suspend fun getAllBirthdaysRaw(): List<Birthday> = birthdays.toList()

        override suspend fun getBirthdayById(id: Long): Result<Birthday?> =
            Result.Success(birthdays.find { it.id == id })

        override suspend fun getByContactId(contactId: String): Result<Birthday?> =
            Result.Success(birthdays.find { it.contactId == contactId })

        override suspend fun insertBirthday(birthday: Birthday): Result<Long> {
            seed(birthday)
            return Result.Success(birthdays.last().id)
        }

        override suspend fun updateBirthday(birthday: Birthday): Result<Unit> {
            val index = birthdays.indexOfFirst { it.id == birthday.id }
            if (index < 0) return Result.Error(NoSuchElementException("Birthday ${birthday.id} not found"))
            birthdays[index] = birthday
            flow.value = birthdays.toList()
            return Result.Success(Unit)
        }

        override suspend fun deleteBirthday(birthday: Birthday): Result<Unit> {
            birthdays.removeAll { it.id == birthday.id }
            flow.value = birthdays.toList()
            return Result.Success(Unit)
        }

        override suspend fun deleteById(id: Long): Result<Unit> {
            birthdays.removeAll { it.id == id }
            flow.value = birthdays.toList()
            return Result.Success(Unit)
        }
    }
}
