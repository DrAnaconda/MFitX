package anonymouls.dev.mgcex.util

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.main.DeviceControllerActivity
import anonymouls.dev.mgcex.app.main.SettingsActivity
import anonymouls.dev.mgcex.databaseProvider.SleepRecordsTable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.DecimalFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.roundToInt


@ExperimentalStdlibApi
object Utils {

    const val BluetoothEnableRequestCode = 1
    const val LocationPermissionRequest = 2
    const val PermissionsRequest = 3
    const val PermsRequest = 4
    const val PermsAdvancedRequest = 5
    lateinit var SharedPrefs: SharedPreferences

    enum class SDFPatterns(val pattern: String) {
        DayScaling("LLLL d HH:mm"), WeekScaling("LLLL W yyyy"),
        MonthScaling("d LLLL yyyy"), OverallStats("d LLL"), TimeOnly("HH:mm")
    }

    var IsStorageAccess = true
    var IsLocationAccess = true

    val UsedPerms = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE, //Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.WAKE_LOCK,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE)//, Manifest.permission.RECEIVE_BOOT_COMPLETED)

    fun getSharedPrefs(context: Context): SharedPreferences {
        if (!Utils::SharedPrefs.isInitialized) {
            SharedPrefs = context.getSharedPreferences("MainPrefs", Context.MODE_PRIVATE)
            SleepRecordsTable.GlobalSettings.ignoreLightSleepData = SharedPrefs.getBoolean(SettingsActivity.lightSleepIgnore, true)
            if (!SharedPrefs.contains(Analytics.UserID))
                SharedPrefs.edit().putString(Analytics.UserID, UUID.randomUUID().toString()).apply()
        }
        return SharedPrefs
    }

    fun requestEnableBluetooth(ContextActivity: Activity) {
        if (!ContextActivity.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                || !ContextActivity.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            return

        val requestIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        ContextActivity.startActivityForResult(requestIntent, BluetoothEnableRequestCode)
    }

    fun isDeviceSupported(context: Activity): Boolean {
        return if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            //DeviceControllerActivity.ViewDialog("Your device not supported. You must have Bluetooth adapter and BLE technology",
            //DeviceControllerActivity.ViewDialog.DialogTask.Intent, null)
            Toast.makeText(context, "Your device not supported. You must have Bluetooth adapter and BLE technology.", Toast.LENGTH_LONG).show()
            false
        } else true
    }

    fun bluetoothEngaging(AContext: Context): Boolean {
        val bManager = AContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bManager == null) {
            Toast.makeText(AContext, R.string.BluetoothIsNotSupported, Toast.LENGTH_SHORT).show()
        } else {
            if (bManager.adapter == null) {
                Toast.makeText(AContext, R.string.bt_unavailable, Toast.LENGTH_SHORT).show()
                //requestEnableBluetooth(AContext)
                return false
            } else {
                return bManager.adapter.isEnabled
            }
        }
        return false
    }

    fun requestPermissionsAdvanced(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                DeviceControllerActivity.ViewDialog(activity.getString(R.string.location_perm_req), DeviceControllerActivity.ViewDialog.DialogTask.Permission, Manifest.permission.ACCESS_COARSE_LOCATION).showDialog(activity)
                return
            }
            if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    && activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                val dialog = DeviceControllerActivity.ViewDialog(activity.getString(R.string.storage_perm_request), DeviceControllerActivity.ViewDialog.DialogTask.Permission, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                dialog.showDialog(activity)
                return
            }
            if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                    && activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
                val dialog = DeviceControllerActivity.ViewDialog(activity.getString(R.string.phonestate_perm_req), DeviceControllerActivity.ViewDialog.DialogTask.Permission, Manifest.permission.READ_PHONE_STATE)
                dialog.showDialog(activity)
                return
            }

            if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                    && activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                val dialog = DeviceControllerActivity.ViewDialog(activity.getString(R.string.contacts_perm_req), DeviceControllerActivity.ViewDialog.DialogTask.Permission, Manifest.permission.READ_CONTACTS)
                dialog.showDialog(activity)
                return
            }

            if (activity.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
                    && activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                val dialog = DeviceControllerActivity.ViewDialog(activity.getString(R.string.incoming_number_perm),
                        DeviceControllerActivity.ViewDialog.DialogTask.Permission, Manifest.permission.READ_CONTACTS)
                dialog.showDialog(activity)
                return
            }
        }
    }

    fun requestPermissionsDefault(ContextActivity: Activity, NeededPerms: Array<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val Req = ArrayList<String>()
            for (Perm in NeededPerms) {
                if (ContextActivity.checkSelfPermission(Perm) != PackageManager.PERMISSION_GRANTED)
                    Req.add(Perm)
            }
            if (Req.size > 0) {
                val array = arrayOfNulls<String>(Req.size)
                Req.toArray(array)
                ContextActivity.requestPermissions(array, PermsRequest)
            }
        }

        // TODO Request ignore
    }

    private fun requestIgnoreBatteryOptimization(context: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (!(context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                            .isIgnoringBatteryOptimizations(context.packageName)
                    && Utils.getSharedPrefs(context).getBoolean(SettingsActivity.permitWakeLock, true)) {
                val Dialog = DeviceControllerActivity.ViewDialog("Disabled power optimization recommended for reliable connection",
                        DeviceControllerActivity.ViewDialog.DialogTask.Intent, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        context, true, SettingsActivity.permitWakeLock)
                Dialog.showDialog(context)
            }

        }
    }

    fun requestToBindNotifyService(ContextActivity: Activity) {
        val reqIntent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        ContextActivity.startActivity(reqIntent)
    }

    fun bigNumberToString(number: Int, divider: Int): String {
        val newNumber: Float = number.toFloat() / divider
        val format = DecimalFormat("0.###")
        return if (newNumber < 1000) {
            format.format(newNumber)
        } else {
            if (newNumber > 1000000) {
                val millions: Float = newNumber / 1000000
                format.format(millions) + " M"
            } else {
                val thousands: Int = newNumber.toInt() / 1000
                "$thousands K"
            }
        }
    }

    fun getDeltaCalendar(A: Calendar, B: Calendar, Field: Int): Int {
        val diff: Long = A.timeInMillis - B.timeInMillis
        return when (Field) {
            Calendar.SECOND -> (diff.toDouble() / 1000).roundToInt()
            Calendar.MINUTE -> (diff.toDouble() / 1000 / 60).roundToInt() + 1
            Calendar.HOUR_OF_DAY, Calendar.HOUR -> (diff.toDouble() / 1000 / 60 / 60).roundToInt()
            else -> (diff.toDouble() / 1000 / 60 / 60 / 24).roundToInt()
        }
    }

    fun subIntegerConversionCheck(CheckIn: String): String {
        return if (CheckIn.length % 2 != 0) {
            "0" + CheckIn.toUpperCase(Locale.ROOT)
        } else
            CheckIn
    }

    fun byteArrayToHexString(array: ByteArray): String {
        var result = ""
        for (x in array) {
            result += Integer.toHexString(x.toUByte().toInt()) + ':'
        }
        return result
    }

    fun isTimeInInterval(start: String, end: String, target: String): Boolean {
        val hourStart = start.split(':')[0].toInt()
        val minStart = start.split(':')[1].toInt()
        val hourEnd = end.split(':')[0].toInt()
        val minEnd = end.split(':')[1].toInt()
        val targetHour = target.split(':')[0].toInt()
        val targetMin = target.split(':')[1].toInt()
        if (targetHour in (hourStart + 1) until hourEnd) return true
        if (targetHour == hourStart || targetHour == hourEnd) {
            if (targetMin in minStart..minEnd) return true
        }
        return false
    }

    fun startActivityUniversal() {

    }

    fun startSyncingService(service: Intent, context: Context) {
        service.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //context.startForeground(66, buildForegroundNotification(context))
            context.startForegroundService(service)
        } else {
            context.startService(service)
        }
    }

    fun serviceStartForegroundMultiAPI(service: Intent, context: Service) {
        if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForeground(66, buildForegroundNotification(context))
            context.startForegroundService(service)
        } else {
            context.startService(service)
        }
    }

    fun safeThreadSleep(milis: Long, deepSleep: Boolean) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() < startTime + milis) {
            try {
                Thread.sleep(milis)
            } catch (e: InterruptedException) {
                if (deepSleep)
                    Thread.currentThread().interrupt()
                else
                    break
            }
        }
    }

    fun promptSimpleDialog(activity: Activity, message: String, taskforce: ()-> Any){
        val dialog = AlertDialog.Builder(activity)
        dialog.setTitle("Warning")
        dialog.setIcon(android.R.drawable.ic_dialog_alert)
        dialog.setMessage(message)
        dialog.setPositiveButton(android.R.string.ok) { dial, _ -> taskforce; dial.cancel(); }
        dialog.setNegativeButton(android.R.string.cancel) { dial, _ -> dial.cancel(); }
        dialog.create().show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(context: Context, channelId: String, channelName: String): String {
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    private fun buildForegroundNotification(context: Context): Notification? {
        val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel(context, context.getString(R.string.background_runner_label),
                            context.getString(R.string.background_runner_label))
                } else {
                    ""
                }

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
        val notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                //.setBadgeIconType(R.mipmap.ic_launcher)
                //.setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.background_running))
                .setPriority(PRIORITY_MIN)
                //.setCategory(Notification.CATEGORY_SERVICE)
                .build()
        return notification
    }
}

object ReplaceTable {

    private val replaceTable = HashMap<Char, String>()

    private fun initReplacer(context: Context?): Boolean {
        if (replaceTable.size == 0 && context != null) {
            val stream = context.assets.open("table", Context.MODE_PRIVATE)
            val reader = InputStreamReader(stream)
            val buffer = BufferedReader(reader)
            do {
                val line = buffer.readLine()
                if (line != null) {
                    val cells = line.split(';')
                    if (!replaceTable.containsKey(cells[0][0]))
                        replaceTable[cells[0][0]] = cells[1]


                } else break
            } while (true)

            stream.close(); reader.close(); buffer.close()
            return true
        } else return replaceTable.size > 0
    }

    fun replaceString(input: String, context: Context? = null): String {
        if (!initReplacer(context)) return input
        var result = ""
        for (x in input) {
            if (replaceTable.containsKey(x))
                result += replaceTable[x]
            else
                result += x
        }
        return result
    }
}