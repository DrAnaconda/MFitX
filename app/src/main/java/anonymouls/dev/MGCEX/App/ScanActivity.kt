package anonymouls.dev.MGCEX.App

import android.Manifest
import android.app.Activity
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.widget.ListView
import android.widget.Toast

import java.util.ArrayList

import anonymouls.dev.MGCEX.util.Utils

class ScanActivity : Activity() {
    private var BManager: BluetoothManager? = null
    private var mBTAdapter: BluetoothAdapter? = null
    lateinit var mDeviceAdapter: DeviceAdapter
    private var Prefs: SharedPreferences? = null
    private var mIsScanning: Boolean = false
    private var mScanner: BluetoothLeScanner? = null
    private val LECallback = ScannerCallback(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        Prefs = Utils.GetSharedPrefs(this)
        if (Prefs!!.contains("IsConnected") && Prefs!!.contains("BandAddress")) {
            if (Prefs!!.getBoolean("IsConnected", false)) {
                OpenDeviceControlActivity(this.baseContext, Prefs!!.getString("BandAddress", null))
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
        val itemId = item.itemId
        if (itemId == android.R.id.home) {
            return true
        } else if (itemId == R.id.action_scan) {
            startScan()
            return true
        } else if (itemId == R.id.action_stop) {
            stopScan()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun init() {
        Utils.RequestPermissions(this, Utils.UsedPerms)
        BManager = getSystemService(Service.BLUETOOTH_SERVICE) as BluetoothManager
        Utils.BluetoothEngaging(this)
        val deviceListView = findViewById<ListView>(R.id.list)
        mDeviceAdapter = DeviceAdapter(this, R.layout.listitem_device, ArrayList())
        deviceListView.adapter = mDeviceAdapter
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val item = mDeviceAdapter.getItem(position)
            if (item != null) {
                stopScan()

                OpenDeviceControlActivity(baseContext, item.address)
                this.finish()
            }
        }
    }

    private fun OpenDeviceControlActivity(view: Context, LockedAddress: String?) {
        val intent = Intent(view, DeviceControllerActivity::class.java)
        intent.putExtra(DeviceControllerActivity.ExtraDevice, LockedAddress)
        val PEditor = Prefs!!.edit()
        PEditor.putBoolean("IsConnected", true)
        PEditor.putString("BandAddress", LockedAddress)
        PEditor.apply()
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Utils.BluetoothEnableRequestCode -> if (resultCode == Activity.RESULT_OK)
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
        }
    }

    private fun startScan() {
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
}
