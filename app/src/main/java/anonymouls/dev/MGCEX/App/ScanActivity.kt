package anonymouls.dev.MGCEX.App

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
import android.view.Menu
import android.view.MenuItem
import android.widget.ListView
import android.widget.Toast
import anonymouls.dev.MGCEX.util.Analytics
import anonymouls.dev.MGCEX.util.Utils
import java.util.*

class ScanActivity : Activity() {
    private var BManager: BluetoothManager? = null
    private var mBTAdapter: BluetoothAdapter? = null
    lateinit var mDeviceAdapter: DeviceAdapter
    private var Prefs: SharedPreferences? = null
    private var mIsScanning: Boolean = false
    private var mScanner: BluetoothLeScanner? = null
    private val LECallback = ScannerCallback(this)

//region default android

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Utils.BluetoothEnableRequestCode -> if (resultCode == RESULT_OK)
                startScan()
            else
                Toast.makeText(this, R.string.BluetoothRequiredMsg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        Prefs = Utils.GetSharedPrefs(this)
        if (Prefs!!.contains("IsConnected") && Prefs!!.contains("BandAddress")) {
            if (Prefs!!.getBoolean("IsConnected", false)) {
                openDeviceControlActivity(this.baseContext, Prefs!!.getString("BandAddress", null))
                this.finish()
            } else
                init()
        } else
            init()
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
        if (mIsScanning) {
            menu.findItem(R.id.action_scan).isVisible = false
            menu.findItem(R.id.action_stop).isVisible = true
        } else {
            menu.findItem(R.id.action_scan).isVisible = true
            menu.findItem(R.id.action_stop).isVisible = false
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
                this.openDeviceControlActivity(this, null); return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    private fun init() {
        Utils.requestPermissionsDefault(this, Utils.UsedPerms)
        BManager = getSystemService(Service.BLUETOOTH_SERVICE) as BluetoothManager
        Utils.BluetoothEngaging(this)
        val deviceListView = findViewById<ListView>(R.id.list)
        mDeviceAdapter = DeviceAdapter(this, R.layout.listitem_device, ArrayList())
        deviceListView.adapter = mDeviceAdapter
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val item = mDeviceAdapter.getItem(position)
            if (item != null) {
                stopScan()
                openDeviceControlActivity(baseContext, item.address)
                this.finish()
            }
        }
    }

//endregion

//region Logic

    private fun openDeviceControlActivity(view: Context, LockedAddress: String?) {
        val intent = Intent(view, DeviceControllerActivity::class.java)
        if (LockedAddress != null) {
            intent.putExtra(DeviceControllerActivity.ExtraDevice, LockedAddress)
            val PEditor = Prefs!!.edit()
            PEditor.putBoolean("IsConnected", true)
            PEditor.putString("BandAddress", LockedAddress)
            PEditor.apply()
        }
        startActivity(intent)
    }

    private fun checkLocationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Utils.requestPermissionsAdvanced(this)
                return false
            }
        }
        val service = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!service.isLocationEnabled) {
            DeviceControllerActivity.ViewDialog(getString(R.string.enable_location_services),
                    DeviceControllerActivity.ViewDialog.DialogTask.Intent,
                    android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).showDialog(this)
        }
        return true
    }
    private fun startScan() {
        if (!checkLocationEnabled()) return

        if (BManager != null && BManager!!.adapter.isEnabled) {
            mBTAdapter = BManager!!.adapter
            if (mScanner == null) mScanner = mBTAdapter!!.bluetoothLeScanner
            mScanner!!.startScan(LECallback)
            mIsScanning = true
            invalidateOptionsMenu()
        } else {
            Utils.RequestEnableBluetooth(this)
        }
    }
    private fun stopScan() {
        if (mBTAdapter != null) {
            mScanner!!.stopScan(LECallback)
        }
        mIsScanning = false
        setProgressBarIndeterminateVisibility(false) // TODO create custom
        invalidateOptionsMenu()
    }

//endregion
}
