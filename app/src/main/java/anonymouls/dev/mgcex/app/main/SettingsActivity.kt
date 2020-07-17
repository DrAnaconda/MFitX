package anonymouls.dev.mgcex.app.main

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.Algorithm
import anonymouls.dev.mgcex.app.backend.CommandInterpreter
import anonymouls.dev.mgcex.app.backend.NotificationService
import anonymouls.dev.mgcex.app.scanner.ScanActivity
import anonymouls.dev.mgcex.databaseProvider.DatabaseController
import anonymouls.dev.mgcex.databaseProvider.NotifyFilterTable
import anonymouls.dev.mgcex.util.Analytics
import anonymouls.dev.mgcex.util.Utils
import java.util.*

// TODO Fix time selector for idiots
class SettingsActivity : AppCompatActivity() {
    private var packagesList: TableLayout? = null

    private var dataView = false
    private var demoMode = !Utils.getSharedPrefs(this).contains(bandAddress)
    private lateinit var commandController: CommandInterpreter
    private lateinit var prefs: SharedPreferences

    //region Init Views

    private fun initHRMonitoringBlock() {
        if (!commandController.hRRealTimeControlSupport) {
            findViewById<LinearLayout>(R.id.HRMonitoringSettings).visibility = View.VISIBLE
            findViewById<Switch>(R.id.enableHRMonitoring).isChecked =
                    prefs.getBoolean(HRMonitoringSettings.hrMonitoringEnabled, false)

            if (findViewById<Switch>(R.id.enableHRMonitoring).isChecked) {
                findViewById<LinearLayout>(R.id.HRMonitoringSettingsBlock).visibility = View.VISIBLE
                findViewById<EditText>(R.id.HRMonitoringInterval).setText(
                        prefs.getInt(HRMonitoringSettings.hrMeasureInterval, 5).toString())
                findViewById<EditText>(R.id.HRMonitoringStart).setText(
                        prefs.getString(HRMonitoringSettings.hrMeasureStart, "00:00").toString())
                findViewById<EditText>(R.id.HRMonitoringEnd).setText(
                        prefs.getString(HRMonitoringSettings.hrMeasureEnd, "00:00").toString())
            } else {
                prefs.edit().remove(HRMonitoringSettings.hrMeasureInterval).apply()
                prefs.edit().remove(HRMonitoringSettings.hrMeasureStart).apply()
                prefs.edit().remove(HRMonitoringSettings.hrMeasureEnd).apply()
                prefs.edit().remove(HRMonitoringSettings.hrMonitoringEnabled).apply()
                findViewById<LinearLayout>(R.id.HRMonitoringSettingsBlock).visibility = View.GONE
            }
        }
    }

    private fun <T> initTextBox(id: Int, defaultValue: Any, settingName: String) {
        findViewById<EditText>(id).addTextChangedListener(createTextWatcher<T>(settingName, defaultValue))
        when (defaultValue) {
            is Float -> findViewById<EditText>(id).setText(prefs.getFloat(settingName, defaultValue).toString())
            is Int -> findViewById<EditText>(id).setText(prefs.getInt(settingName, defaultValue).toString())
            is String -> findViewById<EditText>(id).setText(prefs.getString(settingName, defaultValue).toString())
        }
    }

    private fun initTextBoxes() {
        initTextBox<Float>(R.id.stepSize, 0.66f, stepsSize)
        initTextBox<Int>(R.id.mainSyncInterval, 5, mainSyncMinutes)
        initTextBox<Int>(R.id.batteryThresholdEdit, 20, batteryThreshold)

        initTextBox<Int>(R.id.secondsRepeatsText, 5, secondsNotify)
        initTextBox<Int>(R.id.numberRepeatsTextBox, 3, repeatsNumbers)

        initTextBox<Int>(R.id.HRMonitoringInterval, 5, HRMonitoringSettings.hrMeasureInterval)
        initTextBox<String>(R.id.HRMonitoringStart, "00:00", HRMonitoringSettings.hrMeasureStart)
        initTextBox<String>(R.id.HRMonitoringEnd, "00:00", HRMonitoringSettings.hrMeasureEnd)

        initTextBox<Int>(R.id.stepsCount, 5000, targetSteps)
    }

    private fun dynamicContentInit() {
        if (commandController.stepsTargetSettingSupport && prefs.contains(bandAddress))
            findViewById<LinearLayout>(R.id.stepsTargetContainer).visibility = View.VISIBLE
        else
            findViewById<LinearLayout>(R.id.stepsTargetContainer).visibility = View.GONE

        if (commandController.sittingReminderSupport && prefs.contains(bandAddress))
            findViewById<Switch>(R.id.sittingReminderSwitch).visibility = View.VISIBLE
        else
            findViewById<Switch>(R.id.sittingReminderSwitch).visibility = View.GONE

        if (commandController.vibrationSupport && prefs.contains(bandAddress))
            findViewById<Switch>(R.id.vibrationSwitch).visibility = View.VISIBLE
        else
            findViewById<Switch>(R.id.vibrationSwitch).visibility = View.GONE

        if (prefs.getBoolean(batterySaverEnabled, true))
            findViewById<View>(R.id.batteryThresholdContainer).visibility = View.VISIBLE
        else
            findViewById<View>(R.id.batteryThresholdContainer).visibility = View.GONE

        if (demoMode) {
            findViewById<Switch>(R.id.NotificationsSwitch).visibility = View.GONE
            findViewById<Switch>(R.id.PhoneSwitch).visibility = View.GONE
            findViewById<Switch>(R.id.GyroSwitch).visibility = View.GONE
            findViewById<View>(R.id.LoadPackListBtn).visibility = View.GONE
        }
    }

    private fun initSwitches() {
        findViewById<Switch>(R.id.NotificationsSwitch).isChecked = prefs.getBoolean("NotificationGranted", false)
        findViewById<Switch>(R.id.NotificationsSwitch).isChecked = Algorithm.isNotifyServiceAlive(this)
        findViewById<Switch>(R.id.PhoneSwitch).isChecked = prefs.getBoolean(receiveCallsSetting, true)
        findViewById<Switch>(R.id.GyroSwitch).isChecked = prefs.getBoolean(illuminationSetting, false)
        findViewById<Switch>(R.id.vibrationSwitch).isChecked = prefs.getBoolean(vibrationSetting, false)
        findViewById<Switch>(R.id.sittingReminderSwitch).isChecked = prefs.getBoolean(longSittingSetting, false)
        findViewById<Switch>(R.id.batterySaverSwitch).isChecked = prefs.getBoolean(batterySaverEnabled, true)
    }

    private fun initViews() {
        if (!dataView) {
            initTextBoxes()
            initHRMonitoringBlock()
            dynamicContentInit()
            initSwitches()
            if (prefs.contains("IsConnected") && prefs.getBoolean("IsConnected", false)) {
                findViewById<TextView>(R.id.currentConnectionText).text = getString(R.string.current_connection) + prefs.getString(bandAddress, null)
                findViewById<Button>(R.id.breakConnectionBtn).visibility = View.VISIBLE
            } else {
                findViewById<TextView>(R.id.currentConnectionText).text = getString(R.string.connection_not_established)
                findViewById<Button>(R.id.breakConnectionBtn).visibility = View.GONE
            }
        } else {
            packagesList = findViewById(R.id.DataGrid)
            packagesList!!.isStretchAllColumns = true
        }
    }

    //endregion

    //region Utility

    private fun <T> createTextWatcher(param: String, datatype: Any): TextWatcher {
        return object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                if (p0.toString().isEmpty()) return
                when (datatype) {
                    is Float -> Utils.getSharedPrefs(this@SettingsActivity).edit().putFloat(param, p0.toString().replace(',', '.').toFloat()).apply()
                    is Int -> Utils.getSharedPrefs(this@SettingsActivity).edit().putInt(param, p0.toString().toInt()).apply()
                    is String -> Utils.getSharedPrefs(this@SettingsActivity).edit().putString(param, p0.toString()).apply()
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        }
    }

    private fun showNotConnectedErrorToast() {
        Toast.makeText(this, getString(R.string.connection_not_established), Toast.LENGTH_LONG).show()
    }

    private fun addToTable(Content: String, IsEnabled: Boolean) {
        var drop = false
        val SW = Switch(this)
        val TR = CustomTableRow(this, Content, SW)
        val Info = TextView(this)

        val appIcon = ImageView(this)
        appIcon.adjustViewBounds = true
        appIcon.minimumWidth = 64
        appIcon.minimumWidth = 64
        appIcon.maxWidth = 64
        appIcon.maxHeight = 64
        appIcon.layoutParams = TableRow.LayoutParams(64, 64)
        appIcon.scaleType = ImageView.ScaleType.FIT_CENTER
        try {
            val iconApp = packageManager.getApplicationIcon(Content)
            appIcon.setImageDrawable(iconApp)
        } catch (ex: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                appIcon.setImageDrawable(getDrawable(android.R.drawable.ic_menu_help))
            } else {
                appIcon.setImageResource(android.R.drawable.ic_menu_help)
            }
            drop = true
        }

        try {
            Info.text = packageManager.getApplicationLabel(packageManager.getApplicationInfo(Content, 0))
        } catch (ex: Exception) {
            Info.text = Content
            drop = true
        }
        Info.setPadding(5, 5, 5, 5)
        TableRow.LayoutParams()
        Info.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
        Info.textSize = 15f
        Info.gravity = Gravity.CENTER

        SW.isChecked = IsEnabled
        SW.gravity = Gravity.END
        SW.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)

        TR.addView(appIcon)
        TR.addView(Info)
        TR.addView(SW)
        if (!IsEnabled && drop) {
            NotifyFilterTable.dropRecord(Content, DatabaseController.getDCObject(this).writableDatabase)
            return
        }
        TR.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT)
        packagesList!!.addView(TR)
    }

    private fun loadPackagesBtn() {
        setContentView(R.layout.activity_data_without_calendartool)
        initViews()
        val reqIntent = Intent(Intent.ACTION_MAIN, null)
        reqIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val packList = packageManager.queryIntentActivities(reqIntent, 0)
        for (Pack in packList) {
            try {
                addToTable(Pack.activityInfo.packageName, NotifyFilterTable.isEnabled(Pack.activityInfo.packageName, DatabaseController.getDCObject(this).writableDatabase))
            } catch (Ex: Exception) {

            }

        }
    }

    private fun createTimePicker(param: String, textObject: EditText) {
        val targetTimeSet = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            var result = "${Utils.subIntegerConversionCheck(hourOfDay.toString())}:${Utils.subIntegerConversionCheck(minute.toString())}"
            textObject.setText(result)
            prefs.edit().putString(param, result).apply()
        }

        val timeHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeMinute = Calendar.getInstance().get(Calendar.MINUTE)
        val tp = TimePickerDialog(this, targetTimeSet, timeHour, timeMinute, true)
        tp.show()
    }

    private fun onCloseEvent() {
        if (commandController.stepsTargetSettingSupport)
            commandController.setTargetSteps(prefs.getInt(targetSteps, 5000))
    }

    //endregion

    //region Device Handling

    private fun sendEraseDatabaseCommand() {
        if (Algorithm.StatusCode.value!!.code >= Algorithm.StatusCodes.Connected.code) {
            commandController.eraseDatabase()
        } else {
            showNotConnectedErrorToast()
        }
    }

    private fun sendResetCommand() {
        if (Algorithm.StatusCode.value!!.code >= Algorithm.StatusCodes.Connected.code) {
            commandController.restoreToDefaults()
        } else {
            showNotConnectedErrorToast()
        }
    }

    private fun deAuthDevice() {
        val IsConnected = prefs.getBoolean("IsConnected", false)
        if (IsConnected) {
            prefs.edit().putBoolean("IsConnected", false).apply()
            prefs.edit().remove(bandIDConst).apply()
            Algorithm.IsActive = false
            DeviceControllerActivity.instance?.finish()
            setContentView(R.layout.activity_scan)
            finish()
            val intent = Intent(baseContext, ScanActivity::class.java)
            startActivity(intent)
        }
    }

    // endregion

    //region Android Logic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        commandController = CommandInterpreter.getInterpreter(this)
        prefs = Utils.getSharedPrefs(this)
        initViews()
    }

    fun onClickHandler(v: View) {
        val state = if (v is Switch) v.isChecked else false

        when (v.id) {
            R.id.LoadPackListBtn -> {
                dataView = true
                loadPackagesBtn()
                return
            }
            R.id.NotificationsSwitch -> {
                if (NotificationService.IsActive) {
                    Algorithm.tryForceStartListener(this)
                } else {
                    Utils.requestToBindNotifyService(this)
                }
                prefs.edit().putBoolean("NotificationGranted", state).apply()
            }
            R.id.GyroSwitch -> {
                commandController.setGyroAction(state)
                prefs.edit().putBoolean(illuminationSetting, state).apply()
            }
            R.id.PhoneSwitch -> {
                prefs.edit().putBoolean(receiveCallsSetting, state).apply(); Utils.requestPermissionsAdvanced(this); }
            R.id.RestoreToDefaultsBtn -> sendResetCommand()
            R.id.EraseDataOnRDeviceBtn -> sendEraseDatabaseCommand()
            R.id.breakConnectionBtn -> deAuthDevice()
            R.id.analyticsOn -> Utils.SharedPrefs?.edit()?.putBoolean(Analytics.HelpData, state)?.apply()
            R.id.ignoreLightSleepData -> prefs.edit().putBoolean(lightSleepIgnore, state).apply()
            R.id.enableHRMonitoring -> {
                prefs.edit()
                        .putBoolean(HRMonitoringSettings.hrMonitoringEnabled, findViewById<Switch>(R.id.enableHRMonitoring).isChecked)
                        .apply()
                initHRMonitoringBlock()
            }
            R.id.HRMonitoringStart -> createTimePicker(HRMonitoringSettings.hrMeasureStart, v as EditText)
            R.id.HRMonitoringEnd -> createTimePicker(HRMonitoringSettings.hrMeasureEnd, v as EditText)
            R.id.sittingReminderSwitch -> {
                prefs.edit().putBoolean(longSittingSetting, state).apply()
                commandController.setSittingReminder(state)
            }
            R.id.vibrationSwitch -> {
                prefs.edit().putBoolean(vibrationSetting, state).apply()
                commandController.setVibrationSetting(state)
            }
            R.id.batterySaverSwitch -> prefs.edit().putBoolean(batterySaverEnabled, state).apply()
        }
        dynamicContentInit()
    }

    override fun onBackPressed() {
        if (!Algorithm.isNotifyServiceAlive(this)) {
            Algorithm.tryForceStartListener(this)
        } else {
            prefs.edit().putBoolean("NotificationGranted", true).apply()
        }
        if (dataView) {
            for (i in 0 until packagesList!!.childCount) {
                val ctr = packagesList!!.getChildAt(i) as CustomTableRow
                if (ctr.IsEnabled.isChecked)
                    NotifyFilterTable.insertRecord(ctr.Package, ctr.IsEnabled.isChecked,
                            DatabaseController.getDCObject(this).writableDatabase)
                else
                    NotifyFilterTable.dropRecord(ctr.Package, DatabaseController.getDCObject(this).writableDatabase)
            }
            dataView = !dataView
            setContentView(R.layout.activity_settings)
            initViews()
        } else {
            onCloseEvent()
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    //endregion

    private inner class CustomTableRow(context: Context, var Package: String, var IsEnabled: Switch) : TableRow(context)

    companion object {
        const val illuminationSetting = "illuminationSetting"
        const val receiveCallsSetting = "ReceiveCalls"
        const val secondsNotify = "secondsRepeat"
        const val repeatsNumbers = "repeatsNumber"
        const val stepsSize = "Step_Size"
        const val lightSleepIgnore = "LightIgnore"
        const val bandIDConst = "BandID"
        const val mainSyncMinutes = "AutoSyncInterval"
        const val targetSteps = "TargetStepsSetting"
        const val longSittingSetting = "LongSittingReminder"
        const val vibrationSetting = "VibrationSetting"
        const val bandAddress = "BandAddress"
        const val batteryThreshold = "BST"
        const val batterySaverEnabled = "BSE"

        object HRMonitoringSettings {
            const val hrMonitoringEnabled = "HRMonitoringEnabled"
            const val hrMeasureInterval = "HRMeasureInterval"
            const val hrMeasureStart = "HRMonitoringStart"
            const val hrMeasureEnd = "HRMonitoringEnd"
        }
    }
}
