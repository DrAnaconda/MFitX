package anonymouls.dev.mgcex.app.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import anonymouls.dev.mgcex.util.Utils

class PhoneStateListenerBroadcast : BroadcastReceiver() {
    // TODO Fix and test, not working
    override fun onReceive(context: Context, intent: Intent) {
        if (Algorithm.StatusCode.value!!.code >= 3) {
            if (!Utils.SharedPrefs!!.getBoolean("ReceiveCalls", true)) return
            val extras = intent.extras
            val State = extras!!.getString(TelephonyManager.EXTRA_STATE)
            var IncomingNumber: String = extras.get(TelephonyManager.EXTRA_INCOMING_NUMBER) as String // TODO upgrade in 29
            val ci = CommandInterpreter.getInterpreter(context)
            when (State) {
                "IDLE", "OFFHOOK" -> ci?.stopLongAlarm()
                "RINGING" -> {
                    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(IncomingNumber))
                    val cursor: Cursor?
                    try {
                        cursor = context.contentResolver.query(uri,
                                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null, null)
                        cursor!!.moveToFirst()
                        IncomingNumber = cursor.getString(0)
                        cursor.close()
                    } catch (Ex: Exception) {
                        // ignore
                    }

                    ci?.buildLongNotify(IncomingNumber)
                }
            }
        }
    }
}
