package app.gemicom

import android.app.Application
import app.gemicom.models.*
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kodein.di.*
import org.kodein.di.conf.DIGlobalAware
import org.kodein.di.conf.global
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.createDirectories

fun appModule(databaseDir: Path, mediaDir: Path, cacheDir: Path) = DI.Module(name = "App") {
    bindSingleton(tag = "MEDIA_DIR") { mediaDir }
    bindSingleton(tag = "CACHE_DIR") { cacheDir }
    bind<CoroutineDispatcher>() with singleton { Dispatchers.IO }
    bind<CoroutineDispatcher>(tag = "WRITER") with singleton {
        Dispatchers.IO.limitedParallelism(1)
    }
    bindSingleton { DefaultContext() }

    bindSingleton { Db.at(databaseDir) }
    bindSingleton { SqlDocuments(instance()) }
    bindSingleton { SqlTabs(instance()) }
    bindSingleton { SqlCertificates(instance()) }
    bindSingleton { AppSettings(SqlPreferences("AppSettings", instance())) }
}

class App : Application(), DIGlobalAware {
    private val Db: IDb by instance()
    private val Documents: IDocuments by instance()
    private val AppSettings: AppSettings by instance()
    private val DefaultContext: IContext by instance()
    private val Writer: CoroutineDispatcher by instance(tag = "WRITER")

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("gemicom")
        val databaseDir = applicationContext.getDatabasePath(DB_NAME).toPath().parent
        val cacheDir = applicationContext.cacheDir.toPath()
        val mediaRoot = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
        val mediaDir = mediaRoot.resolve(MEDIA_NAME).toPath()
        mediaDir.createDirectories()
        DI.global.addImport(appModule(databaseDir, mediaDir, cacheDir))

        DefaultContext.co.launch(Writer) {
            if (AppSettings.isDebug) {
                val root = LoggerFactory.getLogger("ROOT") as Logger
                root.level = Level.DEBUG
            }

            /* Maintenance on startup */
            Documents.clear(LocalDateTime.now().minusMonths(1))
            SqliteCache.purge(cacheDir, Db)
        }
    }
}
