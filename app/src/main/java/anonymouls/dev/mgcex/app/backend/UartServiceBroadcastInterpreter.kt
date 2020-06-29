package anonymouls.dev.mgcex.app.backend

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.*
import kotlin.collections.ArrayDeque

class UartServiceBroadcastInterpreter : BroadcastReceiver() {

    private var tsk = InsertTask()
    private var isInited = false

    @ExperimentalStdlibApi
    override fun onReceive(context: Context, intent: Intent) {
        val Action = intent.action
        when (Action) {
            "AlarmAction" -> {
                Algorithm.IsAlarmKilled = true
                Algorithm.IsAlarmWaiting = false
                Algorithm.IsAlarmingTriggered = false
                CommandInterpreter.getInterpreter(context)?.stopLongAlarm()
                val mNotificationManager = Algorithm.SelfPointer?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                mNotificationManager.cancel(21)
            }
            NotificationService.NotifyAction -> {
                val extras = intent.extras
                //val app = extras!!.get("app") as String // TODO integrations
                val Title = extras?.get("title") as String
                val Text = extras.get("text") as String
                val Message = Title + "\n" + Text
                Algorithm.SelfPointer?.ci?.fireNotification(Message)
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
                if (!isInited) {
                    tsk.ci = CommandInterpreter.getInterpreter(context)
                    isInited = true
                }

                if (Algorithm.StatusCode.value!!.code < Algorithm.StatusCodes.GattReady.code)
                    Algorithm.StatusCode.postValue(Algorithm.StatusCodes.GattReady)
                tsk.dataToHandle.add(
                        SimpleRecord(
                                intent.getByteArrayExtra(UartService.EXTRA_DATA)!!,
                                intent.getStringExtra(UartService.EXTRA_CHARACTERISTIC)!!
                        ))
                tsk.thread?.interrupt()
                if (tsk.status == AsyncTask.Status.FINISHED) tsk = InsertTask()
                else if (tsk.status != AsyncTask.Status.RUNNING) {
                    tsk.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                }
            }
            UartService.ACTION_GATT_SERVICES_DISCOVERED -> {
                Algorithm.StatusCode.postValue(Algorithm.StatusCodes.GattReady)
                UartService.instance?.enableTXNotification(UartService.RX_SERVICE_UUID, UartService.TX_CHAR_UUID, UartService.TXServiceDesctiptor)
                UartService.instance?.enableTXNotification(UartService.PowerServiceUUID, UartService.PowerTXUUID, UartService.PowerDescriptor)
                Algorithm.SelfPointer?.thread?.interrupt()
            }
// gatt init failed ?
        }
    }


}

class SimpleRecord(val Data: ByteArray, val characteristic: String)

class InsertTask : AsyncTask<Void, Void, Void>() {

    @ExperimentalStdlibApi
    val dataToHandle = ArrayDeque<SimpleRecord>()
    var thread: Thread? = null
    var timer: Timer = Timer("UIKostilSyncer")
    var ci: CommandInterpreter? = null

    private var confirmA = false
    private var confirmB = false

    override fun onPreExecute() {
        super.onPreExecute()
        val timertask: TimerTask = object : TimerTask() {
            override fun run() {
                if (confirmA && confirmB)
                    _insertedRunning.postValue(false)
                if (confirmA && !confirmB) confirmB = true
            }
        }
        timer = Timer()
        timer.schedule(timertask, 5000, 500)
    }

    @ExperimentalStdlibApi
    override fun doInBackground(vararg params: Void?): Void? {
        Thread.currentThread().name = "Database Inserter"
        thread = Thread.currentThread()
        while (true) {
            if (dataToHandle.size > 0) _insertedRunning.postValue(true)
            while (dataToHandle.size > 0) {
                try {
                    val sm = dataToHandle.removeFirst()
                    ci?.commandAction(sm.Data, UUID.fromString(sm.characteristic))
                    confirmA = false; confirmB = false
                } catch (ex: Exception) {

                }
                if (dataToHandle.size > 2500) dataToHandle.clear()
            }
            try {
                confirmA = true
                Thread.sleep(Long.MAX_VALUE)
            } catch (e: InterruptedException) {
                thread?.isInterrupted
            }
        }
    }

    companion object {
        private var _insertedRunning = MutableLiveData<Boolean>(false)

        val insertedRunning: LiveData<Boolean>
            get() {
                return _insertedRunning
            }

    }
}