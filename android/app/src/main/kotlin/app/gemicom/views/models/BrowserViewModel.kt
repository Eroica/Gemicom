package app.gemicom.views.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gemicom.models.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.conf.DIGlobalAware
import org.kodein.di.instance

class BrowserViewModel : ViewModel(), DIGlobalAware {
    private val Tabs: ITabs by instance()
    private val AppSettings: AppSettings by instance()
    private val Dispatcher: CoroutineDispatcher by instance()
    private val Writer: CoroutineDispatcher by instance(tag = "WRITER")

    private val _tabs = MutableLiveData<List<ScopedTab>>()
    val tabs: LiveData<List<ScopedTab>> = _tabs

    private val _currentTab: MutableLiveData<ScopedTab> = MutableLiveData()
    val currentTab: LiveData<ScopedTab> = _currentTab

    val hasClipboardContent = MutableLiveData(false)
    val isNoNetwork = MutableLiveData(false)

    val initialization: Job = viewModelScope.launch(Dispatcher) {
        val tabs = Tabs.all()

        if (tabs.isNotEmpty()) {
            val lastTabId = AppSettings.selectedTab ?: tabs.last().id
            val lastTab = tabs.first { it.id == lastTabId }
            _tabs.postValue(tabs.map { ScopedTab(it) })
            _currentTab.postValue(ScopedTab(lastTab))
        } else {
            restart()
        }
    }

    suspend fun new() = withContext(Writer) {
        val tab = Tabs.new()
        val newTabs = _tabs.value.orEmpty().toMutableList()
        newTabs.add(ScopedTab(tab))
        _tabs.postValue(newTabs)
    }

    suspend fun close(position: Int) = withContext(Writer) {
        val tabs = _tabs.value ?: return@withContext
        val closingTab = tabs[position]
        closingTab.close()
        val updatedTabs = tabs.toMutableList()
        updatedTabs.remove(closingTab)
        Tabs.delete(closingTab.id)
        _tabs.postValue(updatedTabs)
    }

    suspend fun select(position: Int) = withContext(Writer) {
        val tabs = _tabs.value ?: return@withContext
        val selectedTab = tabs[position]

        /** @since 2025-06-06 If current tab is selected, no need to do anything */
        if (selectedTab.id == _currentTab.value?.id) {
            return@withContext
        }

        AppSettings.selectedTab = selectedTab.id
        _currentTab.postValue(selectedTab)
    }

    suspend fun restart() = withContext(Writer) {
        val tab = ScopedTab(Tabs.new())
        _tabs.postValue(listOf(tab))
        _currentTab.postValue(tab)
    }

    suspend fun reset() = withContext(Writer) {
        _tabs.value.orEmpty().forEach { it.close() }
        Tabs.clear()
        val tab = ScopedTab(Tabs.new())
        _tabs.postValue(listOf(tab))
        _currentTab.postValue(tab)
    }

    suspend fun reload() = withContext(Dispatcher) {
        val tabs = Tabs.all().map { ScopedTab(it) }
        _tabs.postValue(tabs)
    }
}
