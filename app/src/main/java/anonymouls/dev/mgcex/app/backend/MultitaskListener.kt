package anonymouls.dev.mgcex.app.backend

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import anonymouls.dev.mgcex.util.PreferenceListener

import anonymouls.dev.mgcex.util.Utils

@ExperimentalStdlibApi
class MultitaskListener : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR)

                when (state) {
                    BluetoothAdapter.STATE_OFF -> Algorithm.StatusCode.postValue(Algorithm.StatusCodes.Dead)
                }
            }
            restartAction, "android.intent.action.MAIN" -> ressurectService(context)
        }

    }


    companion object {
        const val restartAction = "SVSR"

        fun ressurectService(context: Context) {
            if (Utils.getSharedPrefs(context).contains(PreferenceListener.Companion.PrefsConsts.bandAddress))
                Utils.startSyncingService(Intent(context, Algorithm::class.java),
                        context)
        }
    }
}