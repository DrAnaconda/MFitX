package anonymouls.dev.mgcex.app.backend

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import anonymouls.dev.mgcex.app.backend.ApplicationStarter.Companion.commandHandler
import anonymouls.dev.mgcex.databaseProvider.DatabaseController
import anonymouls.dev.mgcex.databaseProvider.NotifyFilterTable
import anonymouls.dev.mgcex.util.PreferenceListener
import anonymouls.dev.mgcex.util.ReplaceTable
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap


@ExperimentalStdlibApi
class NotificationService : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        Companion.contentResolver = contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.startForeground(66, Utils.buildForegroundNotification(this))
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //Algorithm.tryForceStartListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.startForeground(66, Utils.buildForegroundNotification(this))
        }
        return START_STICKY
    }

    override fun onListenerConnected() {
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.startForeground(66, Utils.buildForegroundNotification(this))
        }
        Utils.getSharedPrefs(this@NotificationService).edit().putBoolean("bindNotifyService", true).apply()
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        instance = null
        Utils.getSharedPrefs(this@NotificationService).edit().putBoolean("bindNotifyService", false).apply()
        this.stopForeground(true)
        this.stopSelf()
        super.onListenerDisconnected()
    }

    private fun proceedNotify(sbn: StatusBarNotification) {
        if (!Algorithm.IsActive
                || Settings.Global.getInt(contentResolver, "zen_mode") > 0) return
        val pack = sbn.packageName
        if (!NotifyFilterTable.isEnabled(pack,
                        DatabaseController.getDCObject(applicationContext).readableDatabase))
            return
        val extras = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            sbn.notification.extras
        } else {
            this.stopForeground(true)
            this.stopSelf()
            null
        }) ?: return
        val title = extras.getString("android.title")
        var applicationName = ""
        try {
            applicationName = this.packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (ex: Exception) {
        }

        var text = "-----"
        try {
            text = Objects.requireNonNull(extras.getCharSequence("android.text")).toString()
        } catch (ex: NullPointerException) {

        }
        val cNotify = if (title != null) {
            CustomNotification(pack, title, text, sbn)
        } else
            CustomNotification(pack, applicationName, text, sbn)
        addNotifyToQuene(cNotify); cNotify.enroll()
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT) // TODO ???
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        instance = this
        GlobalScope.launch(Dispatchers.IO) { proceedNotify(sbn) }
    }

    private fun addNotifyToQuene(notify: CustomNotification) {
        if (PendingList.contains(notify.AppText))
            PendingList[notify.AppText] = notify
        else
            PendingList[notify.AppText] = notify
    }

    override fun onBind(intent: Intent): IBinder? {
        instance = this
        return super.onBind(intent)
    }

    inner class CustomNotification(val AppText: String,
                                   val TitleText: String,
                                   val ContentText: String,
                                   private val sbn: StatusBarNotification) {

        var ID: Int = -1
        var repeats: Int = 1


        init {
            this.ID = sbn.id
        }

        private fun checkIsActive(): Boolean {
            val aN = activeNotifications
            for (notify in aN) {
                if (notify.id == this.ID) {
                    return true
                }
            }
            return false
        }
        private fun sendToDevice() {
            if (!checkIsActive()
                    || repeats > PreferenceListener.repeatNotifications) return
            if (Algorithm.StatusCode.value!!.code >= Algorithm.StatusCodes.GattReady.code) {
                //val app = extras!!.get("app") as String // TODO integrations
                val message = TitleText + "\n" + ContentText
                Algorithm.SelfPointer?.ci?.fireNotification(ReplaceTable.replaceString(message))
                repeats++
                enroll()
            }
        }
        fun enroll(){
            Handler(commandHandler.looper).postDelayed({sendToDevice()},
                PreferenceListener.repeatDelayNotification.toLong()*1000)
        }
    }


    companion object {

        var instance: NotificationService? = null
        var PendingList: ConcurrentHashMap<String, CustomNotification> = ConcurrentHashMap()
        var sharedPrefs: SharedPreferences? = null
        var contentResolver: ContentResolver? = null
    }
}