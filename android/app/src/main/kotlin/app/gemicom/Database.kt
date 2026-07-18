package app.gemicom

import io.github.oshai.kotlinlogging.KotlinLogging
import org.sqlite.SQLiteConnection
import java.nio.file.Path
import java.sql.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
const val DB_NAME = "Gemicom.db"
const val MEDIA_NAME = "Media"

private const val DB_INITIAL_VERSION = 0
private const val DB_CURRENT_VERSION = 2
private val transactionDepth = ThreadLocal<Int>().apply { set(0) }

fun LocalDateTime.toDatabaseString(): String = format(DATE_FORMAT)

interface IDb : AutoCloseable {
    fun <T> query(
        sql: String, setParams: (PreparedStatement) -> Unit = {}, handle: (ResultSet) -> T
    ): T

    fun update(sql: String, setParams: (PreparedStatement) -> Unit = {})

    fun <T> update(
        sql: String, setParams: (PreparedStatement) -> Unit = {}, handle: (ResultSet) -> T
    ): T

    fun transaction(block: () -> Unit)
}

class Db private constructor(uri: String) : IDb {
    companion object {
        fun at(dir: Path) = Db(dir.resolve(DB_NAME).toUri().toString())
        fun memory() = Db(":memory:")
    }

    private val logger = KotlinLogging.logger {}
    private val connection = DriverManager.getConnection(
        "jdbc:sqlite:${uri}?foreign_keys=on&transaction_mode=IMMEDIATE"
    ) as SQLiteConnection
    private val lock = ReentrantReadWriteLock()

    init {
        when (connection.getVersion()) {
            DB_INITIAL_VERSION -> initialize()
            1 -> migrateTo2()
        }
    }

    override fun <T> query(
        sql: String,
        setParams: (PreparedStatement) -> Unit,
        handle: (ResultSet) -> T
    ): T {
        return lock.readLock().withLock {
            connection.prepareStatement(sql).use { statement ->
                setParams(statement)
                statement.executeQuery().use { resultSet ->
                    handle(resultSet)
                }
            }
        }
    }

    override fun update(sql: String, setParams: (PreparedStatement) -> Unit) {
        return lock.writeLock().withLock {
            connection.prepareStatement(sql).use { statement ->
                setParams(statement)
                statement.executeUpdate()
            }
        }
    }

    override fun <T> update(
        sql: String,
        setParams: (PreparedStatement) -> Unit,
        handle: (ResultSet) -> T
    ): T {
        return lock.writeLock().withLock {
            connection.prepareStatement(sql).use { statement ->
                setParams(statement)
                statement.executeQuery().use { resultSet ->
                    handle(resultSet)
                }
            }
        }
    }

    override fun transaction(block: () -> Unit) {
        lock.writeLock().withLock {
            val currentDepth = transactionDepth.get() ?: 0
            val isOuter = currentDepth == 0
            transactionDepth.set(currentDepth + 1)

            if (isOuter) {
                connection.autoCommit = false
            }

            try {
                block()

                if (isOuter) {
                    connection.commit()
                }
            } catch (e: Exception) {
                if (isOuter) {
                    connection.rollback()
                }
                throw e
            } finally {
                if (isOuter) {
                    connection.autoCommit = true
                }
                transactionDepth.set(currentDepth)
            }
        }
    }

    override fun close() {
        connection.close()
        logger.debug { "Closed DB" }
    }

    private fun initialize() {
        update(Sql.ENVIRONMENT)
        update(Sql.DOCUMENT)
        update(Sql.TABS)
        update(Sql.CACHE)
        update(Sql.CERTIFICATE)
        update("""INSERT INTO environment (name, value) VALUES ('AppSettings', '{"home": "", "selectedTab": "0"}')""")
        connection.setVersion(DB_CURRENT_VERSION)
    }

    private fun migrateTo2() {
        update("""DROP TABLE document""")
        update(Sql.DOCUMENT)
        connection.setVersion(2)
    }
}

private fun Connection.getVersion(): Int {
    return createStatement().use {
        it.executeQuery("""PRAGMA user_version""").use {
            it.getInt(1)
        }
    }
}

private fun Connection.setVersion(version: Int) {
    createStatement().use {
        it.executeUpdate("""PRAGMA user_version = $version""")
    }
}
