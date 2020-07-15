package anonymouls.dev.mgcex.app.scanner

import android.Manifest
import android.app.Activity
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.main.DeviceControllerActivity
import anonymouls.dev.mgcex.app.main.SettingsActivity
import anonymouls.dev.mgcex.util.Analytics
import anonymouls.dev.mgcex.util.Utils
import java.util.*

class ScanActivity : Activity() {
    private var BManager: BluetoothManager? = null
    private var mBTAdapter: BluetoothAdapter? = null
    lateinit var mDeviceAdapter: DeviceAdapter
    private var Prefs: SharedPreferences? = null
    private var mIsScanning: Boolean = false
    private var mScanner: BluetoothLeScanner? = null
    private lateinit var LECallback: ScannerCallback
    private lateinit var deprecatedScanner: DeprecatedScanner

//region default android

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data == null || resultCode == RESULT_CANCELED) return
        when (requestCode) {
            Utils.BluetoothEnableRequestCode -> {
                if (resultCode == RESULT_OK)
                    startScan()
                else
                    Toast.makeText(this, R.string.BluetoothRequiredMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == Utils.PermsRequest) {
            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.ACCESS_COARSE_LOCATION -> Utils.IsLocationAccess = Utils.IsLocationAccess and (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                    Manifest.permission.READ_EXTERNAL_STORAGE -> Utils.IsStorageAccess = Utils.IsStorageAccess and (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                    Manifest.permission.WRITE_EXTERNAL_STORAGE -> Utils.IsStorageAccess = Utils.IsStorageAccess and (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                }
            }
            if (grantResults.contains(PackageManager.PERMISSION_DENIED))
                Utils.requestPermissionsAdvanced(this)
        } else {
            if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
                Analytics.getInstance(this)?.sendCustomEvent(permissions[0], "rejected")
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        if (Utils.isDeviceSupported(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                LECallback = ScannerCallback(this)
            } else {
                deprecatedScanner = DeprecatedScanner(this)
            }
            Prefs = Utils.getSharedPrefs(this)
            if (Prefs!!.contains("IsConnected") && Prefs!!.contains(SettingsActivity.bandAddress)
                    && Prefs!!.contains(SettingsActivity.bandIDConst)) {
                if (Prefs!!.getBoolean("IsConnected", false)) {
                    openDeviceControlActivity(this.baseContext,
                            Prefs!!.getString(SettingsActivity.bandAddress, null),
                            Prefs!!.getString(SettingsActivity.bandIDConst, null))
                    this.finish()
                } else
                    init()
            } else
                init()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.scan_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (Utils.isDeviceSupported(this)) {
            if (mIsScanning) {
                menu.findItem(R.id.action_scan).isVisible = false
                menu.findItem(R.id.action_stop).isVisible = true
            } else {
                menu.findItem(R.id.action_scan).isVisible = true
                menu.findItem(R.id.action_stop).isVisible = false
            }
        } else {
            menu.findItem(R.id.action_stop).isVisible = false
            menu.findItem(R.id.action_scan).isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> return true
            R.id.action_scan -> {
                startScan(); return true
            }
            R.id.action_stop -> {
                stopScan(); return true
            }
            R.id.action_skip -> {
                this.openDeviceControlActivity(this, null, null); return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun init() {
        Utils.requestPermissionsDefault(this, Utils.UsedPerms)
        BManager = getSystemService(Service.BLUETOOTH_SERVICE) as BluetoothManager
        Utils.bluetoothEngaging(this)
        val deviceListView = findViewById<ListView>(R.id.list)
        mDeviceAdapter = DeviceAdapter(this, R.xml.listitem_device, ArrayList())
        deviceListView.adapter = mDeviceAdapter
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val item = mDeviceAdapter.getItem(position)
            if (item != null) {
                stopScan()
                openDeviceControlActivity(baseContext, item.address, item.name)
                this.finish()
            }
        }
    }

//endregion

//region Logic

    private fun openDeviceControlActivity(context: Context, LockedAddress: String?, DeviceName: String?) {
        val intent = Intent(context, DeviceControllerActivity::class.java)

        if (DeviceName != null) {
            Utils.getSharedPrefs(this).edit().putString(SettingsActivity.bandIDConst, DeviceName).apply()
        }
        if (LockedAddress != null) {
            intent.putExtra(DeviceControllerActivity.ExtraDevice, LockedAddress)
            val PEditor = Prefs!!.edit()
            PEditor.putBoolean("IsConnected", true)
            PEditor.putString(SettingsActivity.bandAddress, LockedAddress)
            PEditor.apply()
        } else Utils.getSharedPrefs(this).edit().remove(SettingsActivity.bandIDConst).apply()
        startActivity(intent)
    }

    private fun checkLocationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Utils.requestPermissionsAdvanced(this)
                return false
            }
            if (this.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Utils.requestPermissionsAdvanced(this)
                return false
            }
        }
        val service = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!service.isLocationEnabled) {
                DeviceControllerActivity.ViewDialog(getString(R.string.enable_location_services),
                        DeviceControllerActivity.ViewDialog.DialogTask.Intent,
                        Settings.ACTION_LOCATION_SOURCE_SETTINGS).showDialog(this)
                return false
            }
        } else {
            if (!service.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                DeviceControllerActivity.ViewDialog(getString(R.string.enable_location_services),
                        DeviceControllerActivity.ViewDialog.DialogTask.Intent,
                        Settings.ACTION_LOCATION_SOURCE_SETTINGS).showDialog(this)
                return false
            }
        }
        return true
    }

    private fun startScan() {
        if (!checkLocationEnabled()) return
        if (BManager != null && BManager!!.adapter.isEnabled) {
            findViewById<ProgressBar>(R.id.scanInProgress).visibility = View.VISIBLE
            mScanner = null
            mBTAdapter = BManager!!.adapter
            if (mScanner == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mScanner = mBTAdapter!!.bluetoothLeScanner
                    mScanner!!.startScan(LECallback)
                } else {
                    mBTAdapter!!.startLeScan(deprecatedScanner)
                }
            }
            mIsScanning = true
            invalidateOptionsMenu()
        } else {
            Utils.requestEnableBluetooth(this)
        }
    }

    private fun stopScan() {
        try {
            if (mBTAdapter != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mScanner!!.flushPendingScanResults(LECallback)
                    mScanner!!.stopScan(LECallback)
                }
            } else {
                mBTAdapter!!.stopLeScan(deprecatedScanner)
            }
        } catch (e: Exception) {
        }
        mIsScanning = false
        findViewById<ProgressBar>(R.id.scanInProgress).visibility = View.GONE
        invalidateOptionsMenu()
    }

//endregion
}
