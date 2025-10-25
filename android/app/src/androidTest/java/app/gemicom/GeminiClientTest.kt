package app.gemicom

import app.gemicom.models.SqlCertificates
import org.junit.AfterClass
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class GeminiClientTest {
    companion object {
        private lateinit var db: IDb
        private lateinit var client: GeminiClient

        @BeforeClass
        @JvmStatic
        fun setUp() {
            db = Db.memory()
            client = GeminiClient(SqlCertificates(db))
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            db.close()
        }
    }

    @Before
    fun reset() {
        db.update("""DELETE FROM certificate""")
    }

    /**
     * @since 2025-06-09
     * Added for debugging gemini://geminiprotocol.net/docs/faq.gmi, throws SSLException on AVD.
     */
    @Test
    fun getPage() {
        val r = client.get("gemini://geminiprotocol.net/docs/faq.gmi")
        assertNotEquals("", r)
    }
}
