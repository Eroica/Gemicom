package app.gemicom

import android.annotation.SuppressLint
import app.gemicom.models.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.io.FilenameUtils
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.Reader
import java.net.*
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.io.path.name
import kotlin.io.path.outputStream

/* 10 MiB */
private const val MAX_RESPONSE_SIZE = 10 * 1024 * 1024
private const val MAX_STATUS_LINE_LENGTH = 1000
private const val MAX_REQUEST_TIME = 60 * 1000

enum class GeminiStatus(val code: Int) {
    INPUT(10),
    SUCCESS(20),
    REDIRECT(30),
    TEMPORARY_FAILURE(40),
    PERMANENT_FAILURE(50),
    CLIENT_CERTIFICATE_REQUIRED(60);
}

class InvalidGeminiUri(message: String) : Exception(message)
class InvalidGeminiResponse(message: String) : Exception(message)
class NoResponseError(message: String) : Exception(message)
class TooManyRedirects(message: String) : Exception(message)
class InputRequired(val currentUri: String, val meta: String) : Exception()
class SensitiveInputRequired(val currentUri: String, val meta: String) : Exception()
class CertificateMismatchError(
    val host: String,
    val newHash: String
) : CertificateException("Certificate fingerprint mismatch for $host")
class CertificateDateError : Exception()
class RequestRefusedError(message: String) : Exception(message)

interface IGeminiClient {
    fun get(uri: String, isCheckCache: Boolean = false): String
    fun binary(uri: String): ByteArray
}

class TofuTrustManager(
    private val host: String,
    private val certificates: ICertificates
) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val certificate = chain.first()
        certificate.checkValidity()

        val hash = sha256(certificate)
        try {
            if (certificates[host].first != hash) {
                throw CertificateMismatchError(host, hash)
            }
        } catch (_: NoCertificateError) {
            certificates.add(host, hash)
        }
    }

    @SuppressLint("TrustAllX509TrustManager")
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

    private fun sha256(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(cert.encoded)
        return hash.joinToString("") { "%02x".format(it) }
    }
}

class GeminiClient(private val certificates: ICertificates) : IGeminiClient {
    private var redirectCount = 0

    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "Initializing new GeminiClient" }
    }

    override fun get(uri: String, isCheckCache: Boolean): String = withSocket(uri) { socket, input ->
        logger.info { "GET: $uri, isCheckCache: $isCheckCache" }
        val headerLine = readHeaderLine(input)
        val (status, meta) = parseHeader(headerLine)
        val reader = input.bufferedReader(Charsets.UTF_8)

        when (status) {
            10 -> throw InputRequired(uri, meta)
            11 -> throw SensitiveInputRequired(uri, meta)
            in 12..19 -> throw InputRequired(uri, meta)
            in 30..39 -> redirectTo(meta)
            53 -> throw RequestRefusedError(meta)
            !in 20..29 -> {
                logger.debug { "Server responded with non-success: $status" }
                throw InvalidGeminiResponse("Server responded with non-success: $status")
            }

            else -> readLimitedText(reader)
        }
    }

    override fun binary(uri: String) = withSocket(uri) { socket, input ->
        val headerLine = readHeaderLine(input)
        val (status, meta) = parseHeader(headerLine)

        if (status !in 20..29) {
            logger.debug { "Server responded with non-success: $status" }
            throw InvalidGeminiResponse("Cannot download: server responded with status $status ($meta)")
        }

        readLimitedBytes(input)
    }

    private fun <T> withSocket(address: String, block: (SSLSocket, InputStream) -> T): T {
        val maxAttempts = 3
        var attempt = 0
        var lastException: Exception? = null

        val uri = try { URI(address) } catch (_: URISyntaxException) {
            throw InvalidGeminiUri("Illegal URI: $address")
        }
        val host = uri.host ?: throw InvalidGeminiUri("No host in URI: $address")
        val port = if (uri.port == -1) 1965 else uri.port

        validateRequest(address)

        /**
         * @since 2025-06-09
         * Weird error on an Android emulator (version 15, non-Google image) when trying to read the response,
         * ironically happens at gemini://geminiprotocol.net/docs/faq.gmi:
         * error:1e000065:Cipher functions:OPENSSL_internal:BAD_DECRYPT (external/boringssl/src/crypto/cipher_extra/e_chacha20poly1305.c:259 0x750ae470ed4b:0x00000000)
         * error:1000008b:SSL routines:OPENSSL_internal:DECRYPTION_FAILED_OR_BAD_RECORD_MAC (external/boringssl/src/ssl/tls_record.cc:294 0x750ae470ed4b:0x00000000)
         *
         * geminiprotocol.net uses TLS_CHACHA20_POLY1305_SHA256 (renamed in later BoringSSL versions!?) while e.g. mine
         * uses TLS_AES_128_GCM_SHA256. So far only experienced this on an emulator, happens about 80% of the time.
         * Interestingly, another connection right after the first one usually works. Add a simple retry for now ...
         */
        while (attempt < maxAttempts) {
            try {
                val context = SSLContext.getInstance("TLS")
                context.init(null, arrayOf(TofuTrustManager(host, certificates)), null)

                (context.socketFactory.createSocket(host, port) as SSLSocket).use { socket ->
                    socket.soTimeout = MAX_REQUEST_TIME
                    socket.startHandshake()
                    socket.outputStream.bufferedWriter(Charsets.UTF_8).apply {
                        write(address)
                        write("\r\n")
                        flush()
                    }

                    return block(socket, socket.inputStream.buffered())
                }
            } catch (e: SSLHandshakeException) {
                if (e.cause is CertificateMismatchError) {
                    throw e.cause as CertificateMismatchError
                } else {
                    throw e
                }
            } catch (_: CertificateExpiredException) {
                throw CertificateDateError()
            } catch (_: CertificateNotYetValidException) {
                throw CertificateDateError()
            } catch (e: SSLException) {
                logger.warn(e) { "TLS error on attempt ${attempt + 1}. Retrying ..." }
                lastException = e
                attempt++
            } catch (_: SocketTimeoutException) {
                logger.debug { "Request timed out: $address" }
                throw NoResponseError("Request timed out: $address")
            } catch (_: UnknownHostException) {
                throw InvalidGeminiUri("Illegal URI: $address (unknown host)")
            } catch (_: ConnectException) {
                throw NoResponseError("Server did not accept connection: $address")
            } catch (_: NullPointerException) {
                throw InvalidGeminiUri("Illegal URI: $address (missing scheme?)")
            }
        }

        logger.error(lastException) { "All TLS retries failed for $address" }
        throw lastException ?: InvalidGeminiResponse("Unknown TLS failure after retries")
    }

    /* First response line is always considered UTF-8 */
    private fun readHeaderLine(input: InputStream): String {
        val buffer = ByteArrayOutputStream()
        var foundCR = false

        while (true) {
            val byte = input.read()
            if (byte == -1) {
                throw InvalidGeminiResponse("Unexpected end of stream in header")
            }

            buffer.write(byte)

            if (buffer.size() > MAX_STATUS_LINE_LENGTH) {
                throw InvalidGeminiResponse("Header exceeds max. size limit")
            }

            if (foundCR && byte == '\n'.code) {
                val headerBytes = buffer.toByteArray().dropLast(2).toByteArray()
                return headerBytes.toString(Charsets.UTF_8)
            }

            foundCR = (byte == '\r'.code)
        }
    }

    private fun parseHeader(headerLine: String): Pair<Int, String> {
        val status = headerLine.substringBefore(" ").toIntOrNull()
            ?: throw InvalidGeminiResponse("Invalid Gemini status line: $headerLine")
        val meta = headerLine.substringAfter(" ", "")
        return status to meta
    }

    private fun validateRequest(url: String) {
        val urlBytes = url.toByteArray(Charsets.UTF_8)

        if (urlBytes.size >= 3 &&
            urlBytes[0] == 0xEF.toByte() &&
            urlBytes[1] == 0xBB.toByte() &&
            urlBytes[2] == 0xBF.toByte()
        ) {
            throw InvalidGeminiUri("URI must not begin with a UTF-8 BOM (U+FEFF)")
        }

        if (urlBytes.size > 1024) {
            throw InvalidGeminiUri("URI exceeds 1024-byte limit when UTF-8 encoded")
        }
    }

    private fun redirectTo(url: String): String {
        logger.info { "REDIRECT: $url, count: $redirectCount" }

        try {
            if (redirectCount++ > 5) {
                throw TooManyRedirects("Tried too many redirects: $url")
            }

            return get(url)
        } finally {
            redirectCount = 0
        }
    }
}

class CachableGeminiClient(
    private val cache: SqliteCache,
    private val documents: IDocuments,
    private val client: IGeminiClient
) : IGeminiClient by client, AutoCloseable {
    private val logger = KotlinLogging.logger {}

    override fun close() {
        cache.close()
    }

    override fun get(uri: String, isCheckCache: Boolean): String {
        return if (isCheckCache) {
            try {
                documents[uri].content.also {
                    logger.info { "GET $uri, found in cache!" }
                }
            } catch (_: NoDocument) {
                logger.info { "$uri not in cache! Getting ..." }
                client.get(uri).also { store(uri, it) }
            }
        } else {
            logger.info { "GET: $uri, skipping cache" }
            client.get(uri).also { store(uri, it) }
        }
    }

    fun download(url: String): Path {
        val bytes = client.binary(url)
        val extension = FilenameUtils.getExtension(url)
        val filePath = cache.cacheDir.resolve("${System.currentTimeMillis()}.$extension")

        filePath.outputStream().use { output -> bytes.inputStream().copyTo(output) }
        cache.add(filePath.name, Paths.get(url).fileName.toString())

        return filePath
    }

    private fun store(url: String, text: String) {
        documents.new(url, text)
    }
}

private fun readLimitedText(reader: Reader): String {
    val builder = StringBuilder()
    val buffer = CharArray(8192)
    var total = 0

    while (true) {
        val read = reader.read(buffer)
        if (read == -1) {
            break
        }
        total += read
        if (total > MAX_RESPONSE_SIZE) {
            throw InvalidGeminiResponse("Response too large (limit: $MAX_RESPONSE_SIZE bytes)")
        }
        builder.appendRange(buffer, 0, read)
    }

    return builder.toString()
}

private fun readLimitedBytes(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0

    while (true) {
        val read = input.read(buffer)
        if (read == -1) {
            break
        }
        total += read
        if (total > MAX_RESPONSE_SIZE) {
            throw InvalidGeminiResponse("Binary response too large (limit: $MAX_RESPONSE_SIZE bytes)")
        }
        output.write(buffer, 0, read)
    }

    return output.toByteArray()
}
