package anonymouls.dev.mgcex.app.main

import android.app.Activity
import android.app.Dialog
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import anonymouls.dev.mgcex.app.AlarmActivity
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.*
import anonymouls.dev.mgcex.app.data.DataView
import anonymouls.dev.mgcex.app.data.MultitaskActivity
import anonymouls.dev.mgcex.app.databinding.ActivityDeviceControllerBinding
import anonymouls.dev.mgcex.databaseProvider.DatabaseController
import anonymouls.dev.mgcex.util.AdsController
import anonymouls.dev.mgcex.util.Analytics
import anonymouls.dev.mgcex.util.Utils
import com.google.firebase.analytics.FirebaseAnalytics
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess


class DeviceControllerActivity : AppCompatActivity() {

    private var demoMode = false

    private val customViewModel by lazy {
        ViewModelProviders.of(this, MyViewModelFactory(this)).get(DeviceControllerViewModel::class.java)
    }

    var binding: ActivityDeviceControllerBinding? = null

    private fun initAlgo() {
        UartService.mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!((UartService.mBluetoothManager != null) and UartService.mBluetoothManager!!.adapter.isEnabled)) {
            Algorithm.StatusCode.postValue(Algorithm.StatusCodes.BluetoothDisabled)
        }
        startService(Intent(this, Algorithm::class.java))
    }

    private fun initBindings() {
        val binding: ActivityDeviceControllerBinding = DataBindingUtil.setContentView(this, R.layout.activity_device_controller)
        binding.lifecycleOwner = this
        binding.viewmodel = customViewModel
        AdsController.initAdBanned(binding.dcAD, this)
        this.binding = binding
    }

    private fun reInit() {
        if (demoMode) return

        if (Algorithm.IsAlarmingTriggered) {
            Algorithm.IsAlarmKilled = true
            Algorithm.IsAlarmWaiting = false
            Algorithm.postCommand(CommandInterpreter.StopLongAlarm(), false)
            val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mNotificationManager.cancel(21)
        }
        DeviceControllerViewModel.instance?.reInit()
    }

    //region default android

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == Utils.PermsAdvancedRequest) {
            if (permissions.contains(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                DatabaseController.DCObject?.migrateToExternal(this)
            }
            if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
                Analytics.getInstance(this)?.sendCustomEvent(permissions[0], "rejected")
            }
        } else {
            var shouldRetry = false
            for (x in grantResults.indices) {
                if (grantResults[x] == PackageManager.PERMISSION_DENIED) {
                    Analytics.getInstance(this)?.sendCustomEvent(permissions[x], "denied")
                    shouldRetry = true
                }
            }
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (shouldRetry) Utils.requestPermissionsAdvanced(this)
        }
    }

    public override fun onStop() {
        super.onStop()
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
        when (requestCode) {
            Utils.BluetoothEnableRequestCode -> {
                if (resultCode == RESULT_CANCELED)
                    DeviceControllerViewModel.instance?._currentStatus?.postValue(getString(R.string.offline_mode))
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.requestPermissionsDefault(this, Utils.UsedPerms)
        //setContentView(R.layout.activity_device_controller)
        initBindings()
        Analytics.getInstance(this)?.sendCustomEvent(FirebaseAnalytics.Event.APP_OPEN, null)
//        AdsController.initAdBanned(ad, this)TODO
        if (Utils.isDeviceSupported(this)) {
            if (!Utils.getSharedPrefs(this).contains("BandAddress")) {
                DeviceControllerViewModel.instance?._currentStatus?.postValue(getString(R.string.demo_mode))
                findViewById<ProgressBar>(R.id.syncInProgress).visibility = View.GONE
                demoMode = true
                return
            }
            CommandCallbacks.SelfPointer = CommandCallbacks(this)
            CommandController = CommandInterpreter()
            CommandInterpreter.Callback = CommandCallbacks.SelfPointer
            if (Algorithm.NextSync != null)
                DeviceControllerViewModel.instance?._currentStatus?.postValue(getString(R.string.status_connected_next_sync) + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Algorithm.NextSync!!.time))
            initAlgo()
        } else DeviceControllerViewModel.instance?._currentStatus?.postValue(getString(R.string.demo_mode))
        instance = this
    }

    public override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (Algorithm.StatusCode.value!!.code < 3) {
            Algorithm.IsActive = false
            this.finish()
            exitProcess(0)
        } else {
            this.moveTaskToBack(true)
        }
    }

    // endregion

    private fun launchDataGraph(Data: String) {
        val newIntent = Intent(baseContext, DataView::class.java)
        newIntent.putExtra(DataView.ExtraViewMode, 1)
        newIntent.putExtra(DataView.ExtraDataToLoad, Data)
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(newIntent)
    }

    fun onClickHandler(view: View) {
        val ID = view.id
        when (ID) {
            R.id.realtimeHRSync -> Algorithm.postCommand(CommandInterpreter.HRRealTimeControl((view as Switch).isChecked), false)
            R.id.ExitBtnContainer -> {
                Algorithm.IsActive = false
                Algorithm.SelfPointer?.stopSelf()
                UartService.instance?.disconnect()
                UartService.instance?.stopSelf()
                stopService(Intent(this, Algorithm::class.java))
                stopService(Intent(this, UartService::class.java))
                finish()
                exitProcess(0)
            }
            R.id.SyncNowContainer -> {
                if (DeviceControllerViewModel.instance == null || DeviceControllerViewModel.instance?.workInProgress == null
                        || DeviceControllerViewModel.instance?.workInProgress!!.value == View.VISIBLE) {
                    Toast.makeText(this, getString(R.string.wait_untill_complete), Toast.LENGTH_LONG).show()
                } else {
                    Algorithm.SelfPointer?.thread?.interrupt()
                }
            }
            R.id.HRContainer -> launchDataGraph("HR")
            R.id.StepsContainer -> launchDataGraph("STEPS")
            R.id.CaloriesContainer -> launchDataGraph("CALORIES")
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
            R.id.InfoContainer -> ViewDialog(getString(R.string.info_text), ViewDialog.DialogTask.About).showDialog(this)
            R.id.SleepContainer -> {
                val reportIntent = Intent(baseContext, MultitaskActivity::class.java)
                reportIntent.putExtra(MultitaskActivity.TaskTypeIntent, 1)
                reportIntent.putExtra(MultitaskActivity.TextIntent, getString(R.string.sleep_not_ready))
                startActivity(reportIntent)
            }
            R.id.BatteryContainer -> Toast.makeText(this, getString(R.string.battery_health_not_ready), Toast.LENGTH_LONG).show()
        }
    }

    class ViewDialog(private val message: String, private val task: DialogTask, private val param: String? = null) {

        enum class DialogTask { About, Permission, Intent }

        companion object {
            var LastSelectedScaling = DataView.Scalings.Day
            var LastDialogLink: Dialog? = null
        }

        fun showDialog(activity: Activity) {
            val dialog = Dialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(false)
            dialog.setContentView(R.layout.about_dialog)

            val infoText = dialog.findViewById<TextView>(R.id.infoText)
            infoText.text = message

            val aboutPage = dialog.findViewById<TextView>(R.id.about_app_page)
            aboutPage.movementMethod = LinkMovementMethod.getInstance()
            val privacyPage = dialog.findViewById<TextView>(R.id.privacy_policy_page)
            val contact = dialog.findViewById<TextView>(R.id.contactMail)
            privacyPage.movementMethod = LinkMovementMethod.getInstance()

            when (task) {
                DialogTask.About -> {
                }
                else -> {
                    privacyPage.visibility = View.GONE
                    aboutPage.visibility = View.GONE
                    contact.visibility = View.GONE
                }
            }

            val dialogButton = dialog.findViewById<View>(R.id.infoDialogClose) as Button
            dialogButton.setOnClickListener {
                dialog.dismiss()
                when (task) {
                    DialogTask.Permission -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            activity.requestPermissions(arrayOf(param), Utils.PermsAdvancedRequest)
                        }
                    }
                    DialogTask.Intent -> {
                        if (param != null && param.isNotEmpty())
                            activity.startActivity(Intent(param))
                    }
                    else -> {
                    }
                }

            }
            dialog.show()
            LastDialogLink = dialog
        }

        fun showSelectorDialog(activity: Activity, confirmAction: View.OnClickListener) {
            LastSelectedScaling = DataView.Scalings.Day
            val dialog = Dialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(false)
            dialog.setContentView(R.layout.selector_dialog)

            val dismissBtn = dialog.findViewById<View>(R.id.dialogDismiss)
            dismissBtn.setOnClickListener { dialog.dismiss() }

            val confirmBtn = dialog.findViewById<View>(R.id.dialogConfirm)
            confirmBtn.setOnClickListener {
                dialog.dismiss()
                confirmAction.onClick(null)
            }
            dialog.show()
            LastDialogLink = dialog
        }
    }

    companion object {

        const val ExtraDevice = "EXTRA_BLE_DEVICE"
        var instance: DeviceControllerActivity? = null


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