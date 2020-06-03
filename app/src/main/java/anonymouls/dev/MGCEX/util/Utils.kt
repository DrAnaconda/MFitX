package anonymouls.dev.MGCEX.util

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
import anonymouls.dev.MGCEX.App.DeviceControllerActivity
import anonymouls.dev.MGCEX.App.R
import java.text.DecimalFormat
import java.util.*

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

    private val hexArray = "0123456789ABCDEF".toCharArray()

    fun getSharedPrefs(context: Context?): SharedPreferences {
        if (SharedPrefs == null || context != null)
            SharedPrefs = context!!.getSharedPreferences("MainPrefs", Context.MODE_PRIVATE)
        if (!SharedPrefs!!.contains(Analytics.UserID))
            SharedPrefs!!.edit().putString(Analytics.UserID, UUID.randomUUID().toString()).apply()
        return SharedPrefs!!
    }
    fun RequestEnableBluetooth(ContextActivity: Activity) {
        val RequestIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        ContextActivity.startActivityForResult(RequestIntent, BluetoothEnableRequestCode)
    }
    fun isBLESupported(context: Activity) {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(context, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            context.finish()
        }
    }

    fun bluetoothEngaging(AContext: Activity): Boolean {
        val bManager = AContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bManager == null) {
            Toast.makeText(AContext, R.string.BluetoothIsNotSupported, Toast.LENGTH_SHORT).show()
            AContext.finish()
        } else {
            if (bManager.adapter == null) {
                Toast.makeText(AContext, R.string.bt_unavailable, Toast.LENGTH_SHORT).show()
                RequestEnableBluetooth(AContext)
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


    fun RequestToBindNotifyService(ContextActivity: Activity){
        val ReqIntent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        ContextActivity.startActivity(ReqIntent)
    }
    fun bigNumberToString(number: Int, divider: Int): String{
        val newNumber: Float = number.toFloat() / divider
        val format = DecimalFormat("0.###")
        return if (newNumber < 1000) {
            format.format(newNumber)
        }
        else {
            if (newNumber > 1000000) {
                val millions: Float = newNumber / 1000000
                format.format(millions)+" M"
            }else {
                val thousands: Int = newNumber.toInt() / 1000
                "$thousands K"
            }
        }
    }
}
