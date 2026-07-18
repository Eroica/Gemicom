package app.gemicom.views.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gemicom.IDb
import app.gemicom.models.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.conf.DIGlobalAware
import org.kodein.di.instance
import java.nio.file.Path

class SettingsViewModel : ViewModel(), DIGlobalAware {
    private val Db: IDb by instance()
    private val CacheDir: Path by instance(tag = "CACHE_DIR")
    private val Tabs: ITabs by instance()
    private val Certificates: ICertificates by instance()
    private val AppSettings: AppSettings by instance()
    private val Dispatcher: CoroutineDispatcher by instance()

    val isDarkTheme: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val home: StateFlow<String>
        field = MutableStateFlow("")

    val isShowInline: StateFlow<Boolean>
        field = MutableStateFlow(true)

    val initialization: Job = viewModelScope.launch(Dispatcher) {
        isDarkTheme.value = AppSettings.isDarkTheme
        home.value = AppSettings.home
        isShowInline.value = AppSettings.isShowImagesInline
    }

    suspend fun setDarkTheme(isDark: Boolean) = withContext(Dispatcher) {
        AppSettings.isDarkTheme = isDark
    }

    suspend fun setHome(home: String) = withContext(Dispatcher) {
        AppSettings.home = home
    }

    suspend fun setShowImagesInline(isShowInline: Boolean) = withContext(Dispatcher) {
        AppSettings.isShowImagesInline = isShowInline
    }

    suspend fun clearCertificates() = withContext(Dispatcher) {
        Certificates.clear()
    }

    suspend fun clearCache() = withContext(Dispatcher) {
        SqlDocuments.purge(null, Db)
        Tabs.all().forEach { ScopedTab(it).close() }
        Tabs.clear()
        SqliteCache.purge(CacheDir, Db)
    }

    suspend fun resetPreferences() = withContext(Dispatcher) {
        AppSettings.clear()
        home.value = ""
        isDarkTheme.value = false
        isShowInline.value = false
    }
}
