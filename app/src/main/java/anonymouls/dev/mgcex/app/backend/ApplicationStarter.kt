package anonymouls.dev.mgcex.app.backend

import android.app.Application
import android.content.Context
import android.os.HandlerThread
import android.os.StrictMode
import anonymouls.dev.mgcex.app.BuildConfig
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@ExperimentalStdlibApi
class ApplicationStarter : Application() {
    companion object {
        lateinit var appContext: Context
        val commandHandler: HandlerThread = HandlerThread("AACommandsSender")
    }

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog().penaltyDialog()
                    .build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build())
        }
        try {
            appContext = applicationContext
        } catch (e: Throwable) {
        }
        super.onCreate()
        if (appContext == null) appContext = applicationContext
        MultitaskListener.ressurectService(appContext)
        GlobalScope.launch(Dispatchers.Default) {
            Utils.getSharedPrefs(applicationContext)
            MultitaskListener.ressurectService(appContext)
        }
    }
}