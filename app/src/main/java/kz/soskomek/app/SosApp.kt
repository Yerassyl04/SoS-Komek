package kz.soskomek.app

import android.app.Application
import android.content.Context
import kz.soskomek.app.data.AppContainer
import kz.soskomek.app.sos.Notifications
import kz.soskomek.app.sos.SyncWorker
import org.osmdroid.config.Configuration
import java.io.File

class SosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.settings.applyTheme()
        Notifications.createChannel(this)
        Configuration.getInstance().apply {
            load(this@SosApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        if (container.hasPendingSync()) SyncWorker.enqueue(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as SosApp).container
