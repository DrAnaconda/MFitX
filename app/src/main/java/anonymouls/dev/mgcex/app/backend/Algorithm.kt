package anonymouls.dev.mgcex.app.backend

import android.app.IntentService
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import anonymouls.dev.mgcex.app.AlarmProvider
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.main.DeviceControllerActivity
import anonymouls.dev.mgcex.app.main.DeviceControllerViewModel
import anonymouls.dev.mgcex.app.main.SettingsActivity
import anonymouls.dev.mgcex.databaseProvider.*
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class Algorithm : IntentService("Syncer") {

    //region Properties

    private lateinit var database: DatabaseController
    private lateinit var prefs: SharedPreferences
    private var hrMonitoringTimer: Timer? = null
    lateinit var ci: CommandInterpreter
    private var workInProgress = false
    private var isFirstTime = true
    private var connectionTries = 0
    var thread: Thread? = null

    private var serviceObject: IBinder? = null
    private var serviceName: ComponentName? = null

    //endregion

    enum class StatusCodes(val code: Int) {
        BluetoothDisabled(-2), DeviceLost(-1),
        Disconnected(0), Connected(10), Connecting(20), GattConnecting(21),
        GattConnected(30), GattDiscovering(40), GattReady(50)
    }

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

    //region Sync Utilities

    private fun getLastHRSync(): Calendar {
        return CustomDatabaseUtils.getLastSyncFromTable(HRRecordsTable.TableName,
                HRRecordsTable.ColumnsNames, true, database.readableDatabase)
    }

    private fun getLastMainSync(): Calendar {
        return CustomDatabaseUtils.getLastSyncFromTable(MainRecordsTable.TableName,
                MainRecordsTable.ColumnNames, true, database.readableDatabase)
    }

    private fun getLastSleepSync(): Calendar {
        return CustomDatabaseUtils.longToCalendar(SleepRecordsTable.getLastSync(database.readableDatabase), true)
    }

    private fun executeForceSync() {
        DeviceControllerViewModel.instance?.workInProgress?.postValue(View.VISIBLE)
        GlobalScope.launch(Dispatchers.IO) { database.initRepairsAndSync(database.writableDatabase) }
        ci.requestSettings()
        Utils.safeThreadSleep(1000, false)
        ci.requestBatteryStatus()
        Utils.safeThreadSleep(1000, false)
        ci.syncTime(Calendar.getInstance())
        Utils.safeThreadSleep(1000, false)
        ci.getMainInfoRequest()
        Utils.safeThreadSleep(1000, false)
        ci.requestSleepHistory(getLastSleepSync())
        Utils.safeThreadSleep(1000, false)
        ci.requestHRHistory(getLastHRSync())
        Utils.safeThreadSleep(1000, false)
        if (isFirstTime) forceSyncHR()
        //if (IsAlarmingTriggered && !IsFromActivity) alarmTriggerDecider(0)
    }

    private fun forceSyncHR() {
        ci.hRRealTimeControl(true)
        ci.requestManualHRMeasure(false)
        try {
            Utils.safeThreadSleep(10000, true)
        } catch (e: InterruptedException) {
            this.thread?.interrupt()
        }
        ci.hRRealTimeControl(false)
    }

    //endregion

    //region Background taskforce

    private fun run() {
        if (Thread.currentThread().name !== "Syncer") return
        while (UartService.instance == null) Utils.safeThreadSleep(3000, false)
        while (IsActive) {
            workInProgress = true
            when (StatusCode.value!!) {
                StatusCodes.BluetoothDisabled -> bluetoothDisabledAlgo()
                StatusCodes.Connected, StatusCodes.Disconnected,
                StatusCodes.DeviceLost, StatusCodes.Connecting, StatusCodes.GattConnecting -> deviceDisconnectedAlgo()
                StatusCodes.GattConnected, StatusCodes.GattDiscovering -> connectedAlgo()
                StatusCodes.GattReady -> executeMainAlgo()
            }
        }
    }

    private fun executeMainAlgo() {
        connectionTries = 0
        currentAlgoStatus.postValue(getString(R.string.connected_syncing))
        if (!isNotifyServiceAlive(this)) tryForceStartListener(this)
        executeForceSync()
        if ((!ci.hRRealTimeControlSupport && isFirstTime
                        && prefs.contains(SettingsActivity.Companion.HRMonitoringSettings.hrMeasureInterval))
                || ci.hRRealTimeControlSupport) {
            forceSyncHR()
            manualHRHack()
        }
        NextSync = Calendar.getInstance()
        NextSync!!.add(Calendar.MILLISECOND, MainSyncPeriodSeconds)
        currentAlgoStatus.postValue(getString(R.string.next_sync_status) + SimpleDateFormat("HH:mm", Locale.getDefault()).format(NextSync!!.time))
        workInProgress = false
        isFirstTime = false
        MainSyncPeriodSeconds = prefs.getInt(SettingsActivity.mainSyncMinutes, 5) * 60 * 1000
        Utils.safeThreadSleep(MainSyncPeriodSeconds.toLong(), false)
        workInProgress = true
    }

    private fun bluetoothDisabledAlgo() {
        isFirstTime = true
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

    private fun deviceDisconnectedAlgo() {
        if (StatusCode.value!!.code < StatusCodes.Connecting.code || connectionTries > 5) {
            currentAlgoStatus.postValue(getString(R.string.conntecting_status))
            if (UartService.instance!!.connect(LockedAddress)) {
                if (StatusCode.value!!.code < StatusCodes.Connected.code)
                    StatusCode.postValue(StatusCodes.Connected)
                connectionTries = 0
            }
        } else {
            if (connectionTries++ > 5)
                StatusCode.postValue(StatusCodes.Disconnected)
            else
                Utils.safeThreadSleep(1000, false)
        }
    }

    private fun connectedAlgo() {
        currentAlgoStatus.postValue(getString(R.string.discovering))
        if (StatusCode.value!!.code < StatusCodes.GattDiscovering.code) {
            connectionTries = 0
            //UartService.instance!!.retryDiscovering()
        } else if (StatusCode.value!!.code == StatusCodes.GattDiscovering.code) {
            if (connectionTries++ > 25) {
                UartService.instance?.disconnect()
                connectionTries = 0
            } else Utils.safeThreadSleep(1000, false)
        }
    }

    //endregion

    //region Android

    override fun onHandleIntent(intent: Intent?) {
        Thread.currentThread().name = "Syncer"
        Thread.currentThread().priority = Thread.MIN_PRIORITY
        this.thread = Thread.currentThread()
        init()
        run()
    }

    override fun onBind(intent: Intent): IBinder? {
        init()
        return mBinder
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

    override fun stopService(name: Intent?): Boolean {
        IsActive = false
        return super.stopService(name)
    }

    fun init() {
        if (IsInit) return
        isFirstTime = true
        ci = CommandInterpreter.getInterpreter(this)
        prefs = Utils.getSharedPrefs(this)
        database = DatabaseController.getDCObject(this)
        UartService.instance = UartService(this)
        MainSyncPeriodSeconds = prefs.getInt(SettingsActivity.mainSyncMinutes, 5) * 60 * 1000

        val dCAct = Intent(this, DeviceControllerActivity::class.java)
        dCAct.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        //Utils.serviceStartForegroundMultiAPI(Intent(this, UartService::class.java), this)
        val service = Intent(this, NotificationService::class.java)
        if (!NotificationService.IsActive) {
            bindService(service, connection, Context.BIND_AUTO_CREATE)
            Utils.serviceStartForegroundMultiAPI(service, this)
        }
        if (!isNotifyServiceAlive(this))
            tryForceStartListener(this)
        LockedAddress = Utils.getSharedPrefs(this).getString(SettingsActivity.bandAddress, null)
        PhoneListener = PhoneStateListenerBroadcast()
        SBIReceiver = UartServiceBroadcastInterpreter()
        registerReceiver(SBIReceiver, DeviceControllerActivity.makeGattUpdateIntentFilter())
        val IF = IntentFilter("android.intent.action.PHONE_STATE")

        if (Utils.getSharedPrefs(this).getBoolean("ReceiveCalls", true))
            registerReceiver(PhoneListener, IF)

        IsInit = true
        SelfPointer = this
        getLastHRSync()
        getLastMainSync()
    }

    //endregion

    private fun manualHRHack() {
        val startString = prefs.getString(SettingsActivity.Companion.HRMonitoringSettings.hrMeasureStart, "00:00")
        val endString = prefs.getString(SettingsActivity.Companion.HRMonitoringSettings.hrMeasureEnd, "00:00")
        var targetString = Utils.subIntegerConversionCheck(Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toString())
        targetString += ":"
        targetString += Utils.subIntegerConversionCheck(Calendar.getInstance().get(Calendar.MINUTE).toString())
        val isActive = if (startString == endString) true; else Utils.isTimeInInterval(startString!!, endString!!, targetString)
        if (isActive && prefs.getBoolean(SettingsActivity.Companion.HRMonitoringSettings.hrMonitoringEnabled, false)) {
            ci.requestManualHRMeasure(false)
        }
        val interval = prefs.getInt(SettingsActivity.Companion.HRMonitoringSettings.hrMeasureInterval, 5)
        val taskForce = object : TimerTask() {
            override fun run() {
                manualHRHack()
            }
        }
        hrMonitoringTimer = Timer(); hrMonitoringTimer?.schedule(taskForce, interval.toLong() * 60 * 1000)
    }

    companion object {

        var NextSync: Calendar? = null
        var SelfPointer: Algorithm? = null
        var StatusCode = MutableLiveData(StatusCodes.Disconnected)

        var MainSyncPeriodSeconds = 310000
        const val StatusAction = "STATUS_CHANGED"

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

        fun updateStatusCode(newStatus: StatusCodes) {
            StatusCode.postValue(newStatus)
            SelfPointer?.thread?.interrupt()
        }
    }

}

// TODO Speed up activities transitions or upgrade to fragments
// TODO Incapsulate UARTService
// TODO Integrate data sleep visualization
// TODO Integrate 3d party services
// TODO Battery health tracker + Power save algo
// TODO Manual hearth value request
// TODO LM: Sleep Data (tests needed)
// TODO LM: Other settings (dnd, alarms)