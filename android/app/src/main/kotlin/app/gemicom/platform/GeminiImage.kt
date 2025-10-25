package app.gemicom.platform

import app.gemicom.GeminiClient
import app.gemicom.InvalidGeminiUri
import app.gemicom.models.SqliteCache
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import org.apache.commons.io.FilenameUtils
import java.net.URI
import kotlin.io.path.name
import kotlin.io.path.outputStream

interface IImagePool {
    fun getCache(): SqliteCache
}

/* Takes care of pointing Coil to the correct image fetcher if it encounters this type */
@JvmInline
value class GeminiUri(val uri: String) {
    companion object {
        fun fromAddress(address: String): GeminiUri {
            val uri = try {
                URI.create(address)
            } catch (_: Exception) {
                throw InvalidGeminiUri(address)
            }

            when {
                !uri.scheme.isNullOrBlank() && uri.scheme != "gemini" -> throw InvalidGeminiUri(address)
                !uri.scheme.isNullOrBlank() && uri.host.isNullOrBlank() -> throw InvalidGeminiUri(address)
                address.startsWith("//") -> throw InvalidGeminiUri(address)
            }

            return GeminiUri(address)
        }
    }
}

class GeminiImageFetcher(
    private val client: GeminiClient,
    private val cachePool: IImagePool,
    private val data: String,
    private val options: Options,
    private val imageLoader: ImageLoader
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bytes = client.binary(data)
        val extension = FilenameUtils.getExtension(data)
        val cache = cachePool.getCache()
        val filePath = cache.cacheDir.resolve("${System.currentTimeMillis()}.$extension")

        filePath.outputStream().use { output -> bytes.inputStream().copyTo(output) }
        cache.add(filePath.name, FilenameUtils.getName(data))

        val output = imageLoader.components.newFetcher(filePath.toFile(), options, imageLoader)
        val (fetcher) = checkNotNull(output) { "no supported fetcher" }
        return fetcher.fetch()
    }

    class Factory(
        private val geminiClient: GeminiClient,
        private val cachePool: IImagePool
    ) : Fetcher.Factory<GeminiUri> {
        override fun create(
            data: GeminiUri,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return GeminiImageFetcher(geminiClient, cachePool, data.uri, options, imageLoader)
        }
    }
}

class GeminiImageKeyer : Keyer<GeminiUri> {
    override fun key(
        data: GeminiUri,
        options: Options
    ): String {
        return data.uri
    }
}
