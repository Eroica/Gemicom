package app.gemicom.views.models

import app.gemicom.Db
import app.gemicom.IDb
import app.gemicom.TESTS_APP_DIR
import app.gemicom.models.SqlCertificates
import app.gemicom.models.SqlDocuments
import app.gemicom.models.SqlTabs
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.conf.global
import org.kodein.di.instance

private fun testAppModule(db: IDb) = DI.Module("App") {
    bindSingleton(tag = "CACHE_DIR") { TESTS_APP_DIR }
    bindSingleton { db }
    bindSingleton { SqlCertificates(instance()) }
}

internal class ScopedTabTest {
    companion object {
        private lateinit var db: IDb
        private lateinit var tabs: SqlTabs

        @BeforeClass
        @JvmStatic
        fun setUp() {
            System.loadLibrary("gemicom")
            db = Db.memory()
            db.update("""DELETE FROM tab""")
            DI.global.addImport(testAppModule(db))
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
    fun `Test initial navigate`() = runTest {
        val scopedTab = ScopedTab(tabs.new())
        scopedTab.navigate("gemicom.app/")
        assertEquals("gemini://gemicom.app/", scopedTab.currentLocation)
    }
}
