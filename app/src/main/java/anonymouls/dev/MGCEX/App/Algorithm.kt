package anonymouls.dev.MGCEX.App

import android.annotation.SuppressLint
import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.*
import androidx.core.app.NotificationManagerCompat

import java.text.SimpleDateFormat

import anonymouls.dev.MGCEX.DatabaseProvider.AlarmsTable
import anonymouls.dev.MGCEX.DatabaseProvider.CustomDatabaseUtils
import anonymouls.dev.MGCEX.DatabaseProvider.DatabaseController
import anonymouls.dev.MGCEX.DatabaseProvider.HRRecordsTable
import anonymouls.dev.MGCEX.DatabaseProvider.MainRecordsTable
import anonymouls.dev.MGCEX.util.HRAnalyzer
import anonymouls.dev.MGCEX.util.TopExceptionHandler
import anonymouls.dev.MGCEX.util.Utils
import java.util.*

class Algorithm : IntentService("Syncer") {
    override fun onHandleIntent(intent: Intent?) {
        Thread.currentThread().name     = "Syncer"
        Thread.currentThread().priority = Thread.MIN_PRIORITY
        run()
    }

    private var Database: DatabaseController? = null
    private var Prefs: SharedPreferences? = null

    var workInProgress: Boolean = true
    var LastMainSync = Calendar.getInstance()
    var LastHRSync = Calendar.getInstance()
    var LastSleepSync = Calendar.getInstance()

    var synchronizer = 0
    private var isSchleduled = false
    private var syncTimer = Timer()
    private var lastStatus = ""

    private var ServiceObject: IBinder? = null
    private var ServiceName: ComponentName? = null
    private var hardTask: AsyncTask<Void, Void, Void>? = null

    private val Connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            ServiceObject = service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ServiceName = name
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
    @SuppressLint("WrongConstant")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, Service.START_STICKY, startId)
    }
    fun init(){
        if (IsInit) return
        val dCAct = Intent(this, DeviceControllerActivity::class.java)
        dCAct.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        var service = Intent(this, UartService::class.java)
        startService(service)
        service = Intent(this, NotificationService::class.java)
        if (!NotificationService.IsActive) {
            bindService(service, Connection, Context.BIND_AUTO_CREATE)
            startService(service)
        }
        if (!isNotifyServiceAlive(this))
            tryForceStartListener(this)
        LockedAddress = Utils.GetSharedPrefs(this).getString("BandAddress", null)
        Prefs = Utils.GetSharedPrefs(this)
        PhoneListener = PhoneStateListenerBroadcast()
        SBIReceiver = UartServiceBroadcastInterpreter()
        registerReceiver(SBIReceiver, DeviceControllerActivity.makeGattUpdateIntentFilter())
        val IF = IntentFilter("android.intent.action.PHONE_STATE")

        if (Utils.GetSharedPrefs(this).getBoolean("ReceiveCalls", true))
            registerReceiver(PhoneListener, IF)

        IsInit = true
        SelfPointer = this
        Database = DatabaseController.getDCObject(this)
        getLastHRSync()
        getLastMainSync()
    }
    fun startWorkInProgress(){
        synchronizer = 0
        if (!isSchleduled) {
            syncTimer.schedule(object : TimerTask() {
                override fun run() {
                    checkWorkInProgress()
                }
            }, 2000, 2000)
            isSchleduled = true
        }
    }
    private fun stopTimer(){
        syncTimer.cancel()
        syncTimer.purge()
        isSchleduled = false
    }
    private fun checkWorkInProgress(){
        if (synchronizer++ > 5) {
            workInProgress = false
            stopTimer()
            additionalStatus = null
            this.lastStatus.replace(getString(R.string.downloading_data_status), "")
            changeStatus(lastStatus)
        } else {
            additionalStatus = getString(R.string.downloading_data_status)
            workInProgress = true
            changeStatus(lastStatus)
        }
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
        if (Database != null)
            LastHRSync = CustomDatabaseUtils.GetLastSyncFromTable(DatabaseController.HRRecordsTableName,
                    HRRecordsTable.ColumnsNames, true, Database!!.currentDataBase!!)
    }
    private fun getLastMainSync() {
        if (Database != null)
            LastMainSync = CustomDatabaseUtils.GetLastSyncFromTable(DatabaseController.MainRecordsTableName,
                    MainRecordsTable.ColumnNames, true, Database!!.currentDataBase!!)
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
    fun executeForceSync(IsFromActivity: Boolean) {
        workInProgress = true
        getLastHRSync()
        getLastMainSync()
        postCommand(CommandInterpreter.requestHRHistory(LastHRSync), false)
        customWait(1000)
        postCommand(CommandInterpreter.SyncTime(Calendar.getInstance()), false)
        customWait(1000)
        postCommand(CommandInterpreter.GetMainInfoRequest(), false)
        customWait(1000)
        postCommand(CommandInterpreter.requestSleepHistory(LastSleepSync), false)
        customWait(1000)
        //if (IsAlarmingTriggered && !IsFromActivity) alarmTriggerDecider(0)
        if (IsFromActivity) workInProgress = false
        changeStatus(this.lastStatus)
    }


    var additionalStatus: String? = null

    private fun changeStatus(Text: String) {
        var text = Text
        if (!text.isNotEmpty() && this.lastStatus.isNotEmpty())
            text = this.lastStatus
        if (HRAnalyzer.isShadowAnalyzerRunning)
            text += "\nData analyzer is running..."
        if (additionalStatus != null) {
            if (additionalStatus != null && !text.contains(additionalStatus!!))
                text += "\n"+additionalStatus
            additionalStatus = null
        }
        Algorithm.LastStatus = text
    }

    private fun run() {
        if (Thread.currentThread().name !== "Syncer") return
        HRAnalyzer.analyzeShadowMainData(this.Database!!.writableDatabase)
        while (UartService.instance == null) customWait(3000)
        while (IsActive) {
            if (DeviceControllerActivity.StatusCode < 2) {
                when (DeviceControllerActivity.StatusCode) {
                    -2 -> {
                        if(DeviceControllerActivity.IsActive) {
                            Utils.RequestEnableBluetooth(DeviceControllerActivity.instance!!)
                            if (Utils.BluetoothEngaging(DeviceControllerActivity.instance!!)) {
                                DeviceControllerActivity.StatusCode = 0
                                changeStatus(getString(R.string.status_engaging))
                            } else{
                                changeStatus(getString(R.string.offline_mode))
                            }
                        }
                    }
                    -1, 0 -> if (UartService.instance!!.connect(LockedAddress)) {
                            changeStatus(getString(R.string.conntecting_status))
                            while (UartService.instance!!.mConnectionState < UartService.STATE_CONNECTED) {
                                Thread.sleep(2500)
                            }
                            DeviceControllerActivity.StatusCode = 1
                        }
                    1 -> {
                        changeStatus(getString(R.string.discovering))
                        if (UartService.instance!!.mConnectionState < UartService.STATE_DISCOVERED) {
                            Thread.sleep(3000)
                            UartService.instance!!.retryDiscovering()
                        } else{
                            DeviceControllerActivity.StatusCode = 3
                        }
                    }
                }
                continue
            }
            //if (!IsAlarmWaiting) checkForAlarms()
            if (!ServiceObject!!.isBinderAlive && !isNotifyServiceAlive(this)) tryForceStartListener(this)
            if (DeviceControllerActivity.isFirstLaunch){
                this.lastStatus = getString(R.string.connected_syncing)
                changeStatus(getString(R.string.connected_syncing))
                postCommand(CommandInterpreter.HRRealTimeControl(true), false)
                customWait(12000)
                postCommand(CommandInterpreter.HRRealTimeControl(false), true)
                DeviceControllerActivity.isFirstLaunch = false
            }
            executeForceSync(false)
            NextSync = Calendar.getInstance()
            NextSync!!.add(Calendar.MILLISECOND, MainSyncPeriodSeconds)
            this.lastStatus = getString(R.string.next_sync_status) + SimpleDateFormat("HH:mm", Locale.getDefault()).format(NextSync!!.time)
            changeStatus(LastStatus)
            if (!DatabaseController.getDCObject(this).currentDataBase!!.inTransaction() && hardTask == null){
                hardTask = AsyncCollapser()
                hardTask!!.execute()
            }
            workInProgress = false
            customWait(MainSyncPeriodSeconds.toLong())
            workInProgress = true
        }
    }

    inner class LocalBinder : Binder() {
        internal val service: Algorithm
            get() = this@Algorithm
    }
    override fun onBind(intent: Intent): IBinder? {
        init()
        return mBinder
    }
    override fun onUnbind(intent: Intent): Boolean {
        return super.onUnbind(intent)
    }

    companion object {
        var NextSync: Calendar? = null
        var IsRealTimeSynced = false
        var SelfPointer: Algorithm? = null
        var BatteryHolder : Int = -1
        var LastHearthRateIncomed : Int = -1
        var LastStepsIncomed : Int = -1
        var LastCcalsIncomed : Int = -1
        var LastStatus       : String = ""

        var MainSyncPeriodSeconds = 310000 // 5`10`` in millis
        const val StatusAction = "STATUS_CHANGED"

        private var AvgHR: Int = 0
        private var AlarmFiredIterator = 0

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

        fun postCommand(Request: ByteArray, IsForAlarm : Boolean) {
                if ((IsForAlarm && AlarmFiredIterator%50 == 0) || !IsForAlarm) {
                    val RThread = { UartService.instance!!.writeRXCharacteristic(Request) }
                    RThread.run { UartService.instance!!.writeRXCharacteristic(Request) }
                }
        }
        private fun customWait(MiliSecs: Long) {
            try {
                Thread.sleep(MiliSecs)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        }
    }


    inner class AsyncCollapser: AsyncTask<Void, Void, Void>(){

        private var exceptionHandler: TopExceptionHandler = TopExceptionHandler(SelfPointer!!.applicationContext)

        override fun doInBackground(vararg params: Void?): Void? {
            MainRecordsTable.executeDataCollapse(Prefs!!.getLong(MainRecordsTable.SharedPrefsMainCollapsedConst, 0), Prefs!!, Database!!.currentDataBase!!)
            return null
        }
        override fun onCancelled(result: Void?) {
            hardTask = null
            super.onCancelled(result)
        }
        override fun onCancelled() {
            hardTask = null
            super.onCancelled()
        }
        override fun onPostExecute(result: Void?) {
            hardTask = null
            super.onPostExecute(result)
        }
    }
}

// TODO Integrate sleeping data. Develop and test algos.
// TODO Integrate 3d party services