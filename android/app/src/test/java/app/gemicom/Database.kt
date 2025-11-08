package app.gemicom

import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class DatabaseTest {
    companion object {
        private lateinit var db: IDb

        @BeforeClass
        @JvmStatic
        fun setUp() {
            db = Db.memory()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            db.close()
        }
    }

    @After
    fun reset() {
        db.update("""DELETE FROM environment""")
    }

    @Test
    fun `Parallel reads`() {
        val pool = Executors.newFixedThreadPool(2)
        val returns = arrayOf<String?>(null, null)
        db.update("""INSERT INTO environment (name, value) values ('Foo', 'Bar')""")
        db.update("""INSERT INTO environment (name, value) values ('Hello', 'World')""")

        pool.submit {
            returns[0] = db.query("""SELECT value FROM environment WHERE name='Foo'""") {
                it.getString(1)
            }
        }

        Thread.sleep(100)

        pool.submit {
            returns[1] = db.query("""SELECT value FROM environment WHERE name='Hello'""") {
                it.getString(1)
            }
        }

        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)
        assertArrayEquals(returns, arrayOf("Bar", "World"))
    }

    @Test
    fun `Writer blocks everything else`() {
        val pool = Executors.newFixedThreadPool(2)
        val returns = arrayOf<String?>(null)

        pool.submit {
            db.transaction {
                db.update("""INSERT INTO environment (name, value) values ('Foo', 'Bar')""")
                Thread.sleep(2000)
            }
        }

        Thread.sleep(100)

        pool.submit {
            returns[0] = db.query("""SELECT value FROM environment WHERE name='Foo'""") {
                it.getString(1)
            }
        }

        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)
        assertArrayEquals(returns, arrayOf("Bar"))
    }

    @Test
    fun `Nested transaction`() {
        try {
            db.transaction {
                db.update("""INSERT INTO environment (name, value) values ('Foo', 'Bar')""")

                db.transaction {
                    db.update("""INSERT INTO environment (name, value) values ('Hello', 'World')""")
                    throw Exception()
                }
            }
        } catch (_: Exception) {
        } finally {
            assert(db.query("""SELECT value FROM environment WHERE name='Foo'""") { it.getString(1) } == null)
            assert(db.query("""SELECT value FROM environment WHERE name='Hello'""") { it.getString(1) } == null)
        }
    }
}
