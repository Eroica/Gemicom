package app.gemicom.models

import app.gemicom.Db
import app.gemicom.IDb
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.sqlite.SQLiteException

internal class CertificatesTest {
    companion object {
        private lateinit var db: IDb
        private lateinit var certificates: ICertificates

        @BeforeClass
        @JvmStatic
        fun setUp() {
            db = Db.memory()
            certificates = SqlCertificates(db)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            db.close()
        }
    }

    @After
    fun reset() {
        db.update("""DELETE FROM certificate""")
    }

    @Test
    fun `Create new certificate`() {
        val host = "dummy"
        val hash = "dummy cert"
        certificates.add(host, hash)

        db.query("""SELECT * FROM certificate WHERE host='dummy'""") {
            it.next()
            assertEquals(host, it.getString(1))
            assertEquals(hash, it.getString(2))
        }
    }

    @Test
    fun `Get certificate`() {
        val host = "dummy"
        val hash = "dummy cert"
        certificates.add(host, hash)
        assertEquals(hash, certificates[host].first)
    }

    @Test(expected = SQLiteException::class)
    fun `Create duplicate host throws`() {
        val host = "dummy"
        val hash = "dummy cert"
        certificates.add(host, hash)
        certificates.add(host, "cert2")
        assertEquals(hash, certificates[host].first)
    }

    @Test
    fun `Replace existing hash`() {
        val host = "dummy"
        val hash = "dummy cert"
        certificates.add(host, hash)
        assertEquals(hash, certificates[host].first)

        certificates.replace(host, "new cert")
        assertEquals("new cert", certificates[host].first)
    }
}
