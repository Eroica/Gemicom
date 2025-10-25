package app.gemicom.models

import app.gemicom.Db
import app.gemicom.IDb
import app.gemicom.TESTS_APP_DIR
import app.gemicom.toDatabaseString
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertNotEquals
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDateTime

internal class DocumentTest {
    companion object {
        private lateinit var db: IDb
        private lateinit var documents: IDocuments

        @BeforeClass
        @JvmStatic
        fun setUp() {
            db = Db.memory()
            documents = SqlDocuments(db)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            db.close()
        }
    }

    @After
    fun reset() {
        db.update("""DELETE FROM document""")
    }

    val gemtext = """# Hello, Gemicom

Text text

```preformat start

=> ignore link
> ignore
```

After text"""

    @Test
    fun `Create new document`() {
        documents.new("localhost/test", gemtext)
        val count = db.query("""SELECT COUNT(*) FROM document""") { it.getLong(1) }
        assertEquals(1, count)
    }

    @Test
    fun `Create same document under same URL`() {
        documents.new("doc1", "# Hello, Capsule")
        db.update("""UPDATE document SET created_at='ignore' WHERE url='doc1'""")

        documents.new("doc1", "# Hello, Sonde")
        db.query("""SELECT content, created_at FROM document WHERE url='doc1'""", {}) {
            it.next()
            assertEquals("# Hello, Sonde", it.getString(1))
            assertNotEquals("ignore", it.getString(2))
        }
    }

    @Test
    fun `Get document`() {
        documents.new("localhost/test", gemtext)
        val document = documents["localhost/test"]
        assertEquals(gemtext, document.content)
    }

    @Test
    fun `Purge old documents`() {
        val document1 = """Document 1"""
        val document2 = """Document 2"""
        val documentNew = """Document New"""

        documents.new("document1", document1)
        documents.new("document2", document2)
        documents.new("documentNew", documentNew)

        db.update("""UPDATE document SET created_at=? WHERE url=?""") {
            it.setString(1, LocalDateTime.now().plusDays(10).toDatabaseString())
            it.setString(2, "documentNew")
        }

        SqlDocuments.purge(LocalDateTime.now().plusDays(1), db)

        val remainingCount = db.query("""SELECT COUNT(*) FROM document""", {}) {
            it.next()
            it.getLong(1)
        }
        assertEquals(1, remainingCount)
    }
}
