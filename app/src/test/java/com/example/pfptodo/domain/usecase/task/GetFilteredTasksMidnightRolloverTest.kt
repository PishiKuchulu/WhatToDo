package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.domain.model.SortOrder
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.model.TaskFilter
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

/**
 * M-14: the TODAY window must roll over when local midnight passes, even if
 * the task table itself does not change. Kept separate from
 * [GetFilteredTasksUseCaseTest] because the use case applies
 * flowOn(Dispatchers.Default): the ticker's wakeup runs in real time here, so
 * the fake clock starts 200 ms before midnight (wakeup ≈ 200 ms + the
 * ticker's 1 s epsilon) and the test polls with a deadline.
 */
class GetFilteredTasksMidnightRolloverTest {

    /** A [Clock] whose instant the test moves by hand. */
    private class MutableClock(
        var currentInstant: Instant,
        private val currentZone: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = currentZone
        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)
        override fun instant(): Instant = currentInstant
    }

    private val repository: TaskRepository = mock()

    @Test
    fun `TODAY filter rolls over at midnight without new task data`() = runBlocking {
        val zone = ZoneId.of("UTC")
        val clock = MutableClock(Instant.parse("2026-10-22T23:59:59.800Z"), zone)
        val useCase = GetFilteredTasksUseCase(repository, clock)
        val dueTomorrow = Task(
            id = 12, title = "Tomorrow",
            dueDate = Instant.parse("2026-10-23T10:00:00Z")
        )
        whenever(repository.getAllMainTasks()).thenReturn(flowOf(listOf(dueTomorrow)))

        val emissions = CopyOnWriteArrayList<List<Task>>()
        val job = launch {
            useCase("", TaskFilter(dateFilter = TaskFilter.DateFilter.TODAY), SortOrder.MANUAL)
                .collect { emissions.add(it) }
        }
        try {
            // Before midnight the task is due tomorrow: not in TODAY.
            withTimeout(5_000) { while (emissions.isEmpty()) delay(20) }
            assertTrue(emissions.first().isEmpty())

            // Cross midnight on the fake clock; the day ticker must trigger a
            // recompute on its own and the task must enter the TODAY window.
            clock.currentInstant = Instant.parse("2026-10-23T00:00:01Z")
            withTimeout(10_000) {
                while (emissions.none { emission -> emission.map { it.id } == listOf(12L) }) delay(20)
            }
            assertEquals(listOf(12L), emissions.last().map { it.id })
        } finally {
            job.cancel()
        }
    }
}
