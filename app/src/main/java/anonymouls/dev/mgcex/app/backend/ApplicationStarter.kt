package anonymouls.dev.mgcex.app.backend

import android.app.Application
import android.content.Context
import android.os.HandlerThread

@ExperimentalStdlibApi
class ApplicationStarter : Application() {
    companion object {
        lateinit var appContext: Context
        val commandHandler: HandlerThread = HandlerThread("CommandsSender")
    }

    override fun onCreate() {
        try {
            appContext = applicationContext
        } catch (e: Throwable) {
        }
        super.onCreate()
        if (appContext == null) appContext = applicationContext
        MultitaskListener.ressurectService(appContext)
    }
}