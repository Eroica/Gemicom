package app.gemicom.models

import app.gemicom.Db
import app.gemicom.IDb
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

internal class TabsTest {
    companion object {
        private lateinit var db: IDb
        private lateinit var tabs: SqlTabs

        @BeforeClass
        @JvmStatic
        fun setUp() {
            db = Db.memory()
            db.update("""DELETE FROM tab""")
            tabs = SqlTabs(db)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            db.close()
        }
    }

    @After
    fun reset() {
        db.update("""DELETE FROM tab""")
    }

    @Test
    fun `Create Tab`() {
        tabs.new()
        db.query("""SELECT COUNT(*) FROM tab""") {
            it.next()
            assertEquals(1, it.getInt(1))
        }
    }

    @Test
    fun `Test initial navigate`() {
        val tab = tabs.new()
        assertEquals("gemini://gemicom.app/", tab.start("gemini://gemicom.app"))
    }

    @Test
    fun `Test creating history`() {
        val tab = tabs.new()
        tab.start("gemini://gemicom.app")

        tab.navigate("/example")
        tab.navigate("image")
        tab.navigate("/image/nested")

        assertEquals(4, tab.history.size)
        assertEquals(
            listOf(
                "gemini://gemicom.app/",
                "gemini://gemicom.app/example",
                "gemini://gemicom.app/image",
                "gemini://gemicom.app/image/nested"
            ), tab.history
        )
    }

    @Test
    fun `Test create history once`() {
        val tab = tabs.new()
        tab.start("gemicom.app")
        tab.navigate(tab.resolve("/example2"))
        assertEquals(2, tab.history.size)
    }

    @Test(expected = NoNextEntry::class)
    fun `Test tab peeking at end`() {
        val tab = tabs.new()
        tab.navigate("gemini://gemicom.app")
        tab.navigate("/example")
        tab.peekNext()
    }

    @Test(expected = NoMoreHistory::class)
    fun `Test tab peeking at beginning`() {
        val tab = tabs.new()
        tab.peekPrevious()
    }

    @Test
    fun `Test tab peeking`() {
        val tab = tabs.new()
        tab.navigate("gemini://gemicom.app")
        tab.navigate("/example")
        tab.navigate("image")
        tab.navigate("/image/nested")

        assertEquals("gemini://gemicom.app/image", tab.peekPrevious())

        tab.back()

        assertEquals("gemini://gemicom.app/example", tab.peekPrevious())
        assertEquals("gemini://gemicom.app/image/nested", tab.peekNext())
    }

    @Test
    fun `Test moving back and navigating`() {
        val tab = tabs.new()
        tab.navigate("gemini://gemicom.app")
        tab.navigate("/example")
        tab.navigate("image")
        tab.navigate("/image/nested")
        tab.back()
        tab.navigate("/new")

        assertEquals(4, tab.history.size)
        assertEquals(
            listOf(
                "gemini://gemicom.app/",
                "gemini://gemicom.app/example",
                "gemini://gemicom.app/image",
                "gemini://gemicom.app/new"
            ),
            tab.history
        )
        assertEquals("gemini://gemicom.app/image", tab.peekPrevious())
    }

    @Test
    fun `Test start with hostname`() {
        val tab1 = tabs.new()
        tab1.start("localhost")

        assertEquals("gemini://localhost/", tab1.currentLocation)

        val tab2 = tabs.new()
        tab2.navigate("localhost")

        assertEquals("gemini://localhost/", tab2.currentLocation)
    }

    @Test
    fun `Test navigating to same location does not push history`() {
        val tab = tabs.new()
        tab.navigate("gemini://gemicom.app")
        tab.navigate("/example", true)
        tab.navigate("/example", true)
        assertEquals(2, tab.history.size)
    }
}
