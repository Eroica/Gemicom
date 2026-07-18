package app.gemicom.models

import app.gemicom.DATE_FORMAT
import app.gemicom.IDb
import app.gemicom.Sql
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.CRC32

class NoMoreHistory : Exception()
class NoNextEntry : Exception()
class TabNotFound(val id: Long) : Exception()

enum class TabStatus(val code: Int) {
    BLANK(0), VALID(1), INVALID(2);

    companion object {
        private val map = entries.associateBy(TabStatus::code)

        fun fromInt(id: Int) = map[id] ?: BLANK
    }
}

interface ITab {
    val id: Long
    val currentLocation: String
    val history: List<String>
    var position: Int
    val createdAt: LocalDateTime
    var status: TabStatus
    val uniqueId: Long

    fun peekPrevious(): String
    fun peekNext(): String
    fun back(): String
    fun forward(): String

    fun navigate(address: String, pushToHistory: Boolean = true): String
    fun resolve(reference: String): String

    fun canGoBack(): Boolean = false
    fun canGoForward(): Boolean = false
}

interface ITabs {
    fun all(): List<ITab>
    fun new(): ITab
    fun delete(tabId: Long)
    fun clear()
    fun size(): Long
    fun get(id: Long): ITab
}

class SqlTab(
    override val id: Long,
    override val createdAt: LocalDateTime,
    private val db: IDb,
    status: TabStatus
) : ITab {
    override val currentLocation: String
        get() = history.getOrNull(position) ?: ""

    override val history: List<String>
        get() = db.query(Sql.Tab_GetHistory, { it.setLong(1, id) }) {
            buildList {
                while (it.next()) {
                    add(it.getString(1))
                }
            }
        }

    override var position: Int = db.query(Sql.Tab_GetPosition, { it.setLong(1, id) }) {
        if (it.next()) {
            it.getInt(1)
        } else {
            0
        }
    }
        set(value) {
            db.update(Sql.Tab_SetPosition) {
                it.setInt(1, value)
                it.setLong(2, id)
            }
            field = value
        }

    override var status = status
        set(value) {
            db.update(Sql.Tab_SetStatus) {
                it.setInt(1, value.code)
                it.setLong(2, id)
            }
            field = value
        }

    override val uniqueId = CRC32().let {
        val epochMillis = createdAt.atZone(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val bytes = ByteBuffer.allocate(16)
            .putLong(id)
            .putLong(epochMillis)
            .array()
        it.update(bytes)
        it.value and 0xffffffffL
    }

    private var geminiHost = history.getOrNull(position)?.let { GeminiHost.fromAddress(it) }

    override fun peekPrevious(): String {
        try {
            return history[position - 1]
        } catch (_: IndexOutOfBoundsException) {
            throw NoMoreHistory()
        }
    }

    override fun peekNext(): String {
        try {
            return history[position + 1]
        } catch (_: IndexOutOfBoundsException) {
            throw NoNextEntry()
        }
    }

    override fun back(): String {
        peekPrevious()
        return navigate(history[--position], false)
    }

    override fun forward(): String {
        peekNext()
        return navigate(history[++position], false)
    }

    override fun canGoBack() = position > 0

    override fun canGoForward() = position < history.size - 1

    override fun resolve(reference: String): String {
        return geminiHost?.resolve(reference) ?: ""
    }

    override fun navigate(address: String, pushToHistory: Boolean): String {
        if (geminiHost == null) {
            val newHost = GeminiHost.fromAddress(address)
            db.update(Sql.Tab_SetHistory) {
                val entries = Json.encodeToString(listOf(newHost.location))
                it.setString(1, entries)
                it.setLong(2, id)
            }
            geminiHost = newHost
            return newHost.location
        }

        val locationBeforeNavigate = currentLocation
        val nextLocation = geminiHost!!.navigate(address)

        if (pushToHistory && locationBeforeNavigate != nextLocation) {
            addToHistory(nextLocation)
        }

        return nextLocation
    }

    private fun addToHistory(address: String) {
        /* If history is not at last location, drop everything behind it */
        var updatedHistory = history.toMutableList()
        if (position != updatedHistory.size - 1) {
            updatedHistory = updatedHistory.dropLast((updatedHistory.size - position - 1).coerceAtLeast(0))
                .toMutableList()
        }

        updatedHistory.add(address)
        db.update(Sql.Tab_SetHistory) {
            val entries = Json.encodeToString(updatedHistory)
            it.setString(1, entries)
            it.setLong(2, id)
        }
        position++
    }
}

class SqlTabs(private val db: IDb) : ITabs {
    override fun all(): List<ITab> {
        return db.query(Sql.Tab_All, {}) {
            buildList {
                while (it.next()) {
                    val id = it.getLong(1)
                    val status = TabStatus.fromInt(it.getInt(2))
                    val createdAt = LocalDateTime.parse(it.getString(4), DATE_FORMAT)
                    val tab = SqlTab(id, createdAt, db, status)
                    add(tab)
                }
            }
        }
    }

    override fun new(): ITab {
        val (tabId, createdAt) = db.update(Sql.Tab_Create, {}) {
            it.getLong(1) to LocalDateTime.parse(it.getString(2), DATE_FORMAT)
        }

        return SqlTab(tabId, createdAt, db, TabStatus.BLANK)
    }

    override fun delete(tabId: Long) {
        db.update(Sql.Tab_Delete) { it.setLong(1, tabId) }
    }

    override fun clear() {
        db.update(Sql.Tab_Purge)
    }

    override fun size(): Long {
        return db.query(Sql.Tab_Count, {}) {
            it.getLong(1)
        }
    }

    override fun get(id: Long): ITab {
        return db.query(Sql.Tab_Get, {
            it.setLong(1, id)
        }) {
            if (it.next()) {
                val id = it.getLong(1)
                val status = TabStatus.fromInt(it.getInt(2))
                val createdAt = LocalDateTime.parse(it.getString(4), DATE_FORMAT)
                SqlTab(id, createdAt, db, status)
            } else {
                throw TabNotFound(id)
            }
        }
    }
}
