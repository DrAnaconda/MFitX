package anonymouls.dev.mgcex.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.view.View
import anonymouls.dev.mgcex.app.Backend.Algorithm
import anonymouls.dev.mgcex.app.Backend.CommandInterpreter

class UartServiceBroadcastInterpreter : BroadcastReceiver() {

    private val tsk = InsertTask()

    override fun onReceive(context: Context, intent: Intent) {
        val Action = intent.action
        when (Action) {
            "AlarmAction" -> {
                Algorithm.IsAlarmKilled = true
                Algorithm.IsAlarmWaiting = false
                Algorithm.IsAlarmingTriggered = false
                Algorithm.postCommand(CommandInterpreter.StopLongAlarm(), false)
                val mNotificationManager = Algorithm.SelfPointer?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                mNotificationManager.cancel(21)
            }
            NotificationService.NotifyAction -> {
                val extras = intent.extras
                //val app = extras!!.get("app") as String // TODO integrations
                val Title = extras?.get("title") as String
                val Text = extras.get("text") as String
                val Message = Title + "\n" + Text
                Algorithm.SelfPointer?.postShortMessageDivider(Message)
            }
            UartService.ACTION_GATT_CONNECTED -> {
                DeviceControllerActivity.StatusCode = 1
                UartService.instance?.retryDiscovering()
                Algorithm.LastStatus = "Status : Connected"
            }
            UartService.ACTION_GATT_DISCONNECTED -> {
                DeviceControllerActivity.StatusCode = -1
                Algorithm.LastStatus = "Status : Device lost. Trying to reconnect"
                DeviceControllerActivity.instance?.realTimeSwitch?.visibility = View.GONE
            }
            UartService.ACTION_DATA_AVAILABLE -> {
                DeviceControllerActivity.StatusCode = 5
                tsk.dataToHandle.add(intent.getByteArrayExtra(UartService.EXTRA_DATA)!!)
                if (tsk.status != AsyncTask.Status.RUNNING) {
                    tsk.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                }
            }
            UartService.ACTION_GATT_SERVICES_DISCOVERED -> {
                UartService.instance?.enableTXNotification()
                DeviceControllerActivity.StatusCode = 2
                DeviceControllerActivity.instance?.realTimeSwitch?.visibility = View.VISIBLE
            }
// gatt init failed ?
        }
    }

    private class InsertTask : AsyncTask<Void, Void, Void>() {

        val dataToHandle = ArrayList<ByteArray>()

        override fun doInBackground(vararg params: Void?): Void? {
            Thread.currentThread().name = "Database Inserter"
            while (true) {
                try {
                    for (x in 0 until dataToHandle.size - 1) {
                        var cmd: ByteArray
                        if (x < dataToHandle.size - 1) {
                            if (dataToHandle[x] != null) { // it is not true, sometimes null is coming
                                cmd = dataToHandle[x].clone()
                            } else {
                                dataToHandle.removeAt(x); continue
                            }
                        } else break
                        dataToHandle.removeAt(x)
                        CommandInterpreter.CommandAction(cmd)
                        Algorithm.SelfPointer?.startWorkInProgress()
                    }
                } catch (ex: Exception) {
                    continue
                }
            }
        }
    }
}