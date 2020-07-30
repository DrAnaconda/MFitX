package anonymouls.dev.mgcex.app.backend

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import anonymouls.dev.mgcex.app.AlarmProvider
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.ApplicationStarter.Companion.commandHandler
import anonymouls.dev.mgcex.databaseProvider.CustomDatabaseUtils
import anonymouls.dev.mgcex.databaseProvider.HRRecordsTable
import anonymouls.dev.mgcex.databaseProvider.MainRecordsTable
import anonymouls.dev.mgcex.databaseProvider.SleepRecordsTable
import anonymouls.dev.mgcex.util.PreferenceListener
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.text.SimpleDateFormat
import java.util.*


@ExperimentalStdlibApi
class Algorithm : LifecycleService(), ConnectionObserver {

    companion object {

        var SelfPointer: Algorithm? = null
        var StatusCode = MutableLiveData(StatusCodes.Disconnected)

        val currentAlgoStatus = MutableLiveData<String>(ApplicationStarter.appContext.getString(R.string.status_label))

        var ApproachingAlarm: AlarmProvider? = null
        var IsAlarmWaiting = false
        var IsAlarmingTriggered = false
        var IsAlarmKilled = false

        var IsActive = false

        fun updateStatusCode(newStatus: StatusCodes) {
            if (newStatus == StatusCodes.Dead) SelfPointer?.killService()
            if (StatusCode.value!!.code == StatusCodes.Dead.code) {
                return
            } else {
                StatusCode.postValue(newStatus)
            }
        }
    }

    //region Properties

    private lateinit var prefs: SharedPreferences
    private var isFirstTime = true
    private var nextSyncMain: Calendar = Calendar.getInstance()
    private var nextSyncHR: Calendar? = null
    private var savedBattery: Short = 100

    private var mainSyncTimer = Timer("AAMainTimer", false)
    private var hrSyncTimer = Timer("AAHRSyncTimer", false)
    private var disabledTimer = Timer("AADisabledTimer", false)

    lateinit var ci: CommandInterpreter
    lateinit var uartService: UartServiceMK2
    private lateinit var lockedAddress: String

    //endregion

    enum class StatusCodes(val code: Int) {
        Dead(-666), BluetoothDisabled(-2), DeviceLost(-1),
        Disconnected(0), Connected(10), Connecting(20), GattConnecting(21),
        GattConnected(30), GattDiscovering(40), GattReady(50)
    }

    private fun buildStatusMessage() {
        var result = ""
        if (checkPowerAlgo()) {
            result += getString(R.string.next_sync_status) +
                    SimpleDateFormat(Utils.SDFPatterns.TimeOnly.pattern,
                            Locale.getDefault()).format(nextSyncMain.time)


            if (nextSyncHR != null && prefs.getBoolean(PreferenceListener.Companion.PrefsConsts.hrMonitoringEnabled, false)) {
                result += "\n" + getString(R.string.hr_data_requested) +
                        SimpleDateFormat(Utils.SDFPatterns.TimeOnly.pattern,
                                Locale.getDefault()).format(nextSyncHR?.time)
            }
        } else {
            result += "\n" + getString(R.string.battery_low_status)
        }
        currentAlgoStatus.postValue(result)
    }

    //region Sync Utilities

    private fun getLastHRSync(): Calendar {
        return CustomDatabaseUtils.getLastSyncFromTable(HRRecordsTable.TableName,
                HRRecordsTable.ColumnsNames, true)
    }

    private fun getLastMainSync(): Calendar {
        return CustomDatabaseUtils.getLastSyncFromTable(MainRecordsTable.TableName,
                MainRecordsTable.ColumnNames, true)
    }

    private fun getLastSleepSync(): Calendar {
        return CustomDatabaseUtils.longToCalendar(SleepRecordsTable.getLastSync(), true)
    }

    private fun forceSyncHR() {
        ci.hRRealTimeControl(true)
        Handler(commandHandler.looper).postDelayed({ ci.hRRealTimeControl(false) }, 2000)
    }

    //endregion

    //region Background Taskforce

    private fun checkPowerAlgo(): Boolean {
        return if (Utils.getSharedPrefs(this).getBoolean(PreferenceListener.Companion.PrefsConsts.batterySaverEnabled, true)) {
            val threshold = Utils.getSharedPrefs(this).getString(PreferenceListener.Companion.PrefsConsts.batteryThreshold, "20")!!.toInt()
            savedBattery !in 0..threshold
        } else true
    }
    private fun reWipeTimers(){
        mainSyncTimer.cancel(); mainSyncTimer.purge()
        hrSyncTimer.cancel();   hrSyncTimer.purge()
        disabledTimer = Timer("AADisabledTimer", false)
        disabledTimer.schedule(object : TimerTask(){
            override fun run() { killService() }
        }, 5*60*1000)
    }

    private fun deadAlgo() {
        StatusCode.postValue(StatusCodes.Dead)
        IsActive = false
        SelfPointer = null
        uartService.disconnect()
        SelfPointer = null
        currentAlgoStatus.postValue(this.getString(R.string.status_label))
        this.stopForeground(true)
        this.stopSelf()
    }
    private fun executeMainAlgo() {
        disabledTimer.cancel(); disabledTimer.purge()
        mainSyncTimer.cancel(); mainSyncTimer.purge()
        val syncPeriod = prefs.getString(PreferenceListener.Companion.PrefsConsts.mainSyncMinutes, "5")!!.toLong() * 60 * 1000
        nextSyncMain = Calendar.getInstance()
        nextSyncMain.add(Calendar.MILLISECOND, syncPeriod.toInt())

        if (checkPowerAlgo()) {
            currentAlgoStatus.postValue(getString(R.string.connected_syncing))
            executeForceSync()
            if ((!ci.hRRealTimeControlSupport && isFirstTime
                            && prefs.contains(PreferenceListener.Companion.PrefsConsts.hrMeasureInterval))
                    || ci.hRRealTimeControlSupport) {
                forceSyncHR()
                manualHRHack()
            }
            isFirstTime = false
            buildStatusMessage()
        }
        buildStatusMessage()
        mainSyncTimer = Timer("AAMainTimer", false)
        mainSyncTimer.schedule(object : TimerTask() {
            override fun run() { executeMainAlgo() }
        }, syncPeriod, syncPeriod)
    }
    private fun manualHRHack() {
        if (StatusCode.value!!.code == StatusCodes.Dead.code
                || ci.hRRealTimeControlSupport) return
        this.hrSyncTimer.cancel(); this.hrSyncTimer.purge()
        val startString = prefs.getString(PreferenceListener.Companion.PrefsConsts.hrMeasureStart, "00:00")
        val endString = prefs.getString(PreferenceListener.Companion.PrefsConsts.hrMeasureEnd, "00:00")
        var targetString = Utils.subIntegerConversionCheck(Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toString())
        targetString += ":"
        targetString += Utils.subIntegerConversionCheck(Calendar.getInstance().get(Calendar.MINUTE).toString())
        val isActive = if (startString == endString) true; else Utils.isTimeInInterval(startString!!, endString!!, targetString)
        if (isActive && prefs.getBoolean(PreferenceListener.Companion.PrefsConsts.hrMonitoringEnabled, false)
                && checkPowerAlgo()
                && StatusCode.value!!.code >= StatusCodes.GattReady.code) {
            ci.requestManualHRMeasure(false)
        }
        val interval = prefs.getString(PreferenceListener.Companion.PrefsConsts.hrMeasureInterval, "5")!!.toLong() * 60 *1000
        this.nextSyncHR = Calendar.getInstance(); this.nextSyncHR?.add(Calendar.MINUTE, interval.toInt())
        buildStatusMessage()
        hrSyncTimer = Timer("AAHRSyncTimer", false)
        hrSyncTimer.schedule(object : TimerTask() {
            override fun run() { manualHRHack() }
        }, interval, interval)
    }

    //endregion

    //region Android

    override fun onDestroy() {
        super.onDestroy()
        deadAlgo()
        this.stopForeground(true)
        SelfPointer = null
        sendBroadcast(Intent(MultitaskListener.restartAction))
        this.uartService.disconnect()
        this.uartService.close()
    }

    override fun stopService(name: Intent?): Boolean {
        deadAlgo()
        return super.stopService(name)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            this.startForeground(66, Utils.buildForegroundNotification(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }
        GlobalScope.launch { init() }
        return START_STICKY
    }

    //endregion

    //region Interacting

    fun killService(){
        deadAlgo()
    }

    private fun initVariables(){
        lockedAddress = Utils.getSharedPrefs(this).getString(PreferenceListener.Companion.PrefsConsts.bandAddress, "").toString()
        try{
            if (commandHandler.looper == null
                    && !commandHandler.isAlive) commandHandler.start()
        } catch (e: Exception) {
        } // todo magic?
        SelfPointer = this
        isFirstTime = true
        IsActive = true
        ci = CommandInterpreter.getInterpreter(this)
        ci.callback = CommandCallbacks.getCallback(this)
        prefs = Utils.getSharedPrefs(this)
        uartService = UartServiceMK2(this)
        ServiceRessurecter.startJob(this)
        getLastHRSync()
        getLastMainSync()
    }

    fun init() = runBlocking {
        synchronized(this::class) {
            if (!Utils.getSharedPrefs(this@Algorithm).contains(PreferenceListener.Companion.PrefsConsts.bandAddress)) {
                this@Algorithm.stopForeground(true)
                this@Algorithm.stopSelf()
                return@runBlocking
            }
            if (SelfPointer == null || IsActive) {
                initVariables()
                val service = Intent(this@Algorithm, NotificationService::class.java)
                if (!NotificationService.IsActive) {
                    Utils.serviceStartForegroundMultiAPI(service, this@Algorithm)
                }
                uartService.connectToDevice(this@Algorithm.lockedAddress)
                Handler(Looper.getMainLooper()).post { StatusCode.value = StatusCodes.Disconnected; }
            }
        }
    }


    fun enqueneData(sm: SimpleRecord){
        if (sm.Data == null) return
        if (sm.characteristic == ci.PowerServiceString ||
                sm.characteristic == ci.PowerDescriptor ||
                sm.characteristic == ci.PowerTXString ||
                sm.characteristic == ci.PowerTX2String) {
            savedBattery = sm.Data[0].toShort()
            CommandCallbacks.getCallback(this).batteryInfo(sm.Data[0].toInt())
        } else {
            //incomingMessages.add(sm)
            Handler(commandHandler.looper).post { ci.commandAction(sm.Data, UUID.fromString(sm.characteristic)) }
        }
    }

    fun sendData(Data: ByteArray){
        this.uartService.sendDataToRX(Data)
    }

    fun executeForceSync() {
//TODO        GlobalScope.launch(Dispatchers.IO) { database.initRepairsAndSync(database.writableDatabase) }
        ci.requestSettings()
        ci.requestBatteryStatus()
        ci.syncTime(Calendar.getInstance())
        ci.requestHRHistory(getLastHRSync())
        ci.requestSleepHistory(getLastSleepSync())
        ci.getMainInfoRequest()
        if (isFirstTime) forceSyncHR()
        //if (IsAlarmingTriggered && !IsFromActivity) alarmTriggerDecider(0)
    }

    //endregion

    //region Implementation of connection observer

    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        currentAlgoStatus.postValue(getString(R.string.status_disconnected))
        StatusCode.postValue(StatusCodes.Disconnected)
        reWipeTimers()
    }

    override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
        currentAlgoStatus.postValue(getString(R.string.status_disconnected))
        StatusCode.postValue(StatusCodes.Disconnected)
        reWipeTimers()
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        currentAlgoStatus.postValue(getString(R.string.connected_syncing))
        StatusCode.postValue(StatusCodes.GattReady)
        executeMainAlgo(); manualHRHack()
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        currentAlgoStatus.postValue(getString(R.string.connected_syncing))
        StatusCode.postValue(StatusCodes.GattConnected)
    }

    override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
        uartService.connectToDevice(this.lockedAddress)
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        currentAlgoStatus.postValue(getString(R.string.status_connecting))
        StatusCode.postValue(StatusCodes.GattConnecting)
    }

    //endregion

}

// TODO Integrate data sleep visualization
// TODO Integrate 3d party services
// TODO Battery health tracker
// TODO LM: Other settings (alarms)