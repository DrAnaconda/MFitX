package anonymouls.dev.mgcex.app.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult

@ExperimentalStdlibApi
class ScannerCallback(private val ScannerActivity: ScanActivity) : ScanCallback() {

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        ScannerActivity.runOnUiThread { ScannerActivity.mDeviceAdapter.update(result.device) }
    }

    override fun onBatchScanResults(results: List<ScanResult>) {
        super.onBatchScanResults(results)
        for (Result in results) {
            if (!ScannerActivity.mDeviceAdapter.mList.contains(Result.device)) {
                ScannerActivity.runOnUiThread { ScannerActivity.mDeviceAdapter.update(Result.device) }
            }
        }
    }
}

@ExperimentalStdlibApi
class DeprecatedScanner(private val ScannerActivity: ScanActivity) : BluetoothAdapter.LeScanCallback {
    override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
        ScannerActivity.runOnUiThread { ScannerActivity.mDeviceAdapter.update(device) }
    }
}