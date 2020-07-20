package anonymouls.dev.mgcex.app.main

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
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
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.ArrayList

// TODO Fix time selector for idiots
@ExperimentalStdlibApi
class SettingsActivity : AppCompatActivity() {
    private var packagesList: TableLayout? = null

    private var dataView = false
    private lateinit var commandController: CommandInterpreter
    private lateinit var prefs: SharedPreferences
    private val loader = CoroutineScope(Job())
    private val fakeLooper: HandlerThread = HandlerThread("FakeLooper")

    private var packList: ArrayList<View> = ArrayList()
    private var checker = 0

    //region Init Views

    init {
        fakeLooper.start()
    }

    private fun initHRMonitoringBlock() {
        if (!commandController.hRRealTimeControlSupport) {
            findViewById<LinearLayout>(R.id.HRMonitoringSettings).visibility = View.VISIBLE
            findViewById<Switch>(R.id.enableHRMonitoring).isChecked =
                    prefs.getBoolean(hrMonitoringEnabled, false)

            if (findViewById<Switch>(R.id.enableHRMonitoring).isChecked) {
                findViewById<LinearLayout>(R.id.HRMonitoringSettingsBlock).visibility = View.VISIBLE
                findViewById<EditText>(R.id.HRMonitoringInterval).setText(
                        prefs.getInt(hrMeasureInterval, 5).toString())
                findViewById<EditText>(R.id.HRMonitoringStart).setText(
                        prefs.getString(hrMeasureStart, "00:00").toString())
                findViewById<EditText>(R.id.HRMonitoringEnd).setText(
                        prefs.getString(hrMeasureEnd, "00:00").toString())
            } else {
                prefs.edit().remove(hrMeasureInterval).apply()
                prefs.edit().remove(hrMeasureStart).apply()
                prefs.edit().remove(hrMeasureEnd).apply()
                prefs.edit().remove(hrMonitoringEnabled).apply()
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

        initTextBox<Int>(R.id.HRMonitoringInterval, 5, hrMeasureInterval)
        initTextBox<String>(R.id.HRMonitoringStart, "00:00", hrMeasureStart)
        initTextBox<String>(R.id.HRMonitoringEnd, "00:00", hrMeasureEnd)

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

        if (!prefs.contains(bandAddress)) {
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
        findViewById<Switch>(R.id.wakelocksSwitch).isChecked = prefs.getBoolean(permitWakeLock, true)
    }

    private fun initViews() {
        if (!dataView) {
            initTextBoxes()
            initHRMonitoringBlock()
            dynamicContentInit()
            initSwitches()
            if (prefs.contains(bandAddress)) {
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

    private fun preload() {
        val reqIntent = Intent(Intent.ACTION_MAIN, null)
        reqIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val packList = packageManager.queryIntentActivities(reqIntent, 0)
        findViewById<View>(R.id.LoadPackListBtn).isEnabled = false
        loader.launch(Dispatchers.IO) {
            val jobs = ArrayList<Job>()
            for (Pack in packList) {
                val readable = DatabaseController.getDCObject(this@SettingsActivity).readableDatabase
                val writeble = DatabaseController.getDCObject(this@SettingsActivity).writableDatabase
                jobs.add(launch(Dispatchers.IO) {
                    addToTable(Pack.activityInfo.packageName,
                            NotifyFilterTable.isEnabled(Pack.activityInfo.packageName,
                                    readable), writeble)
                })
            }
            jobs.joinAll()
            this@SettingsActivity.runOnUiThread { findViewById<View>(R.id.LoadPackListBtn).isEnabled = true }
        }
    }

    private fun addToTable(Content: String, IsEnabled: Boolean, db: SQLiteDatabase) {
        try {
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
            GlobalScope.launch {
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
            }
            try {
                Info.text = packageManager.getApplicationLabel(packageManager.getApplicationInfo(Content, 0))
            } catch (ex: Exception) {
                Info.text = Content
                drop = true
            }
            Info.setPadding(5, 5, 5, 5)
            Info.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
            Info.textSize = 15f
            Info.gravity = Gravity.CENTER

            try {
                Handler(fakeLooper.looper).post { SW.isChecked = IsEnabled }
                SW.gravity = Gravity.END
                SW.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
                SW.setOnClickListener {
                    NotifyFilterTable.insertRecord(Content, (it as Switch).isChecked, db)
                }
            } catch (ex: Exception) {
                val test = ""
            }
            TR.addView(appIcon)
            TR.addView(Info)
            TR.addView(SW)
            if (!IsEnabled && drop) {
                GlobalScope.launch(Dispatchers.Default)
                { NotifyFilterTable.dropRecord(Content, DatabaseController.getDCObject(this@SettingsActivity).writableDatabase) }
                checker++
                return
            }
            TR.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT)
            packList.add(TR)
        } catch (ex: Exception) {
            val test = ""
        }
    }

    private fun parsePack() {
        if (packagesList != null && packagesList!!.childCount > 0) return
        for (v in packList) {
            packagesList?.addView(v)
        }
    }

    private fun loadPackagesBtn() {
        setContentView(R.layout.activity_data_without_calendartool)
        initViews()
        parsePack()
    }

    override fun onStart() {
        super.onStart()
        preload()
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

    private fun deAuthDevice(): Boolean {
        prefs.edit().remove(bandAddress).apply()
        prefs.edit().remove(bandIDConst).apply()
        Algorithm.IsActive = false
        DeviceControllerActivity.instance?.finish()
        setContentView(R.layout.activity_scan)
        finish()
        Algorithm.updateStatusCode(Algorithm.StatusCodes.Dead)
        val intent = Intent(baseContext, ScanActivity::class.java)
        startActivity(intent)
        return true
    }

    // endregion

    //region Android Logic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        commandController = CommandInterpreter.getInterpreter(this)
        prefs = Utils.getSharedPrefs(this)
        initViews()
        //preload()
    }

    fun onClickHandler(v: View) {
        val state = if (v is Switch) v.isChecked else false
        var end = false
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
            R.id.wakelocksSwitch -> {
                prefs.edit().putBoolean(permitWakeLock, state).apply()
                Algorithm.SelfPointer?.killWakeLock()
            }
            R.id.PhoneSwitch -> {
                prefs.edit().putBoolean(receiveCallsSetting, state).apply(); Utils.requestPermissionsAdvanced(this); }
            R.id.RestoreToDefaultsBtn -> sendResetCommand()
            R.id.EraseDataOnRDeviceBtn -> sendEraseDatabaseCommand()
            R.id.breakConnectionBtn -> end = deAuthDevice()
            R.id.analyticsOn -> prefs.edit().putBoolean(Analytics.HelpData, state).apply()
            R.id.ignoreLightSleepData -> prefs.edit().putBoolean(lightSleepIgnore, state).apply()
            R.id.enableHRMonitoring -> {
                prefs.edit()
                        .putBoolean(hrMonitoringEnabled, findViewById<Switch>(R.id.enableHRMonitoring).isChecked)
                        .apply()
                initHRMonitoringBlock()
            }
            R.id.HRMonitoringStart -> createTimePicker(hrMeasureStart, v as EditText)
            R.id.HRMonitoringEnd -> createTimePicker(hrMeasureEnd, v as EditText)
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
        if (!end)
            dynamicContentInit()
    }

    override fun onBackPressed() {
        if (!Algorithm.isNotifyServiceAlive(this)) {
            Algorithm.tryForceStartListener(this)
        } else {
            prefs.edit().putBoolean("NotificationGranted", true).apply()
        }
        if (dataView) {
            packagesList?.removeAllViews()
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
        const val permitWakeLock = "PWL"
        const val disconnectedMonitoring = "DMT"

        const val hrMonitoringEnabled = "HRMEn"
        const val hrMeasureInterval = "HRMI"
        const val hrMeasureStart = "HRMS"
        const val hrMeasureEnd = "HRMEnd"

    }
}
