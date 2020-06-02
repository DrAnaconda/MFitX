package anonymouls.dev.MGCEX.App

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.*

import anonymouls.dev.MGCEX.DatabaseProvider.DatabaseController
import anonymouls.dev.MGCEX.DatabaseProvider.NotifyFilterTable
import anonymouls.dev.MGCEX.util.Utils

class SettingsActivity : Activity() {
    private var SharedPrefs: SharedPreferences? = null

    private var MinHRBox: EditText? = null
    private var MaxHRBox: EditText? = null
    private var AvgHRBox: EditText? = null
    private var stepSizeTextBox:    EditText?  = null
    private var secondsRepeatBox:   EditText?  = null
    private var repeatsNumberBox:   EditText?  = null

    private var ReceiveNotificationBox: Switch? = null
    private var ReceivePhoneCallBox: Switch? = null
    private var EnableAutoIllumination: Switch? = null
    private val LoadPackages: Button? = null
    private var PackagesList: TableLayout? = null
    private var currentConnectionBox: TextView? = null
    private var breakConnectionBtn: Button? = null

    private var DataView = false
    private lateinit var defaultTextListener: TextWatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        SharedPrefs = Utils.GetSharedPrefs(this)
        InitViews()
    }


    private fun createTextWatcher(param: String, dataType: Int): TextWatcher{
        return object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                if (p0.toString().isEmpty()) return
                when(dataType) {
                    0-> SharedPrefs!!.edit().putFloat(param, p0.toString().replace(',', '.').toFloat()).apply()
                    1-> SharedPrefs!!.edit().putInt(param, p0.toString().toInt()).apply()
                }
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {           }
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {            }
        }
    }
    private fun initTextBoxes(){
        stepSizeTextBox = findViewById(R.id.stepSize)
        stepSizeTextBox!!.addTextChangedListener(createTextWatcher(stepsSize, 0))
        stepSizeTextBox!!.setText(SharedPrefs!!.getFloat(stepsSize, 0.66f).toString().replace('.',','))

        secondsRepeatBox = findViewById(R.id.secondsRepeatsText)
        secondsRepeatBox!!.addTextChangedListener(createTextWatcher(secondsNotify, 1))
        secondsRepeatBox!!.setText(SharedPrefs!!.getInt(secondsNotify, 5).toString())

        repeatsNumberBox = findViewById(R.id.numberRepeatsTextBox)
        repeatsNumberBox!!.addTextChangedListener(createTextWatcher(repeatsNumbers, 1))
        repeatsNumberBox!!.setText(SharedPrefs!!.getInt(repeatsNumbers, 3).toString())
    }
    private fun InitViews() = if (!DataView) {
        ReceivePhoneCallBox = findViewById(R.id.PhoneSwitch)
        EnableAutoIllumination = findViewById(R.id.GyroSwitch)
        ReceiveNotificationBox = findViewById(R.id.NotificationsSwitch)
        currentConnectionBox = findViewById(R.id.currentConnectionText)
        breakConnectionBtn = findViewById(R.id.breakConnectionBtn)

        initTextBoxes()

        ReceiveNotificationBox!!.isChecked = SharedPrefs!!.getBoolean("NotificationGranted", false)
        ReceiveNotificationBox!!.isChecked = Algorithm.isNotifyServiceAlive(this)
        ReceivePhoneCallBox!!.isChecked = SharedPrefs!!.getBoolean("ReceiveCalls", true)
        EnableAutoIllumination!!.isChecked = SharedPrefs!!.getBoolean("Illumination", false)
        if (SharedPrefs!!.contains("IsConnected") && SharedPrefs!!.getBoolean("IsConnected", false)) {
            currentConnectionBox!!.text = "Current connection : " + SharedPrefs!!.getString("BandAddress", null)
            breakConnectionBtn!!.visibility = View.VISIBLE;
        } else {
            currentConnectionBox!!.text = getString(R.string.connection_not_established)
            breakConnectionBtn!!.visibility = View.GONE
        }
    } else{
        PackagesList = findViewById(R.id.DataGrid)
        PackagesList!!.isStretchAllColumns = true
    }

    private fun showNotConnectedErrorToast() {
        Toast.makeText(this, "Connection to device is not established.", Toast.LENGTH_LONG).show()
    }

    private fun SendEraseDatabaseCommand() {
        if (DeviceControllerActivity.StatusCode >= 3) {
            Algorithm.postCommand(CommandInterpreter.EraseDatabase(), false)
        } else {
            showNotConnectedErrorToast()
        }
    }
    private fun SendResetCommand() {
        if (DeviceControllerActivity.StatusCode >= 3) {
            Algorithm.postCommand(CommandInterpreter.RestoreToDefaults(), false)
        } else {
            showNotConnectedErrorToast()
        }
    }
    private fun DeAuthDevice() {
        val IsConnected = SharedPrefs!!.getBoolean("IsConnected", false)
        if (IsConnected) {
            SharedPrefs!!.edit().putBoolean("IsConnected", false).apply()
            Algorithm.IsActive = false
            DeviceControllerActivity.instance!!.finish()
            setContentView(R.layout.activity_scan)
            finish()
        }
    }
    private fun AddToTable(Content: String, IsEnabled: Int) {
        var drop: Boolean = false
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
        }catch (ex: Exception){
            appIcon.setImageDrawable(getDrawable(android.R.drawable.ic_menu_help))
            drop = true
        }

        try {
            Info.text = packageManager.getApplicationLabel(packageManager.getApplicationInfo(Content, 0))
        }catch (ex: Exception){
            Info.text = Content
            drop  = true
        }
        Info.setPadding(5, 5, 5, 5)
        TableRow.LayoutParams()
        Info.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
        Info.textSize = 15f
        Info.gravity = Gravity.CENTER

        SW.isChecked = IsEnabled == 1
        SW.gravity = Gravity.RIGHT
        SW.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)

        TR.addView(appIcon)
        TR.addView(Info)
        TR.addView(SW)
        if (IsEnabled == 0 && drop) {
            NotifyFilterTable.dropRecord(Content, DatabaseController.getDCObject(this).currentDataBase!!)
            return
        }
        TR.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT)
        PackagesList!!.addView(TR)
    }
    private fun LoadPackagesBtn() {
        setContentView(R.layout.activity_data_without_calendartool)
        InitViews()
        val I = Intent(Intent.ACTION_MAIN, null)
        I.addCategory(Intent.CATEGORY_LAUNCHER)
        val PackList = packageManager.queryIntentActivities(I, 0)
        val Old = NotifyFilterTable.ExtractRecords(DatabaseController.getDCObject(this).currentDataBase!!)
        if (Old != null && Old.count > 0) {
            do {
                AddToTable(Old.getString(0), Old.getInt(1))
            } while (Old.moveToNext())
        }
        for (Pack in PackList) {
            try {
                NotifyFilterTable.InsertRecord(Pack.activityInfo.packageName,
                        false, DatabaseController.getDCObject(this).currentDataBase!!)
            } catch (Ex: Exception) {

            }

        }
    }

    fun OnClickHandler(v: View) {
        when (v.id) {
            R.id.LoadPackListBtn -> {
                DataView = true
                LoadPackagesBtn()
            }
            R.id.NotificationsSwitch -> {
                Utils.RequestToBindNotifyService(this)
                if (!Algorithm.isNotifyServiceAlive(this)) {
                    Algorithm.tryForceStartListener(this)
                } else {
                    SharedPrefs!!.edit().putBoolean("NotificationGranted", true).apply()
                }
            }
            R.id.GyroSwitch -> {
                CommandInterpreter.SetGyroAction(EnableAutoIllumination!!.isChecked)
                SharedPrefs!!.edit().putBoolean("Illumination", EnableAutoIllumination!!.isChecked).apply()
            }
            R.id.PhoneSwitch -> SharedPrefs!!.edit().putBoolean("ReceiveCalls", ReceivePhoneCallBox!!.isChecked).apply()
            R.id.RestoreToDefaultsBtn  -> SendResetCommand()
            R.id.EraseDataOnRDeviceBtn -> SendEraseDatabaseCommand()
            R.id.breakConnectionBtn -> DeAuthDevice()
        }
    }
    override fun onBackPressed() {
        if (!Algorithm.isNotifyServiceAlive(this)) {
            Algorithm.tryForceStartListener(this)
        } else {
            SharedPrefs!!.edit().putBoolean("NotificationGranted", true).apply()
        }
        if (DataView) {
            for (i in 0 until PackagesList!!.childCount) {
                val CTR = PackagesList!!.getChildAt(i) as CustomTableRow
                NotifyFilterTable.UpdateRecord(CTR.Package, CTR.IsEnabled.isChecked,
                        DatabaseController.getDCObject(this).currentDataBase!!)
            }
            DataView = !DataView
            setContentView(R.layout.activity_settings)
            InitViews()
        } else {
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }


    private inner class CustomTableRow(context: Context, var Package: String, var IsEnabled: Switch) : TableRow(context)

    companion object {
        const val secondsNotify = "secondsRepeat"
        const val repeatsNumbers = "repeatsNumber"
        const val stepsSize = "Step_Size"
    }
}
