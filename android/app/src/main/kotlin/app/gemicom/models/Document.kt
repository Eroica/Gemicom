package app.gemicom.models

import app.gemicom.IDb
import app.gemicom.Sql
import app.gemicom.lib.Gemini
import app.gemicom.toDatabaseString
import java.time.LocalDateTime

interface IDocument {
    val url: String
    val content: String
}

interface IDocuments {
    val tabId: Long

    operator fun get(url: String): IDocument
    operator fun contains(url: String): Boolean

    fun new(url: String, content: String): IDocument
}

class NoDocument : Exception()

class InvalidDocument : Exception()

data class Document(
    override val url: String = "",
    override val content: String
) : IDocument

class SqlDocuments(override val tabId: Long, private val db: IDb) : IDocuments {
    companion object {
        fun purge(olderThan: LocalDateTime?, db: IDb) {
            db.update(Sql.Document_DeleteOld) {
                it.setString(
                    1,
                    olderThan?.toDatabaseString() ?: LocalDateTime.now().toDatabaseString()
                )
            }
        }
    }

    override fun get(url: String): IDocument {
        return db.query(Sql.Document_Get, {
            it.setLong(1, tabId)
            it.setString(2, url)
        }) {
            if (it.next()) {
                Document(url, it.getString(1))
            } else {
                throw NoDocument()
            }
        }
    }

    override fun contains(url: String): Boolean {
        return db.query(Sql.Document_Has, {
            it.setLong(1, tabId)
            it.setString(2, url)
        }) {
            it.getLong(1) > 0
        }
    }

    override fun new(url: String, content: String): IDocument {
        db.query(Sql.Document_Create, {
            it.setLong(1, tabId)
            it.setString(2, url)
            it.setString(3, content)
        }) {
            it.getLong(1)
        }

        return Document(url, content)
    }
}

interface IGeminiDocument {
    val blocks: List<IGemtext>
}

class GeminiDocument(val tokens: List<IGemtext>) : IGeminiDocument {
    constructor(document: IDocument) : this(
        Gemini.parse(document.content).map { parseGemtext(it.type, it.value) }
    )

    override val blocks: List<IGemtext>
        get() = tokens
}

/**
 * Merges Anchors, ListItem, and Preformat tokens.
 * Filters Newline tokens.
 */
class ChunkedGeminiDocument(override val blocks: List<IGemtext>) : IGeminiDocument {
    companion object {
        fun fromText(url: String = "", text: String): ChunkedGeminiDocument {
            return ChunkedGeminiDocument(
                GeminiDocument(
                    Document(url, text)
                )
            )
        }
    }

    constructor(document: GeminiDocument) : this(mergeTokens(document.tokens).filter { it !is Newline })
}

data object EmptyGeminiDocument : IGeminiDocument {
    override val blocks = listOf(EmptyPageBlock)
}

data object InvalidGeminiDocument : IGeminiDocument {
    override val blocks = listOf(InvalidDocumentBlock)
}

data object SecurityIssueGeminiDocument : IGeminiDocument {
    override val blocks = listOf(SecurityIssueBlock)
}

data object CertificateInvalidDocument : IGeminiDocument {
    override val blocks = listOf(CertificateInvalidBlock)
}

private fun mergeTokens(tokens: List<IGemtext>): List<IGemtext> {
    val result = mutableListOf<IGemtext>()
    val buffer = mutableListOf<IGemtext>()
    var blockToken: IGemtext? = null

    for (token in tokens) {
        if (blockToken != null && token::class == blockToken::class) {
            buffer.add(token)
        } else {
            when (blockToken) {
                is Preformat -> result.add(
                    PreformatBlock(
                        buffer.filterIsInstance<Preformat>()
                            .toList()
                            .dropLast(1)
                    )
                )

                is ListItem -> result.add(ListItemBlock(buffer.filterIsInstance<ListItem>().toList()))
                is Anchor -> result.add(AnchorBlock(buffer.filterIsInstance<Anchor>().toList()))
                else -> result.addAll(buffer)
            }

            buffer.clear()
            buffer.add(token)
            blockToken = token
        }
    }

    if (buffer.isNotEmpty()) {
        when (blockToken) {
            is Preformat -> result.add(PreformatBlock(buffer.filterIsInstance<Preformat>().toList()))
            is ListItem -> result.add(ListItemBlock(buffer.filterIsInstance<ListItem>().toList()))
            is Anchor -> result.add(AnchorBlock(buffer.filterIsInstance<Anchor>().toList()))
            else -> result.addAll(buffer)
        }
    }

    return result
}
