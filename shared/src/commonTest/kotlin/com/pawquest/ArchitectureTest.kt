package com.pawquest

import com.pawquest.di.createAppContainer
import com.pawquest.domain.model.SectionPlaceholder
import com.pawquest.domain.repository.PlaceholderRepository
import com.pawquest.domain.usecase.GetPlaceholder
import com.pawquest.presentation.navigation.Destination
import com.pawquest.presentation.placeholder.PlaceholderViewModel
import com.pawquest.presentation.state.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.koin.core.parameter.parametersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class ArchitectureTest {
    @Test
    fun allDestinationsResolveThroughDiAndProduceContent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val container = createAppContainer()
        try {
            val repository = container.koin.get<PlaceholderRepository>()
            assertSame(repository, container.koin.get<PlaceholderRepository>())
            assertEquals(5, Destination.entries.size)
            assertEquals(5, Destination.entries.map { it.route }.distinct().size)
            Destination.entries.forEach { destination ->
                val model = container.koin.get<PlaceholderViewModel> { parametersOf(destination.route) }
                assertIs<UiState.Loading>(model.state.value)
                advanceUntilIdle()
                val content = assertIs<UiState.Content<SectionPlaceholder>>(model.state.value)
                assertEquals(destination.label, content.value.title)
                assertNotSame(model, container.koin.get<PlaceholderViewModel> { parametersOf(destination.route) })
            }
            advanceUntilIdle()
        } finally {
            container.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failuresCanBeRetriedAndMissingContentIsEmpty() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var shouldFail = true
        val repository = object : PlaceholderRepository {
            override suspend fun getPlaceholder(sectionId: String): SectionPlaceholder? {
                if (shouldFail) error("Internal details must not reach the UI")
                return null
            }
        }
        try {
            val model = PlaceholderViewModel("unknown", GetPlaceholder(repository))
            advanceUntilIdle()
            assertEquals(UiState.Error("This screen couldn't be loaded. Please try again."), model.state.value)
            shouldFail = false
            model.retry()
            assertIs<UiState.Loading>(model.state.value)
            advanceUntilIdle()
            assertIs<UiState.Empty>(model.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancellationDoesNotBecomeAnError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = object : PlaceholderRepository {
            override suspend fun getPlaceholder(sectionId: String): SectionPlaceholder? = throw CancellationException()
        }
        try {
            val model = PlaceholderViewModel("pet_home", GetPlaceholder(repository))
            advanceUntilIdle()
            assertIs<UiState.Loading>(model.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unknownSavedRouteFallsBackToHome() {
        assertEquals(Destination.PetHome, Destination.fromRoute("removed_destination"))
    }
}
