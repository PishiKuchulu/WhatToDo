package com.example.pfptodo.ui.screens.settings

import com.example.pfptodo.core.repository.LanguageRepository
import com.example.pfptodo.core.repository.ThemeRepository
import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.usecase.backup.BackupUseCases
import com.example.pfptodo.domain.usecase.tag.TagUseCases
import com.example.pfptodo.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val themeRepository: ThemeRepository = mock()
    private val languageRepository: LanguageRepository = mock()
    private val tagUseCases: TagUseCases = mock()
    private val backupUseCases: BackupUseCases = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(themeRepository.themeFlow).thenReturn(flowOf(AppTheme.SYSTEM))
        whenever(languageRepository.languageFlow).thenReturn(flowOf("en"))
        whenever(tagUseCases.getAllTags).thenReturn(mock())
        whenever(tagUseCases.getAllTags.invoke()).thenReturn(flowOf(listOf(Tag(id = 1L, name = "Important"))))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tag management operations call use cases`() = runTest {
        whenever(tagUseCases.addTag).thenReturn(mock())
        whenever(tagUseCases.addTag.invoke(any())).thenReturn(Result.Success(10L))
        whenever(tagUseCases.updateTag).thenReturn(mock())
        whenever(tagUseCases.updateTag.invoke(any())).thenReturn(Result.Success(Unit))
        whenever(tagUseCases.deleteTag).thenReturn(mock())
        whenever(tagUseCases.deleteTag.invoke(any())).thenReturn(Result.Success(Unit))

        val viewModel = SettingsViewModel(
            themeRepository = themeRepository,
            languageRepository = languageRepository,
            tagUseCases = tagUseCases,
            backupUseCases = backupUseCases
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCreateTag("Shopping", "#FFD54F")
        testDispatcher.scheduler.advanceUntilIdle()
        verify(tagUseCases.addTag).invoke(any())

        val tag = Tag(id = 1L, name = "Urgent")
        viewModel.onUpdateTag(tag)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(tagUseCases.updateTag).invoke(tag)

        viewModel.onDeleteTag(tag)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(tagUseCases.deleteTag).invoke(tag)
    }
}
