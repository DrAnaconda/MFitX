package anonymouls.dev.mgcex.app.backend

import android.app.IntentService
import android.app.Service
import android.content.*
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import anonymouls.dev.mgcex.app.AlarmProvider
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.main.DeviceControllerActivity
import anonymouls.dev.mgcex.app.main.DeviceControllerViewModel
import anonymouls.dev.mgcex.databaseProvider.*
import anonymouls.dev.mgcex.util.Utils
import java.text.SimpleDateFormat
import java.util.*

class Algorithm : IntentService("Syncer") {
    override fun onHandleIntent(intent: Intent?) {
        Thread.currentThread().name = "Syncer"
        Thread.currentThread().priority = Thread.MIN_PRIORITY
        this.thread = Thread.currentThread()
        init()
        run()
    }

//region properties

    private var database: DatabaseController? = null
    private var prefs: SharedPreferences? = null
    private var workInProgress = false
    var thread: Thread? = null

    private var lastMainSync = Calendar.getInstance()
    private var lastHRSync = Calendar.getInstance()
    private var lastSleepSync = Calendar.getInstance()


    private var serviceObject: IBinder? = null
    private var serviceName: ComponentName? = null

//endregion


    enum class StatusCodes(val code: Int) { BluetoothDisabled(-2), DeviceLost(-1), Disconnected(0), Connected(1), GattReady(2) }


    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            serviceObject = service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                serviceName = name
                tryForceStartListener(applicationContext)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {}
    }
    private val mBinder: IBinder? = null

    override fun stopService(name: Intent?): Boolean {
        IsActive = false
        return super.stopService(name)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, Service.START_FLAG_RETRY, startId)
    }

    fun init() {
        if (IsInit) return
        val dCAct = Intent(this, DeviceControllerActivity::class.java)
        dCAct.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        var service = Intent(this, UartService::class.java)
        startService(service)
        service = Intent(this, NotificationService::class.java)
        if (!NotificationService.IsActive) {
            bindService(service, connection, Context.BIND_AUTO_CREATE)
            startService(service)
        }
        if (!isNotifyServiceAlive(this))
            tryForceStartListener(this)
        LockedAddress = Utils.getSharedPrefs(this).getString("BandAddress", null)
        prefs = Utils.getSharedPrefs(this)
        PhoneListener = PhoneStateListenerBroadcast()
        SBIReceiver = UartServiceBroadcastInterpreter()
        registerReceiver(SBIReceiver, DeviceControllerActivity.makeGattUpdateIntentFilter())
        val IF = IntentFilter("android.intent.action.PHONE_STATE")

        if (Utils.getSharedPrefs(this).getBoolean("ReceiveCalls", true))
            registerReceiver(PhoneListener, IF)

        IsInit = true
        SelfPointer = this
        database = DatabaseController.getDCObject(this)
        getLastHRSync()
        getLastMainSync()
    }

    override fun onDestroy() {
        unregisterReceiver(SBIReceiver)
        unregisterReceiver(PhoneListener)
        SelfPointer = null
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        init()
    }

    private fun getLastHRSync() {
        if (database != null)
            lastHRSync = CustomDatabaseUtils.GetLastSyncFromTable(DatabaseController.HRRecordsTableName,
                    HRRecordsTable.ColumnsNames, true, database!!.readableDatabase)
    }

    private fun getLastMainSync() {
        if (database != null)
            lastMainSync = CustomDatabaseUtils.GetLastSyncFromTable(MainRecordsTable.TableName,
                    MainRecordsTable.ColumnNames, true, database!!.readableDatabase)
    }

    /*
    private fun checkForAlarmWaiting(): Boolean {
        val Now = Calendar.getInstance()
        val NowHour = Now.get(Calendar.HOUR_OF_DAY)
        val NowMinute = Now.get(Calendar.MINUTE)
        if (ApproachingAlarm!!.HourStart <= NowHour || ApproachingAlarm!!.HourStart == NowHour
                && ApproachingAlarm!!.MinuteStart <= NowMinute) {
            IsAlarmWaiting = true
        }
        return IsAlarmWaiting
    }*/
    /*
    private fun checkForAlarms() {
        val Alarm = AlarmsTable.GetApproachingAlarm()
        if (Alarm.count > 0) {
            do {
                val Buff = AlarmProvider.LoadFromCursor(Alarm)
                if (AlarmProvider.IsAlarmsEqual(ApproachingAlarm, Buff)) {
                    if (!IsAlarmKilled) checkForAlarmWaiting()
                } else {
                    ApproachingAlarm = AlarmProvider.LoadFromCursor(Alarm)
                    IsAlarmKilled = false
                }

            } while (Alarm.moveToNext())
        }
    }
    fun alarmTriggerDecider(HRValue: Int) {
        val hour = Calendar.getInstance().get(Calendar.HOUR)
        val minute = Calendar.getInstance().get(Calendar.MINUTE)
        if (IsAlarmKilled) {
            AlarmFiredIterator = 0
            return
        }
        if (HRValue >= AvgHR || IsAlarmingTriggered
                || hour >= ApproachingAlarm!!.Hour && minute >= ApproachingAlarm!!.Minute) {
            IsAlarmingTriggered = true
            if (ApproachingAlarm!!.DayMask == 128) ApproachingAlarm!!.IsEnabled = false
            postCommand(CommandInterpreter.BuildLongNotify("WAKE UP!"), true)
            val resultIntent = Intent(this, UartServiceBroadcastInterpreter::class.java)
            resultIntent.action = "AlarmAction"
            val resultPendingIntent = PendingIntent.getBroadcast(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, "ALARMS")
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentText("Maybe you should kill it?")
                        .setContentTitle("Alarm is ringing")
                        .setContentIntent(resultPendingIntent)
            } else {
                Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentText("Maybe you should kill it?")
                        .setContentTitle("Alarm is ringing")
                        .setContentIntent(resultPendingIntent)
            }
            val notification = builder.build()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(21, notification)
        }
    }
    */

    fun postShortMessageDivider(Input: String) {
        val Req = CommandInterpreter.BuildNotify(Input)
        var Req1 = Arrays.copyOfRange(Req, 0, 20)
        postCommand(Req1, false)
        Thread.sleep(50)
        Req1 = Arrays.copyOfRange(Req, 20, 40)
        postCommand(Req1, false)
        Thread.sleep(50)
        Req1 = Arrays.copyOfRange(Req, 40, 46)
        postCommand(Req1, false)
        Thread.sleep(50)
    }

    private fun executeForceSync() {
        DeviceControllerViewModel.instance?.workInProgress?.postValue(View.VISIBLE)
        getLastHRSync()
        getLastMainSync()
        postCommand(CommandInterpreter.SyncTime(Calendar.getInstance()), false)
        customWait(1000)
        postCommand(CommandInterpreter.GetMainInfoRequest(), false)
        customWait(1000)
        postCommand(CommandInterpreter.requestSleepHistory(lastSleepSync), false)
        customWait(1000)
        postCommand(CommandInterpreter.requestHRHistory(lastHRSync), false)
        customWait(1000)
        //if (IsAlarmingTriggered && !IsFromActivity) alarmTriggerDecider(0)
    }

//region background taskforce

    private fun run() {
        if (Thread.currentThread().name !== "Syncer") return
        while (UartService.instance == null) customWait(3000)
        while (IsActive) {
            workInProgress = true
            when (StatusCode.value!!) {
                StatusCodes.BluetoothDisabled -> bluetoothDisabledAlgo()
                StatusCodes.Disconnected, StatusCodes.DeviceLost -> deviceLostAlgo()
                StatusCodes.Connected -> connectedAlgo()
                StatusCodes.GattReady -> executeMainAlgo()
            }
        }
    }

    private fun executeMainAlgo() {
        currentAlgoStatus.postValue(getString(R.string.connected_syncing))
        if (!isNotifyServiceAlive(this)) tryForceStartListener(this)
        executeForceSync()
        NextSync = Calendar.getInstance()
        NextSync!!.add(Calendar.MILLISECOND, MainSyncPeriodSeconds)
        currentAlgoStatus.postValue(getString(R.string.next_sync_status) + SimpleDateFormat("HH:mm", Locale.getDefault()).format(NextSync!!.time))
        workInProgress = false
        try {
            Thread.sleep(MainSyncPeriodSeconds.toLong())
        } catch (e: InterruptedException) {
        }
        workInProgress = true
    }

    private fun bluetoothDisabledAlgo() {
        if (DeviceControllerActivity.instance != null) {
            Utils.requestEnableBluetooth(DeviceControllerActivity.instance!!)
            if (Utils.bluetoothEngaging(DeviceControllerActivity.instance!!)) {
                StatusCode.postValue(StatusCodes.Disconnected)
                currentAlgoStatus.postValue(getString(R.string.status_engaging))
            } else {
                currentAlgoStatus.postValue(getString(R.string.offline_mode))
            }
        }
    }

    private fun deviceLostAlgo() {
        if (UartService.instance!!.connect(LockedAddress)) {
            currentAlgoStatus.postValue(getString(R.string.conntecting_status))
            while (UartService.instance!!.mConnectionState < UartService.STATE_CONNECTED) {
                try {
                    Thread.sleep(2500)
                } catch (e: InterruptedException) {
                }
            }
            StatusCode.postValue(StatusCodes.Connected)
        }
    }

    private fun connectedAlgo() {
        currentAlgoStatus.postValue(getString(R.string.discovering))
        if (UartService.instance!!.mConnectionState < UartService.STATE_DISCOVERED) {
            UartService.instance!!.retryDiscovering()
            try {
                Thread.sleep(60000)
            } catch (e: InterruptedException) {
            }
        } else {
            StatusCode.postValue(StatusCodes.GattReady)
        }
    }

//endregion

    fun forceSyncHR() {
        postCommand(CommandInterpreter.HRRealTimeControl(true), false)
        customWait(15000)
        postCommand(CommandInterpreter.HRRealTimeControl(false), true)
    }

    inner class LocalBinder : Binder() {
        internal val service: Algorithm
            get() = this@Algorithm
    }

    override fun onBind(intent: Intent): IBinder? {
        init()
        return mBinder
    }

    companion object {

        var NextSync: Calendar? = null
        var SelfPointer: Algorithm? = null
        var StatusCode = MutableLiveData(StatusCodes.Disconnected)

        var MainSyncPeriodSeconds = 310000 // 5`10`` in millis
        const val StatusAction = "STATUS_CHANGED"

        private var AvgHR: Int = 0
        private var AlarmFiredIterator = 0

        val currentAlgoStatus = MutableLiveData<String>()

        var LockedAddress: String? = null

        var ApproachingAlarm: AlarmProvider? = null
        var IsAlarmWaiting = false
        var IsAlarmingTriggered = false
        var IsAlarmKilled = false

        var IsActive = true
        private var IsInit = false

        private var PhoneListener: PhoneStateListenerBroadcast? = null
        private var SBIReceiver: UartServiceBroadcastInterpreter? = null
        fun isNotifyServiceAlive(context: Context): Boolean {
            val Names = NotificationManagerCompat.getEnabledListenerPackages(context)
            return Names.contains(context.packageName)
        }

        fun tryForceStartListener(context: Context) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(ComponentName(context, NotificationService::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(ComponentName(context, NotificationService::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }

        fun postCommand(Request: ByteArray, IsForAlarm: Boolean) {
            if ((IsForAlarm && AlarmFiredIterator % 50 == 0) || !IsForAlarm) {
                val RThread = { UartService.instance!!.writeRXCharacteristic(Request) }
                RThread.run { UartService.instance!!.writeRXCharacteristic(Request) }
            }
        }

        private fun customWait(MiliSecs: Long) {
            try {
                Thread.sleep(MiliSecs)
            } catch (e: InterruptedException) {
                //
            }

        }
    }

}

// TODO Found active status at sleep intervals. Integrate data visualization
// TODO Integrate 3d party services
// TODO Hard Tasks auto launch