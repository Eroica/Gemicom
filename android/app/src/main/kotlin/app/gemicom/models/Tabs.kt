package app.gemicom.models

import app.gemicom.DATE_FORMAT
import app.gemicom.IDb
import app.gemicom.Sql
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

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
    val createdAt: LocalDateTime
    var status: TabStatus

    fun peekPrevious(): String
    fun peekNext(): String
    fun back(): String
    fun forward(): String

    fun start(address: String): String
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
}

class SqlTab(
    override val id: Long,
    override val createdAt: LocalDateTime,
    private val db: IDb,
    private var geminiHost: GeminiHost? = null,
    status: TabStatus = TabStatus.BLANK
) : ITab {
    private var currentIndex = 0

    override val currentLocation: String
        get() = geminiHost?.location ?: ""

    override val history: List<String>
        get() = db.query(Sql.Tab_GetHistory, { it.setLong(1, id) }) {
            buildList {
                while (it.next()) {
                    add(it.getString(1))
                }
            }
        }

    override var status = status
        set(value) {
            db.update(Sql.Tab_SetStatus) {
                it.setInt(1, value.code)
                it.setLong(2, id)
            }
            field = value
        }

    init {
        /* Tab always starts with index on latest entry. This means that recreating a tab (from DB)
           actually "forwards" tabs to their latest entry. */
        currentIndex = history.size - 1
    }

    override fun peekPrevious(): String {
        try {
            return history[currentIndex - 1]
        } catch (_: IndexOutOfBoundsException) {
            throw NoMoreHistory()
        }
    }

    override fun peekNext(): String {
        try {
            return history[currentIndex + 1]
        } catch (_: IndexOutOfBoundsException) {
            throw NoNextEntry()
        }
    }

    override fun back(): String {
        peekPrevious()
        return navigate(history[--currentIndex], false)
    }

    override fun forward(): String {
        peekNext()
        return navigate(history[++currentIndex], false)
    }

    override fun canGoBack() = currentIndex > 0

    override fun canGoForward() = currentIndex < history.size - 1

    override fun resolve(reference: String): String {
        return geminiHost?.resolve(reference) ?: ""
    }

    override fun start(address: String): String {
        geminiHost = GeminiHost.fromAddress(address)
        addToHistory(currentLocation)

        return currentLocation
    }

    override fun navigate(address: String, pushToHistory: Boolean): String {
        try {
            val locationBeforeNavigate = currentLocation
            geminiHost!!.navigate(address)

            if (pushToHistory && locationBeforeNavigate != currentLocation) {
                addToHistory(currentLocation)
            }

            return currentLocation
        } catch (_: NullPointerException) {
            return start(address)
        }
    }

    private fun addToHistory(address: String) {
        /* If history is not at last location, drop everything behind it */
        var updatedHistory = history.toMutableList()
        if (currentIndex != updatedHistory.size - 1) {
            updatedHistory = updatedHistory.dropLast(updatedHistory.size - currentIndex - 1)
                .toMutableList()
        }

        updatedHistory.add(address)
        db.update(Sql.Tab_SetHistory) {
            val entries = Json.encodeToString(updatedHistory)
            it.setString(1, entries)
            it.setLong(2, id)
        }
        currentIndex++
    }
}

class SqlTabs(private val db: IDb) : ITabs {
    override fun all(): List<ITab> {
        return db.query(Sql.Tab_All, {}) {
            buildList {
                while (it.next()) {
                    val id = it.getLong(1)
                    val status = TabStatus.fromInt(it.getInt(2))
                    val location = it.getString(3) ?: ""
                    val createdAt = LocalDateTime.parse(it.getString(4), DATE_FORMAT)
                    val tab = when (status) {
                        TabStatus.VALID, TabStatus.INVALID -> {
                            val geminiHost = GeminiHost.fromAddress(location)
                            SqlTab(id, createdAt, db, geminiHost, status)
                        }

                        else -> SqlTab(id, createdAt, db)
                    }
                    add(tab)
                }
            }
        }
    }

    override fun new(): ITab {
        val (tabId, createdAt) = db.update(Sql.Tab_Create, {}) {
            it.getLong(1) to LocalDateTime.parse(it.getString(2), DATE_FORMAT)
        }

        return SqlTab(tabId, createdAt, db)
    }

    override fun delete(tabId: Long) {
        db.update(Sql.Tab_Delete) { it.setLong(1, tabId) }
    }

    override fun clear() {
        db.update(Sql.Tab_Purge)
    }
}
