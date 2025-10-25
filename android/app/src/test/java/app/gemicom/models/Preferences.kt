package app.gemicom.models

import app.gemicom.Db
import app.gemicom.IDb
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

internal class PreferencesTest {
    companion object {
        private lateinit var db: IDb
        private lateinit var preferences: IPreferences

        @BeforeClass
        @JvmStatic
        fun setUp() {
            db = Db.memory()
            preferences = SqlPreferences("Tests", db)
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
    fun `Create new preferences`() {
        preferences["key"] = "value"
        db.query("""SELECT COUNT(*), value FROM environment WHERE name='Tests'""") {
            assertEquals(1, it.getInt(1))
            assertEquals("""{"key":"value"}""", it.getString(2))
        }
    }

    @Test
    fun `Get missing preference`() {
        /* Whole preference object does not exist yet */
        assertEquals(null, preferences["missing"])

        /* Exists, but missing key */
        preferences["key"] = "value"
        assertEquals(null, preferences["missing"])
    }

    @Test
    fun `Get existing preference`() {
        preferences["key"] = "value"
        assertEquals("value", preferences["key"])
    }

    @Test
    fun `Update preference`() {
        preferences["key"] = "value"
        assertEquals("value", preferences["key"])

        preferences["key"] = "updated"
        assertEquals("updated", preferences["key"])
    }
}
