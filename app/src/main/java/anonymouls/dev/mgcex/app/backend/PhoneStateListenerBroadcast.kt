package anonymouls.dev.mgcex.app.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.util.Utils

class PhoneStateListenerBroadcast : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        var permissionGrantedReadState = true
        var callLogPermissionGranted = true
        var contactPermissionGranted = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissionGrantedReadState = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            callLogPermissionGranted = context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
            contactPermissionGranted = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        }

        if (Algorithm.StatusCode.value!!.code >= Algorithm.StatusCodes.GattReady.code && permissionGrantedReadState) {
            if (!Utils.SharedPrefs!!.getBoolean("ReceiveCalls", true)) return
            val extras = intent.extras
            val state = extras!!.getString(TelephonyManager.EXTRA_STATE)
            var incomingNumber = context.getString(R.string.incoming_call)
            if (callLogPermissionGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                val smth = extras.get(TelephonyManager.EXTRA_INCOMING_NUMBER) // TODO upgrade in 29
                if (smth != null)
                    incomingNumber = smth as String
            }

            val ci = CommandInterpreter.getInterpreter(context)
            when (state) {
                "IDLE", "OFFHOOK" -> ci.stopLongAlarm()
                "RINGING" -> {
                    if (contactPermissionGranted) {
                        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(incomingNumber))
                        val cursor: Cursor?
                        try {
                            cursor = context.contentResolver.query(uri,
                                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null, null)
                            cursor!!.moveToFirst()
                            incomingNumber = cursor.getString(0)
                            cursor.close()
                        } catch (Ex: Exception) {
                            // ignore
                        }
                    }
                    ci.buildLongNotify(incomingNumber)
                }
            }
        }
    }
}
