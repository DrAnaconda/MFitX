package anonymouls.dev.mgcex.app.backend

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.*

class UartService(private val context: Context) {

    private var mBluetoothAdapter: BluetoothAdapter =
            (context.getSystemService(Service.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var mBluetoothDeviceAddress: String? = null
    private var discoveringPending = false

    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                gatt.discoverServices()
                if (Algorithm.StatusCode.value!!.code < Algorithm.StatusCodes.GattConnected.code)
                    Algorithm.updateStatusCode(Algorithm.StatusCodes.GattConnected)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mBluetoothGatt = null
                Algorithm.updateStatusCode(Algorithm.StatusCodes.Disconnected)
                Algorithm.SelfPointer?.thread?.interrupt()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
                instance?.enableTXNotification(RX_SERVICE_UUID, TX_CHAR_UUID, TXServiceDesctiptor)
                instance?.enableTXNotification(PowerServiceUUID, PowerTXUUID, PowerDescriptor)
                Algorithm.updateStatusCode(Algorithm.StatusCodes.GattReady)
            } else {
                gatt.discoverServices()
                Algorithm.updateStatusCode(Algorithm.StatusCodes.GattConnected)
                Algorithm.SelfPointer?.thread?.interrupt()
            }
            discoveringPending = false
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }
    }

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)
        intent.putExtra(EXTRA_DATA, characteristic.value)
        intent.putExtra(EXTRA_CHARACTERISTIC, characteristic.uuid.toString())
        context.sendBroadcast(intent)
    }

    fun connect(address: String?): Boolean {
        if (mBluetoothDeviceAddress != null
                && address == mBluetoothDeviceAddress
                && mBluetoothGatt != null) {

            return if (mBluetoothGatt!!.connect()) {
                Algorithm.updateStatusCode(Algorithm.StatusCodes.GattConnected)
                true
            } else {
                false
            }
        }

        val device = mBluetoothAdapter.getRemoteDevice(address) ?: return false
        mBluetoothGatt = device.connectGatt(context, false, mGattCallback)
        return if (mBluetoothGatt != null) {
            mBluetoothDeviceAddress = address
            //retryDiscovering()
            if (Algorithm.StatusCode.value!!.code < Algorithm.StatusCodes.GattConnecting.code)
                Algorithm.updateStatusCode(Algorithm.StatusCodes.GattConnecting)
            true
        } else {
            false
        }
    }

    fun disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return
        }
        mBluetoothGatt?.disconnect()
        mBluetoothGatt?.close()
        Algorithm.StatusCode.postValue(Algorithm.StatusCodes.Disconnected)
    }

    private fun close() {
        if (mBluetoothGatt == null) {
            return
        }
        mBluetoothDeviceAddress = null
        mBluetoothGatt!!.close()
        mBluetoothGatt = null
    }

    fun readCharacteristic(service: UUID, txService: UUID) {
        try { // retry algo
            for (i in 0 until 3) {
                if (mBluetoothAdapter == null || mBluetoothGatt == null) {
                    return
                }
                for (x in mBluetoothGatt!!.services) {
                    if (x.uuid == service) {
                        for (y in x.characteristics) {
                            if (y.uuid == txService) {
                                mBluetoothGatt!!.readCharacteristic(y)
                                return
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    fun enableTXNotification(service: UUID, txService: UUID, txDescriptor: UUID) {
        val RxService = mBluetoothGatt!!.getService(service) ?: return
        val TxChar = RxService.getCharacteristic(txService)
        mBluetoothGatt!!.setCharacteristicNotification(TxChar, true)

        val descriptor = TxChar.getDescriptor(txDescriptor)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        mBluetoothGatt!!.writeDescriptor(descriptor)

    }

    fun writeRXCharacteristic(value: ByteArray): Boolean {
        if (mBluetoothGatt == null) {
            // here possible error
            return false
        }
        val RxService = mBluetoothGatt!!.getService(RX_SERVICE_UUID) ?: return false
        // here possible error
        val RxChar = RxService.getCharacteristic(RX_CHAR_UUID) ?: return false
        RxChar.value = value
        return mBluetoothGatt!!.writeCharacteristic(RxChar)
    }

    companion object {

        var instance: UartService? = null

        var mBluetoothManager: BluetoothManager? = null
        private var mBluetoothGatt: BluetoothGatt? = null

        const val ACTION_DATA_AVAILABLE = "ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_CHARACTERISTIC = "EXTRA_CHAR"

        var PowerServiceUUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb")
        var PowerTXUUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb")
        var PowerDescriptor = UUID.randomUUID()

        var RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        var RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        var TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        var TXServiceDesctiptor = UUID.randomUUID()
    }
}
