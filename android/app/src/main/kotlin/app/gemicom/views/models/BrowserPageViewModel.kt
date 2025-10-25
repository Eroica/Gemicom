package app.gemicom.views.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import app.gemicom.models.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.di.conf.DIGlobalAware
import org.kodein.di.instance

class BrowserPageViewModel : ViewModel(), DIGlobalAware {
    private val Tabs: ITabs by instance()
    private val Certificates: ICertificates by instance()
    private val Dispatcher: CoroutineDispatcher by instance()

    private val _tab = MutableLiveData<ScopedTab>()
    val tab: LiveData<ScopedTab> = _tab

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _currentUrl = MutableLiveData("")
    val currentUrl: LiveData<String> = _currentUrl

    val _document = MutableLiveData<IGeminiDocument>()
    val document: LiveData<IGeminiDocument> = _document

    suspend fun load(id: Long) = withContext(Dispatcher) {
        if (_tab.value != null) {
            return@withContext
        }

        val tab = ScopedTab(Tabs.get(id))
        withContext(Dispatchers.Main) {
            _tab.value = tab
            _currentUrl.value = tab.currentLocation

            when (tab.status) {
                TabStatus.BLANK -> _document.value = EmptyGeminiDocument
                TabStatus.VALID -> {
                    try {
                        _isLoading.value = true
                        val document = withContext(Dispatcher) { tab.load(tab.currentLocation, false) }
                        _document.value = document
                    } finally {
                        _isLoading.value = false
                    }
                }

                TabStatus.INVALID -> _document.value = InvalidGeminiDocument
            }
        }
    }

    suspend fun start(address: String) {
        /* Whatever comes from here, act as if gemini:// was prepended to it */
        _tab.value?.let {
            if (address.startsWith("gemini://")) {
                navigate(address, pushToHistory = true, isCheckCache = false)
            } else {
                navigate("gemini://$address", pushToHistory = true, isCheckCache = false)
            }
        }
    }

    suspend fun back() = withContext(Dispatcher) {
        _tab.value?.let {
            try {
                _document.postValue(it.load(it.back(), true))
            } finally {
                _currentUrl.postValue(it.currentLocation)
            }
        }
    }

    suspend fun forward() = withContext(Dispatcher) {
        _tab.value?.let {
            try {
                _document.postValue(it.load(it.forward(), true))
            } finally {
                _currentUrl.postValue(it.currentLocation)
            }
        }
    }

    suspend fun input(query: String) {
        _tab.value?.let {
            val uri = GeminiHost.appendArgs(it.currentLocation, query)
            navigate(uri, pushToHistory = true, isCheckCache = true)
        }
    }

    suspend fun navigate(
        address: String, pushToHistory: Boolean = true, isCheckCache: Boolean = true
    ) = withContext(Dispatcher) {
        _tab.value?.let {
            try {
                _isLoading.postValue(true)
                _document.postValue(it.navigate(address, pushToHistory, isCheckCache))
            } finally {
                _currentUrl.postValue(it.currentLocation)
                _isLoading.postValue(false)
            }
        }
    }

    suspend fun updateCertificate(host: String, hash: String) = withContext(Dispatcher) {
        Certificates.replace(host, hash)
    }
}
