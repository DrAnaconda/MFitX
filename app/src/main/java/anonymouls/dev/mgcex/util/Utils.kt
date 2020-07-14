package anonymouls.dev.mgcex.util

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.main.DeviceControllerActivity
import anonymouls.dev.mgcex.app.main.SettingsActivity
import anonymouls.dev.mgcex.databaseProvider.SleepRecordsTable
import java.text.DecimalFormat
import java.util.*
import kotlin.math.roundToInt

object Utils {

    const val BluetoothEnableRequestCode = 1
    const val LocationPermissionRequest = 2
    const val PermissionsRequest = 3
    const val PermsRequest = 4
    const val PermsAdvancedRequest = 5
    var SharedPrefs: SharedPreferences? = null

    var IsStorageAccess = true
    var IsLocationAccess = true

    val UsedPerms = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE, //Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.WAKE_LOCK,
            Manifest.permission.READ_PHONE_STATE)//, Manifest.permission.RECEIVE_BOOT_COMPLETED)

    fun getSharedPrefs(context: Context): SharedPreferences {
        if (SharedPrefs == null) {
            SharedPrefs = context.getSharedPreferences("MainPrefs", Context.MODE_PRIVATE)
            SleepRecordsTable.GlobalSettings.ignoreLightSleepData = SharedPrefs!!.getBoolean(SettingsActivity.lightSleepIgnore, true)
        }
        if (!SharedPrefs!!.contains(Analytics.UserID))
            SharedPrefs!!.edit().putString(Analytics.UserID, UUID.randomUUID().toString()).apply()
        return SharedPrefs!!
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

    fun bluetoothEngaging(AContext: Activity): Boolean {
        val bManager = AContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bManager == null) {
            Toast.makeText(AContext, R.string.BluetoothIsNotSupported, Toast.LENGTH_SHORT).show()
            AContext.finish()
        } else {
            if (bManager.adapter == null) {
                Toast.makeText(AContext, R.string.bt_unavailable, Toast.LENGTH_SHORT).show()
                requestEnableBluetooth(AContext)
                return false
            } else
                return true
        }
        return true
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

    fun serviceStartForegroundMultiAPI(service: Intent, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(service)
        } else {
            context.startService(service)
        }
    }

    fun safeThreadSleep(milis: Long, rethrow: Boolean) {
        try {
            Thread.sleep(milis)
        } catch (e: InterruptedException) {
            if (rethrow) throw e
        }
    }
}
