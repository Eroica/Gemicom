package app.gemicom.views.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gemicom.models.AppSettings
import app.gemicom.models.ITabs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.conf.DIGlobalAware
import org.kodein.di.instance

class BrowserViewModel : ViewModel(), DIGlobalAware {
    private val Tabs: ITabs by instance()
    private val AppSettings: AppSettings by instance()
    private val Dispatcher: CoroutineDispatcher by instance()

    val tabs: StateFlow<List<ScopedTab>>
        field = MutableStateFlow(listOf())

    val selectedTab: StateFlow<Int>
        field = MutableStateFlow(0)

    val currentTab: StateFlow<ScopedTab?> = combine(tabs, selectedTab) { list, index ->
        list.getOrNull(index)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = tabs.value.getOrNull(selectedTab.value)
    )

    val hasClipboardContent = MutableStateFlow(false)

    val initialization: Job = viewModelScope.launch(Dispatcher) {
        val tabs = Tabs.all()

        if (tabs.isNotEmpty()) {
            this@BrowserViewModel.tabs.value = tabs.map { ScopedTab(it) }
            selectedTab.value = AppSettings.selectedTab
        } else {
            restart()
        }
    }

    suspend fun new() = withContext(Dispatcher) {
        val tab = Tabs.new()
        val newTabs = tabs.value.toMutableList()
        newTabs.add(ScopedTab(tab))
        tabs.value = newTabs
    }

    suspend fun close(position: Int) = withContext(Dispatcher) {
        val closingTab = tabs.value[position]
        closingTab.close()
        val updatedTabs = tabs.value.toMutableList()
        updatedTabs.remove(closingTab)
        Tabs.delete(closingTab.id)
        tabs.value = updatedTabs
    }

    suspend fun select(position: Int) = withContext(Dispatcher) {
        AppSettings.selectedTab = position
        selectedTab.value = position
    }

    suspend fun restart() = withContext(Dispatcher) {
        val tab = ScopedTab(Tabs.new())
        tabs.value = listOf(tab)
        select(0)
    }

    suspend fun reset() = withContext(Dispatcher) {
        tabs.value.forEach { it.close() }
        Tabs.clear()
        restart()
    }
}
