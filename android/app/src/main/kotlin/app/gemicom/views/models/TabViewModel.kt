package app.gemicom.views.models

import app.gemicom.CachableGeminiClient
import app.gemicom.GeminiClient
import app.gemicom.IDb
import app.gemicom.models.*
import org.kodein.di.conf.DIGlobalAware
import org.kodein.di.instance
import java.nio.file.Path

class ScopedTab(private val tab: ITab) : ITab by tab, AutoCloseable, DIGlobalAware {
    private val Db: IDb by instance()
    private val CacheDir: Path by instance(tag = "CACHE_DIR")
    private val Certificates: ICertificates by instance()

    val cache: SqliteCache by lazy { SqliteCache(tab.id, CacheDir, Db) }
    val client: CachableGeminiClient by lazy {
        CachableGeminiClient(cache, SqlDocuments(tab.id, Db), GeminiClient(Certificates))
    }

    override fun close() {
        client.close()
    }

    fun navigate(
        address: String, pushToHistory: Boolean = true, isCheckCache: Boolean = true
    ): IGeminiDocument {
        return load(tab.navigate(address, pushToHistory), isCheckCache)
    }

    fun load(uri: String, isCheckCache: Boolean = true): IGeminiDocument {
        try {
            val content = client.get(uri, isCheckCache)
            tab.status = TabStatus.VALID
            return ChunkedGeminiDocument.fromText(currentLocation, content)
        } catch (e: Exception) {
            tab.status = TabStatus.INVALID
            throw (e)
        }
    }
}
