package anonymouls.dev.mgcex.app.backend

import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.PhyRequest
import java.util.*


class SimpleRecord(val characteristic: String, val Data: ByteArray?)

@ExperimentalStdlibApi
class UartServiceMK2(private val Algo: Algorithm) : BleManager(Algo) {

    //region UUIDs

    private val powerServiceUUID = UUID.fromString(Algo.ci.PowerServiceString)
    private val powerTXUUID = UUID.fromString(Algo.ci.PowerTX2String)

    private val uartServiceUUID = UUID.fromString(Algo.ci.UARTServiceUUIDString)
    private val rxCharUUID = UUID.fromString(Algo.ci.UARTRXUUIDString)
    private val txCharUUID = UUID.fromString(Algo.ci.UARTTXUUIDString)

    //endregion

    //region Characs

    private var powerChar: BluetoothGattCharacteristic? = null
    private var powerService: BluetoothGattService? = null

    private var uartService: BluetoothGattService? = null
    private var receiveChar: BluetoothGattCharacteristic? = null
    private var transmitChar: BluetoothGattCharacteristic? = null

    //endregion

    override fun getGattCallback(): BleManagerGattCallback {
        return GattCallback()
    }

    fun connectToDevice(address: String){
        val bManager = context.getSystemService(Service.BLUETOOTH_SERVICE) as BluetoothManager
        if (!bManager.adapter.isEnabled) bManager.adapter.enable()
        val device = bManager.adapter.getRemoteDevice(address)
        this.setConnectionObserver(Algo)
        waitIf { bManager.adapter.isEnabled }
        this.connect(device)
                .usePreferredPhy(PhyRequest.PHY_LE_2M_MASK)
                .timeout(5 * 60 * 1000)
                .retry(Int.MAX_VALUE / 2, 1000)
                .fail { _, status -> this.connectToDevice(address) }
                .enqueue()
    }

    fun sendDataToRX(Data: ByteArray){
        writeCharacteristic(receiveChar, Data)
                .enqueue()
    }

    private inner class GattCallback : BleManagerGattCallback() {
        public override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            powerService = gatt.getService(powerServiceUUID)
            powerChar = powerService?.getCharacteristic(powerTXUUID)
            uartService = gatt.getService(uartServiceUUID)
            receiveChar = uartService?.getCharacteristic(rxCharUUID)
            transmitChar = uartService?.getCharacteristic(txCharUUID)
            return receiveChar != null && transmitChar != null
        }

        override fun onDeviceDisconnected() {
            powerService = null; powerChar = null
            uartService = null; receiveChar = null; transmitChar = null
        }

        override fun initialize() {
            super.initialize()
            enableNotifications(receiveChar).before {
                setNotificationCallback(receiveChar).with { _, data ->
                    Algo.enqueneData(SimpleRecord(powerChar?.uuid.toString(), data.value))
                }
            }.enqueue()
            enableNotifications(powerChar).before {
                setNotificationCallback(powerChar).with { _, data ->
                    Algo.enqueneData(SimpleRecord(powerChar?.uuid.toString(), data.value))
                }
            }.enqueue()
            enableNotifications(transmitChar).before {
                setNotificationCallback(transmitChar).with { _, data ->
                    Algo.enqueneData(SimpleRecord(transmitChar?.uuid.toString(), data.value))
                }
            }.enqueue()
        }
    }
}