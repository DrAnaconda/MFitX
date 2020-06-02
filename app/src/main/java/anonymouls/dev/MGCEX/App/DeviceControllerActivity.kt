package anonymouls.dev.MGCEX.App

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.Window
import android.widget.*
import anonymouls.dev.MGCEX.util.AdsController
import anonymouls.dev.MGCEX.util.Analytics
import anonymouls.dev.MGCEX.util.TopExceptionHandler
import anonymouls.dev.MGCEX.util.Utils
import com.google.android.gms.ads.AdView
import com.google.firebase.analytics.FirebaseAnalytics
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess


class DeviceControllerActivity : Activity() {

    lateinit var stepsText: TextView
    lateinit var caloriesText: TextView
    lateinit var batteryStatusText: TextView
    lateinit var HRValue: TextView
    lateinit var statusText: TextView
    lateinit var realTimeSwitch: Switch
    lateinit var syncerObj : UIDataSyncer
    lateinit var exceptionHandler: TopExceptionHandler
    lateinit var adContainer: LinearLayout
    lateinit var ad: AdView

    private fun initViews() {
        batteryStatusText = findViewById(R.id.BatteryStatus)
        stepsText = findViewById(R.id.StepsValue)
        caloriesText = findViewById(R.id.CaloriesValue)
        HRValue = findViewById(R.id.HRValue)
        statusText = findViewById(R.id.StatusText)
        realTimeSwitch = findViewById(R.id.realtimeHRSync)
        adContainer = findViewById(R.id.deviceControllerAdsHolder)
        ad = findViewById(R.id.dcAD)
    }
    private fun initAlgo() {
        UartService.mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!((UartService.mBluetoothManager != null) and UartService.mBluetoothManager!!.adapter.isEnabled)) {
            statusText.text = getString(R.string.bluetooth_disabled_or_not_supported)
            StatusCode = -2
        }
        startService(Intent(this, Algorithm::class.java))
    }

    @SuppressLint("SetTextI18n", "SimpleDateFormat")
    fun reInit(){
        try {
            if (!IsActive)
                syncerObj = UIDataSyncer()
            syncerObj.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        } catch (Ex : IllegalStateException){
            // Ignore
        }

        if (Algorithm.IsAlarmingTriggered) {
            Algorithm.IsAlarmKilled = true
            Algorithm.IsAlarmWaiting = false
            Algorithm.postCommand(CommandInterpreter.StopLongAlarm(), false)
            val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mNotificationManager.cancel(21)
        }
        IsActive = true
        isFirstLaunch = true
    }

    //region default android

    override fun onLowMemory() {
        isFirstLaunch = true
        super.onLowMemory()
    }
    public override fun onStop() {
        super.onStop()
        IsActive = false
        isFirstLaunch = true
        syncerObj.cancel(true)
    }
    public override fun onResume() {
        super.onResume()
        reInit()
    }
    public override fun onStart() {
        super.onStart()
        reInit()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode){
            Utils.BluetoothEnableRequestCode -> {
                if (resultCode == RESULT_CANCELED)
                    statusText.text = getString(R.string.offline_mode)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.exceptionHandler = TopExceptionHandler(this.applicationContext)
        setContentView(R.layout.activity_device_controller)
        initViews()
        Analytics.getInstance(this)?.sendCustomEvent(FirebaseAnalytics.Event.APP_OPEN, null)
        AdsController.initAdBanned(ad, this)
        CommandCallbacks.SelfPointer = CommandCallbacks(this)
        CommandController = CommandInterpreter()
        CommandInterpreter.Callback = CommandCallbacks.SelfPointer
        if (Algorithm.NextSync != null)
            statusText.text = getString(R.string.status_connected_next_sync) + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Algorithm.NextSync!!.time)
        initAlgo()
        instance = this
    }
    public override fun onDestroy() {
        syncerObj.cancel(true)
        IsActive = false
        instance = null
        isFirstLaunch = true
        super.onDestroy()
    }
    override fun onBackPressed() {
        if (StatusCode != 3) {
            Algorithm.IsActive = false
            this.finish()
            exitProcess(0)
        } else {
            this.moveTaskToBack(true)
        }
    }

    // endregion

    private fun LaunchDataGraph(Data: String) {
        val newIntent = Intent(baseContext, DataView::class.java)
        newIntent.putExtra(DataView.ExtraViewMode, 1)
        newIntent.putExtra(DataView.ExtraDataToLoad, Data)
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(newIntent)
    }
    @SuppressLint("SetTextI18n")
    fun OnClickHandler(view: View) {
        val ID = view.id
        when (ID) {
            R.id.realtimeHRSync -> Algorithm.postCommand(CommandInterpreter.HRRealTimeControl(realTimeSwitch.isChecked), false)
            R.id.ExitBtnContainer -> {
                Algorithm.IsActive = false
                Algorithm.SelfPointer?.stopSelf()
                UartService.instance!!.disconnect()
                UartService.instance!!.stopSelf()
                stopService(Intent(this, Algorithm::class.java))
                stopService(Intent(this, UartService::class.java))
                finish()
                exitProcess(0)
            }
            R.id.SyncNowContainer -> {
                if (Algorithm.SelfPointer == null || Algorithm.SelfPointer!!.workInProgress) {
                    Toast.makeText(this, getString(R.string.wait_untill_complete), Toast.LENGTH_LONG).show()
                    return
                }
                findViewById<ProgressBar>(R.id.syncInProgress).visibility = View.VISIBLE
                statusText.text = statusText.text.toString() + "\n" + getString(R.string.manual_sync_status)
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        Algorithm.SelfPointer?.executeForceSync(true)
                    }
                }, 1500)
            }
            R.id.HRContainer -> LaunchDataGraph("HR")
            R.id.StepsContainer -> LaunchDataGraph("STEPS")
            R.id.CaloriesContainer -> LaunchDataGraph("CALORIES")
            R.id.SettingContainer -> {
                val Sets = Intent(baseContext, SettingsActivity::class.java)
                Sets.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(Sets)
            }
            R.id.AlarmContainer -> if (Algorithm.IsAlarmingTriggered) {
                Algorithm.IsAlarmingTriggered = false
                Algorithm.IsAlarmWaiting = false
                Algorithm.IsAlarmKilled = true
                view.background = getDrawable(R.drawable.custom_border)
                Algorithm.MainSyncPeriodSeconds = 310000
                Algorithm.postCommand(CommandInterpreter.StopLongAlarm(), false)
            } else {
                val alarmIntent = Intent(baseContext, AlarmActivity::class.java)
                alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(alarmIntent)
            }
            R.id.ReportContainer -> {
                val reportIntent = Intent(baseContext, MultitaskActivity::class.java)
                reportIntent.putExtra(MultitaskActivity.TaskTypeIntent, 0)
                startActivity(reportIntent)
            }
            R.id.InfoContainer -> ViewDialog().showDialog(this)
            R.id.SleepContainer -> {
                val reportIntent = Intent(baseContext, MultitaskActivity::class.java)
                reportIntent.putExtra(MultitaskActivity.TaskTypeIntent, 1)
                reportIntent.putExtra(MultitaskActivity.TextIntent, getString(R.string.sleep_not_ready))
                startActivity(reportIntent)
            }
            R.id.BatteryContainer -> Toast.makeText(this, getString(R.string.battery_health_not_ready), Toast.LENGTH_LONG).show()
            else -> Toast.makeText(this, "?????????????", Toast.LENGTH_SHORT).show()
        }
        if (realTimeSwitch.isChecked){
            realTimeSwitch.isChecked = false
            Algorithm.postCommand(CommandInterpreter.HRRealTimeControl(realTimeSwitch.isChecked), false)
        }
    }

    class ViewDialog {
        fun showDialog(activity: Activity?) {
            val dialog = Dialog(activity!!)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(false)
            dialog.setContentView(R.layout.about_dialog)
            val about_page = dialog.findViewById<TextView>(R.id.about_app_page)
            about_page.movementMethod = LinkMovementMethod.getInstance();
            val privacy_page = dialog.findViewById<TextView>(R.id.privacy_policy_page)
            privacy_page.movementMethod = LinkMovementMethod.getInstance();
            val dialogButton = dialog.findViewById<View>(R.id.infoDialogClose) as Button
            dialogButton.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }


    open inner class UIDataSyncer : AsyncTask<Void, Void, Void>() {

        private var LastSyncedSteps  : Int = -1
        private var LastSyncedCcals  : Int = -1
        private var LastSyncedHR     : Int = -1
        private var LastBatteryChrg  : Int = -1
        private var LastSyncedStatus : String = ""
        private var IsUpdateRequired = false
        private var lastWork = true

        override fun doInBackground(vararg params: Void?): Void {
            Thread.currentThread().name = "UISyncer"
            Thread.currentThread().priority = Thread.MIN_PRIORITY
            while (true){
                if (LastBatteryChrg != Algorithm.BatteryHolder){
                    LastBatteryChrg = Algorithm.BatteryHolder
                    IsUpdateRequired = true
                }
                if (LastSyncedSteps != Algorithm.LastStepsIncomed) {
                    LastSyncedSteps = Algorithm.LastStepsIncomed
                    IsUpdateRequired = true
                }
                if (LastSyncedHR != Algorithm.LastHearthRateIncomed){
                    LastSyncedHR = Algorithm.LastHearthRateIncomed
                    IsUpdateRequired = true
                }
                if (LastSyncedCcals != Algorithm.LastCcalsIncomed){
                    LastSyncedCcals = Algorithm.LastCcalsIncomed
                    IsUpdateRequired = true
                }
                if (LastSyncedStatus != Algorithm.LastStatus){
                    LastSyncedStatus = Algorithm.LastStatus
                    IsUpdateRequired = true
                }
                if (lastWork != Algorithm.SelfPointer?.workInProgress){
                    IsUpdateRequired = true
                    if (Algorithm.SelfPointer != null) lastWork = Algorithm.SelfPointer!!.workInProgress
                }
                if (IsUpdateRequired){
                    publishProgress()
                    IsUpdateRequired = false
                }
                Thread.sleep(2000)
            }
        }
        override fun onProgressUpdate(vararg values: Void?) {
            val progress: ProgressBar = findViewById(R.id.syncInProgress)
            if (Algorithm.SelfPointer!!.workInProgress) {
                progress.visibility = View.VISIBLE
            }else {
                progress.visibility = View.GONE
            }

            if (LastSyncedCcals!=-1) caloriesText.text      = LastSyncedCcals.toString()
            if (LastSyncedSteps!=-1) stepsText.text         = LastSyncedSteps.toString()
            if (LastSyncedHR!=-1)    HRValue.text       = LastSyncedHR.toString()
            if (LastSyncedStatus!="") statusText.text   = LastSyncedStatus
            if (LastBatteryChrg!=-1) batteryStatusText.text = LastBatteryChrg.toString()
            super.onProgressUpdate()
        }
    }

    companion object {

        var IsActive = false
        var isFirstLaunch: Boolean = true

        const val ExtraDevice = "EXTRA_BLE_DEVICE"
        var StatusCode : Int = 0
        var instance : DeviceControllerActivity? = null


        lateinit var CommandController: CommandInterpreter


        fun makeGattUpdateIntentFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction(Algorithm.StatusAction)
            intentFilter.addAction(UartService.ACTION_GATT_CONNECTED)
            intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED)
            intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED)
            intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE)
            intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART)
            intentFilter.addAction(NotificationService.NotifyAction)
            intentFilter.addAction("AlarmAction")
            return intentFilter
        }
    }
}


// TODO integrate adds
// TODO disable crash