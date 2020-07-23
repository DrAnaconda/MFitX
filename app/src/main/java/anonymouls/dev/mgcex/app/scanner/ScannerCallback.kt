package anonymouls.dev.mgcex.app.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@ExperimentalStdlibApi
class ScannerCallback(private val ScannerActivity: ScanFragment) : ScanCallback() {

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        ScannerActivity.activity?.runOnUiThread { ScannerActivity.mDeviceAdapter.update(result.device) }
    }

    override fun onBatchScanResults(results: List<ScanResult>) {
        super.onBatchScanResults(results)
        for (Result in results) {
            if (!ScannerActivity.mDeviceAdapter.mList.contains(Result.device)) {
                ScannerActivity.activity?.runOnUiThread { ScannerActivity.mDeviceAdapter.update(Result.device) }
            }
        }
    }
}

@ExperimentalStdlibApi
class DeprecatedScanner(private val ScannerActivity: ScanFragment) : BluetoothAdapter.LeScanCallback {
    override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
        ScannerActivity.activity?.runOnUiThread { ScannerActivity.mDeviceAdapter.update(device) }
    }
}