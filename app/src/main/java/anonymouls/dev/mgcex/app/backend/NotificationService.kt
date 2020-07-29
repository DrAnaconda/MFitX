package anonymouls.dev.mgcex.app.backend

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
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
        if (!IsActive) {
            IsActive = true
            Repeater = AsyncRepeater()
            Repeater!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.startForeground(66, Utils.buildForegroundNotification(this))
        }
        Utils.getSharedPrefs(this@NotificationService).edit().putBoolean("bindNotifyService", true).apply()
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        IsActive = false
        instance = null
        Repeater?.cancel(false)
        Repeater = null
        Utils.getSharedPrefs(this@NotificationService).edit().putBoolean("bindNotifyService", false).apply()
        this.stopForeground(true)
        this.stopSelf()
        super.onListenerDisconnected()
    }

    private fun proceedNotify(sbn: StatusBarNotification) {
        if (!Algorithm.IsActive || Settings.Global.getInt(contentResolver, "zen_mode") > 0) return
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
        if (title != null) {
            addNotifyToQuene(CustomNotification(pack, title, text, sbn))
        } else
            addNotifyToQuene(CustomNotification(pack, applicationName, text, sbn))
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        findAndDeleteByID(sbn?.id)
        super.onNotificationRemoved(sbn)
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
        var repeats: Int = 0
        var isLocked: Boolean = false
        var ready: Boolean = false
        var cancelled: Boolean = false


        init {
            this.ID = sbn.id
            if (commandHandler != null)
                Handler(commandHandler.looper).postDelayed({ this.ready = true },
                        sharedPrefs!!.getString(PreferenceListener.Companion.PrefsConsts.secondsNotify, "5")!!.toLong() * 500)
        }

        private fun checkIsActive(): Boolean {
            val aN = activeNotifications
            for (notify in aN) {
                if (notify.id == this.ID) {
                    cancelled = true; return true
                }
            }
            return false
        }

        fun sendToDevice() {
            if (!isLocked) {
                isLocked = true
                return
            } else {
                if (!ready || !checkIsActive()) return
                ready = false
                if (Algorithm.StatusCode.value!!.code >= Algorithm.StatusCodes.GattReady.code) {
                    //val app = extras!!.get("app") as String // TODO integrations
                    val message = TitleText + "\n" + ContentText
                    Algorithm.SelfPointer?.ci?.fireNotification(ReplaceTable.replaceString(message))
                    repeats++
                }
            }
        }
    }


    companion object {

        var Repeater: AsyncRepeater? = null
        var instance: NotificationService? = null
        var IsActive: Boolean = false
        var PendingList: ConcurrentHashMap<String, CustomNotification> = ConcurrentHashMap()
        var sharedPrefs: SharedPreferences? = null
        var contentResolver: ContentResolver? = null

        fun findAndDeleteByID(ID: Int?) {
            for (CN in PendingList.elements()) {
                if (CN.ID == ID
                        || CN.repeats >= sharedPrefs!!.getString(PreferenceListener.Companion.PrefsConsts.repeatsNumbers, "5")!!.toInt()
                        || CN.cancelled)
                    PendingList.remove(CN.AppText)
            }
        }

        class AsyncRepeater : AsyncTask<String, Void, Boolean>() { // TODO: This deprecated. Need to use timer or looper
            override fun doInBackground(vararg params: String?): Boolean {
                if (sharedPrefs == null)
                    sharedPrefs = Utils.getSharedPrefs(instance!!)
                Thread.currentThread().name = "NotifyRepeaters"
                Thread.currentThread().priority = Thread.MAX_PRIORITY
                while (IsActive) {
                    if (Settings.Global.getInt(contentResolver, "zen_mode") == 0) {
                        for (CN in PendingList.elements()) {
                            CN.sendToDevice()
                            if (CN.repeats >= sharedPrefs!!.getString(PreferenceListener.Companion.PrefsConsts.repeatsNumbers, "3")!!.toInt())
                                PendingList.remove(CN.AppText)
                            Utils.safeThreadSleep(3000, false)
                        }
                    } else PendingList.clear()
                    Utils.safeThreadSleep((sharedPrefs!!.getString(PreferenceListener.Companion.PrefsConsts.secondsNotify, "5")!!.toInt() * 1000).toLong(), false)
                }
                return true
            }
        }
    }
}