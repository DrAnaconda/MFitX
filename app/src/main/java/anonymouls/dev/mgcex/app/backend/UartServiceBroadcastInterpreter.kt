package anonymouls.dev.mgcex.app.backend

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.AsyncTask

class UartServiceBroadcastInterpreter : BroadcastReceiver() {

    private var tsk = InsertTask()

    @ExperimentalStdlibApi
    val dataToHandle = ArrayDeque<ByteArray>()

    @ExperimentalStdlibApi
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
                Algorithm.StatusCode.postValue(Algorithm.StatusCodes.Connected)
                UartService.instance?.retryDiscovering()
                //Algorithm.LastStatus =  "Status : Connected"
            }
            UartService.ACTION_GATT_DISCONNECTED -> {
                Algorithm.StatusCode.postValue(Algorithm.StatusCodes.Disconnected)
                //Algorithm.LastStatus = "Status : Device lost. Trying to reconnect"
            }
            UartService.ACTION_DATA_AVAILABLE -> {
                //DeviceControllerActivity.StatusCode = 5
                dataToHandle.add(intent.getByteArrayExtra(UartService.EXTRA_DATA)!!)
                if (tsk.status == AsyncTask.Status.FINISHED) tsk = InsertTask()
                else if (tsk.status != AsyncTask.Status.RUNNING) {
                    tsk.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                }
            }
            UartService.ACTION_GATT_SERVICES_DISCOVERED -> {
                UartService.instance?.enableTXNotification()
                Algorithm.StatusCode.postValue(Algorithm.StatusCodes.GattReady)
            }
// gatt init failed ?
        }
    }

    inner class InsertTask : AsyncTask<Void, Void, Void>() {

        @ExperimentalStdlibApi
        override fun doInBackground(vararg params: Void?): Void? {
            Thread.currentThread().name = "Database Inserter"
            while (dataToHandle.size > 0) {
                try {
                    CommandInterpreter.CommandAction(dataToHandle.removeFirst())
                    Algorithm.SelfPointer?.startWorkInProgress()
                } catch (ex: Exception) {

                }
            }
            return null
        }

    }


}