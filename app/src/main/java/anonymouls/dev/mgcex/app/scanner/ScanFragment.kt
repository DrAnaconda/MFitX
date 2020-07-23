package anonymouls.dev.mgcex.app.scanner

import android.Manifest
import android.app.Activity
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
import android.view.*
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.ActivityCompat.invalidateOptionsMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.MultitaskListener
import anonymouls.dev.mgcex.app.main.ui.main.MainFragment
import anonymouls.dev.mgcex.util.Analytics
import anonymouls.dev.mgcex.util.DialogHelpers
import anonymouls.dev.mgcex.util.PreferenceListener
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

@ExperimentalStdlibApi
class ScanFragment : Fragment() {

    private lateinit var mBManager: BluetoothManager
    private lateinit var mScanner: BluetoothLeScanner
    private lateinit var mBTAdapter: BluetoothAdapter
    private var mIsScanning: Boolean = false
    private lateinit var LECallback: ScannerCallback
    private lateinit var prefs: SharedPreferences
    private lateinit var deprecatedScanner: DeprecatedScanner
    private lateinit var menu: Menu

    lateinit var mDeviceAdapter: DeviceAdapter

//region default android

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data == null || resultCode == Activity.RESULT_CANCELED) return
        when (requestCode) {
            Utils.BluetoothEnableRequestCode -> {
                if (resultCode == Activity.RESULT_OK)
                    startScan()
                else
                    Toast.makeText(requireContext(), R.string.BluetoothRequiredMsg, Toast.LENGTH_SHORT).show()
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
                Utils.requestPermissionsAdvanced(requireActivity())
        } else {
            if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
                Analytics.getInstance(requireContext())?.sendCustomEvent(permissions[0], "rejected")
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mBManager =  context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        if (Utils.isDeviceSupported(requireActivity())) {
            prefs = Utils.getSharedPrefs(requireActivity())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                LECallback = ScannerCallback(this)
            } else {
                deprecatedScanner = DeprecatedScanner(this)
            }
            Utils.requestPermissionsDefault(requireActivity(), Utils.UsedPerms)
            Utils.bluetoothEngaging(requireContext())
            prefs = Utils.getSharedPrefs(requireContext())
        }
    }

    override fun onStart() {
        super.onStart()
        init()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scan, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        this.requireActivity().menuInflater.inflate(R.menu.scan_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        this.menu = menu
        if (Utils.isDeviceSupported(requireActivity())) {
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
                this.switchToDeviceControl(null, null); return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDetach() {
        if (this::menu.isInitialized) menu.clear()
        super.onDetach()
    }

    private fun init() {
        val deviceListView = requireActivity().findViewById<ListView>(R.id.list)
        mDeviceAdapter = DeviceAdapter(requireContext(), R.xml.listitem_device, ArrayList())
        deviceListView.adapter = mDeviceAdapter
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val item = mDeviceAdapter.getItem(position)
            if (item != null) {
                stopScan()
                GlobalScope.launch(Dispatchers.Default) {  switchToDeviceControl(item.address, item.name)}
            }
        }
    }

//endregion

    //region Logic

    private fun switchToDeviceControl(LockedAddress: String?, DeviceName: String?) {
        if (DeviceName != null) {
            Utils.getSharedPrefs(this.requireContext()).edit().putString(PreferenceListener.Companion.PrefsConsts.bandIDConst, DeviceName).apply()
        }
        if (LockedAddress != null)
            Utils.getSharedPrefs(this.requireContext()).edit().putString(PreferenceListener.Companion.PrefsConsts.bandAddress, LockedAddress).apply()
        else
            Utils.getSharedPrefs(this.requireContext()).edit().remove(PreferenceListener.Companion.PrefsConsts.bandIDConst).apply()
        val frag = MainFragment()
        val transaction = this.requireActivity().supportFragmentManager.beginTransaction()
        val fm = this.requireActivity().supportFragmentManager
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        transaction.disallowAddToBackStack()
        transaction.replace(R.id.container, frag)
        MultitaskListener.ressurectService(requireContext())
        this.requireActivity().runOnUiThread { transaction.commitNow() }
    }

    private fun requestEnableLocationServices(){

    }

    private fun checkLocationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (this.requireContext().checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Utils.reqPermWithRationalize(Manifest.permission.ACCESS_BACKGROUND_LOCATION, requireActivity())
                return false
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.requireContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Utils.reqPermWithRationalize(Manifest.permission.ACCESS_COARSE_LOCATION, requireActivity())
                return false
            }
        }
        val service = this.requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!service.isLocationEnabled) {
                DialogHelpers.promptSimpleDialog(requireActivity(), getString(R.string.info),
                        getString(R.string.enable_location_services), android.R.drawable.ic_menu_info_details) { requestEnableLocationServices() }
                return false
            }
        } else {
            DialogHelpers.promptSimpleDialog(requireActivity(), getString(R.string.info),
                    getString(R.string.enable_location_services), android.R.drawable.ic_dialog_info) { requestEnableLocationServices() }
            return false
        }
        return true
    }

    private fun startScan() {
        if (!checkLocationEnabled()) return
        if (this::mBManager.isInitialized) {
            this.activity?.findViewById<ProgressBar>(R.id.scanInProgress)?.visibility = View.VISIBLE
            mBTAdapter = mBManager.adapter
            if (!mBTAdapter.isEnabled) mBTAdapter.enable()
            if (!this::mScanner.isInitialized) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mScanner = mBTAdapter.bluetoothLeScanner
                    mScanner.startScan(LECallback)
                } else {
                    mBTAdapter.startLeScan(deprecatedScanner)
                }
            }
            mIsScanning = true
            invalidateOptionsMenu(this.requireActivity())
        } else {
            Utils.requestEnableBluetooth(requireActivity())
        }
    }

    private fun stopScan() {
        try {
            if (this::mBTAdapter.isInitialized) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mScanner.flushPendingScanResults(LECallback)
                    mScanner.stopScan(LECallback)
                }
            } else {
                mBTAdapter.stopLeScan(deprecatedScanner)
            }
        } catch (e: Exception) {
        }
        mIsScanning = false
        this.activity?.findViewById<ProgressBar>(R.id.scanInProgress)?.visibility = View.GONE
        invalidateOptionsMenu(this.requireActivity())
    }

//endregion

    companion object {
        @JvmStatic
        fun newInstance() = ScanFragment()
    }
}