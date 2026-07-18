package app.gemicom.views.models

import app.gemicom.CachableGeminiClient
import app.gemicom.GeminiClient
import app.gemicom.IDb
import app.gemicom.Sql
import app.gemicom.models.*
import kotlinx.serialization.json.Json
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
        try {
            return load(tab.navigate(address, pushToHistory), isCheckCache)
        } catch (e: Exception) {
            /* On exception, need to clear last history entry */
            if (pushToHistory) {
                Db.update(Sql.Tab_SetHistory) {
                    val entries = Json.encodeToString(history.dropLast(1))
                    it.setString(1, entries)
                    it.setLong(2, id)
                }
            }

            throw e
        }
    }

    fun load(uri: String, isCheckCache: Boolean = true): IGeminiDocument {
        try {
            val content = client.get(uri, isCheckCache)
            tab.status = TabStatus.VALID
            return ChunkedGeminiDocument.fromText(currentLocation, content)
        } catch (e: Exception) {
            tab.status = TabStatus.INVALID
            throw e
        }
    }
}
