package app.gemicom.views.models

import androidx.lifecycle.ViewModel
import app.gemicom.models.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.kodein.di.conf.DIGlobalAware
import org.kodein.di.instance

class BrowserPageViewModel : ViewModel(), DIGlobalAware {
    private val Certificates: ICertificates by instance()
    private val Dispatcher: CoroutineDispatcher by instance()

    lateinit var tab: ScopedTab
    var isInitialized = false

    val isLoading: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val currentUrl: StateFlow<String>
        field = MutableStateFlow("")

    val document: StateFlow<IGeminiDocument>
        field = MutableStateFlow<IGeminiDocument>(EmptyGeminiDocument)

    suspend fun load(tab: ScopedTab) = withContext(Dispatcher) {
        this@BrowserPageViewModel.tab = tab
        isInitialized = true
        currentUrl.value = tab.currentLocation

        when (tab.status) {
            TabStatus.BLANK -> document.value = EmptyGeminiDocument
            TabStatus.VALID -> {
                try {
                    isLoading.value = true
                    val document = withContext(Dispatcher) { tab.load(tab.currentLocation, false) }
                    this@BrowserPageViewModel.document.value = document
                } finally {
                    isLoading.value = false
                }
            }

            TabStatus.INVALID -> document.value = InvalidGeminiDocument
        }
    }

    suspend fun start(address: String) {
        /* Whatever comes from here, act as if gemini:// was prepended to it */
        if (address.startsWith("gemini://")) {
            navigate(address, pushToHistory = true, isCheckCache = false)
        } else {
            navigate("gemini://$address", pushToHistory = true, isCheckCache = false)
        }
    }

    suspend fun back() = withContext(Dispatcher) {
        try {
            document.value = tab.load(tab.back(), true)
        } finally {
            currentUrl.value = tab.currentLocation
        }
    }

    suspend fun forward() = withContext(Dispatcher) {
        try {
            document.value = tab.load(tab.forward(), true)
        } finally {
            currentUrl.value = tab.currentLocation
        }
    }

    suspend fun input(query: String) {
        val uri = GeminiHost.appendArgs(tab.currentLocation, query)
        navigate(uri, pushToHistory = true, isCheckCache = true)
    }

    suspend fun navigate(
        address: String, pushToHistory: Boolean = true, isCheckCache: Boolean = true
    ) = withContext(Dispatcher) {
        try {
            isLoading.value = true
            document.value = tab.navigate(address, pushToHistory, isCheckCache)
        } finally {
            currentUrl.value = tab.currentLocation
            isLoading.value = false
        }
    }

    suspend fun updateCertificate(host: String, hash: String) = withContext(Dispatcher) {
        Certificates.replace(host, hash)
    }
}
