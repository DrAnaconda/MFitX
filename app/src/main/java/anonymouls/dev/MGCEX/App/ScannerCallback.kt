package anonymouls.dev.MGCEX.App

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult

class ScannerCallback(private val ScannerActivity: ScanActivity) : ScanCallback() {

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
    }

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
