package anonymouls.dev.mgcex.app.main

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
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.Algorithm
import anonymouls.dev.mgcex.app.backend.CommandInterpreter
import anonymouls.dev.mgcex.app.backend.NotificationService
import anonymouls.dev.mgcex.databaseProvider.DatabaseController
import anonymouls.dev.mgcex.databaseProvider.NotifyFilterTable
import anonymouls.dev.mgcex.util.Analytics
import anonymouls.dev.mgcex.util.Utils
import okhttp3.internal.Util

class SettingsActivity : Activity() {
    private var packagesList: TableLayout? = null

    private var dataView = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        initViews()
    }


    private fun createTextWatcher(param: String, dataType: Int): TextWatcher {
        return object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                if (p0.toString().isEmpty()) return
                when (dataType) {
                    0 -> Utils.getSharedPrefs(this@SettingsActivity).edit().putFloat(param, p0.toString().replace(',', '.').toFloat()).apply()
                    1 -> Utils.getSharedPrefs(this@SettingsActivity).edit().putInt(param, p0.toString().toInt()).apply()
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        }
    }

    private fun initTextBoxes() {
        findViewById<EditText>(R.id.stepSize).addTextChangedListener(createTextWatcher(stepsSize, 0))
        findViewById<EditText>(R.id.stepSize).setText(Utils.getSharedPrefs(this).getFloat(stepsSize, 0.66f).toString().replace('.', ','))

        findViewById<EditText>(R.id.secondsRepeatsText).addTextChangedListener(createTextWatcher(secondsNotify, 1))
        findViewById<EditText>(R.id.secondsRepeatsText).setText(Utils.getSharedPrefs(this).getInt(secondsNotify, 5).toString())

        findViewById<EditText>(R.id.numberRepeatsTextBox).addTextChangedListener(createTextWatcher(repeatsNumbers, 1))
        findViewById<EditText>(R.id.numberRepeatsTextBox).setText(Utils.getSharedPrefs(this).getInt(repeatsNumbers, 3).toString())
    }

    private fun initViews() {
        if (!dataView) {
            initTextBoxes()

            findViewById<Switch>(R.id.NotificationsSwitch).isChecked = Utils.getSharedPrefs(this).getBoolean("NotificationGranted", false)
            findViewById<Switch>(R.id.NotificationsSwitch).isChecked = Algorithm.isNotifyServiceAlive(this)
            findViewById<Switch>(R.id.PhoneSwitch).isChecked = Utils.getSharedPrefs(this).getBoolean("ReceiveCalls", true)
            findViewById<Switch>(R.id.GyroSwitch).isChecked = Utils.getSharedPrefs(this).getBoolean("Illumination", false)
            if (Utils.getSharedPrefs(this).contains("IsConnected") && Utils.getSharedPrefs(this).getBoolean("IsConnected", false)) {
                findViewById<TextView>(R.id.currentConnectionText).text = getString(R.string.current_connection) + Utils.getSharedPrefs(this).getString("BandAddress", null)
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

    private fun showNotConnectedErrorToast() {
        Toast.makeText(this, getString(R.string.connection_not_established), Toast.LENGTH_LONG).show()
    }

    private fun sendEraseDatabaseCommand() {
        if (Algorithm.StatusCode.value!!.code >= 3) {
            Algorithm.postCommand(CommandInterpreter.EraseDatabase(), false)
        } else {
            showNotConnectedErrorToast()
        }
    }

    private fun sendResetCommand() {
        if (Algorithm.StatusCode.value!!.code >= 3) {
            Algorithm.postCommand(CommandInterpreter.RestoreToDefaults(), false)
        } else {
            showNotConnectedErrorToast()
        }
    }

    private fun deAuthDevice() {
        val IsConnected = Utils.getSharedPrefs(this).getBoolean("IsConnected", false)
        if (IsConnected) {
            Utils.getSharedPrefs(this).edit().putBoolean("IsConnected", false).apply()
            Algorithm.IsActive = false
            DeviceControllerActivity.instance!!.finish()
            setContentView(R.layout.activity_scan)
            finish()
        }
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
            appIcon.setImageDrawable(getDrawable(android.R.drawable.ic_menu_help))
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

    fun onClickHandler(v: View) {
        when (v.id) {
            R.id.LoadPackListBtn -> {
                dataView = true
                loadPackagesBtn()
            }
            R.id.NotificationsSwitch -> {
                if (NotificationService.IsActive) {
                    Algorithm.tryForceStartListener(this)
                } else {
                    Utils.requestToBindNotifyService(this)
                }
                Utils.getSharedPrefs(this).edit().putBoolean("NotificationGranted", true).apply()
            }
            R.id.GyroSwitch -> {
                CommandInterpreter.SetGyroAction(findViewById<Switch>(v.id).isChecked)
                Utils.getSharedPrefs(this).edit().putBoolean("Illumination", findViewById<Switch>(v.id).isChecked).apply()
            }
            R.id.PhoneSwitch -> Utils.getSharedPrefs(this).edit().putBoolean("ReceiveCalls", findViewById<Switch>(v.id).isChecked).apply()
            R.id.RestoreToDefaultsBtn -> sendResetCommand()
            R.id.EraseDataOnRDeviceBtn -> sendEraseDatabaseCommand()
            R.id.breakConnectionBtn -> deAuthDevice()
            R.id.analyticsOn -> Utils.SharedPrefs?.edit()?.putBoolean(Analytics.HelpData, findViewById<Switch>(R.id.analyticsOn).isChecked)?.apply()
            R.id.ignoreLightSleepData -> Utils.getSharedPrefs(this).edit().putBoolean(lightSleepIgnore, findViewById<Switch>(v.id).isChecked).apply()
        }
    }

    override fun onBackPressed() {
        if (!Algorithm.isNotifyServiceAlive(this)) {
            Algorithm.tryForceStartListener(this)
        } else {
            Utils.getSharedPrefs(this).edit().putBoolean("NotificationGranted", true).apply()
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
        const val lightSleepIgnore = "LightIgnore"
    }
}
