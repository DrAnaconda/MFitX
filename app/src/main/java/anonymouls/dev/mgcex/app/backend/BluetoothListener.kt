package anonymouls.dev.mgcex.app.backend

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

@ExperimentalStdlibApi
class BluetoothListener : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        if (intent.action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR)

            when (state) {
                BluetoothAdapter.STATE_OFF -> Algorithm.SelfPointer?.thread?.interrupt()
            }
        }
    }
}