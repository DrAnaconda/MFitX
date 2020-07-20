package anonymouls.dev.mgcex.app.backend

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.nfc.FormatException
import android.os.Build
import java.util.*

class SimpleRecord(val characteristic: String, val Data: ByteArray)

@ExperimentalStdlibApi
class UartService(private val context: Context) {

    private var mBluetoothAdapter: BluetoothAdapter =
            (context.getSystemService(Service.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private lateinit var mBluetoothDeviceAddress: String
    private var discoveringPending = false
    private var device: BluetoothDevice? = null
    private var bluetoothServiceBusy = Any()
    private var isConnected = false
    private var isDiscovered = false
    private var existsUnclosedConnection = false

    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            synchronized(bluetoothServiceBusy) {
                Thread.currentThread().name = "AAConnecter" + (Random().nextInt() % 50)
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        discoveringPending = true; isConnected = true; isDiscovered = false
                        gatt.discoverServices()
                        if (Algorithm.StatusCode.value!!.code < Algorithm.StatusCodes.GattDiscovering.code)
                            Algorithm.updateStatusCode(Algorithm.StatusCodes.GattDiscovering)
                        Algorithm.SelfPointer?.thread?.interrupt()
                    }
                    BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_DISCONNECTED -> {
                        gatt.connect()
                        isConnected = false; isDiscovered = false //existsUnclosedConnection = false;
                        //mBluetoothGatt = null
                        //device = null
                        Algorithm.updateStatusCode(Algorithm.StatusCodes.Disconnected)
                    }
                    BluetoothProfile.STATE_CONNECTING -> Algorithm.updateStatusCode(Algorithm.StatusCodes.Connecting)
                    else -> {
                    } // ignore
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            synchronized(bluetoothServiceBusy) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
                    this@UartService.enableTXNotification(RX_SERVICE_UUID, TX_CHAR_UUID, TXServiceDesctiptor)
                    this@UartService.enableTXNotification(PowerServiceUUID, PowerTXUUID, PowerDescriptor)
                    Algorithm.updateStatusCode(Algorithm.StatusCodes.GattReady)
                } else {
                    gatt.discoverServices()
                    Algorithm.updateStatusCode(Algorithm.StatusCodes.GattConnected)
                }
                discoveringPending = false
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendSimpleRecord(characteristic.uuid.toString(), characteristic.value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            sendSimpleRecord(characteristic.uuid.toString(), characteristic.value)
        }
    }

    private fun sendSimpleRecord(characteristic: String, Data: ByteArray) {
        val sm = SimpleRecord(characteristic, Data)
        Algorithm.SelfPointer?.enqueneData(sm)
    }

    fun connect(address: String): Boolean {
        synchronized(bluetoothServiceBusy) {
            if (isConnected) {
                if (Algorithm.StatusCode.value!!.code < Algorithm.StatusCodes.GattConnected.code)
                    Algorithm.updateStatusCode(Algorithm.StatusCodes.GattConnected)
                return true
            }

            if (this::mBluetoothDeviceAddress.isInitialized
                    && address == mBluetoothDeviceAddress
                    && mBluetoothGatt != null) {

                return if (mBluetoothGatt!!.connect()) {
                    Algorithm.updateStatusCode(Algorithm.StatusCodes.GattConnecting)
                    true
                } else {
                    false
                }
            }
            Algorithm.StatusCode.postValue(Algorithm.StatusCodes.Connecting)
            discoveringPending = false
            this.mBluetoothDeviceAddress = address
            device = mBluetoothAdapter.getRemoteDevice(address) ?: return false
            // WARNING Auto connect param impacting of productivity
            mBluetoothGatt = device?.connectGatt(context, false, mGattCallback)
            return if (mBluetoothGatt != null) {
                existsUnclosedConnection = true
                mBluetoothDeviceAddress = address
                //retryDiscovering()
                if (Algorithm.StatusCode.value!!.code < Algorithm.StatusCodes.GattConnecting.code)
                    Algorithm.updateStatusCode(Algorithm.StatusCodes.GattConnecting)
                true
            } else {
                false
            }
        }
    }

    fun disconnect() {
        synchronized(bluetoothServiceBusy) {
            mBluetoothGatt?.disconnect()
            mBluetoothGatt?.close()
            mBluetoothGatt = null
        }
    }

    fun readCharacteristic(service: UUID, txService: UUID) {
        try { // retry algo
            for (i in 0 until 3) {
                if (mBluetoothGatt == null) {
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

    fun retryDiscovery() {
        synchronized(bluetoothServiceBusy) {
            if (discoveringPending) return
            if (mBluetoothGatt == null) {
                //connect(mBluetoothDeviceAddress)
            } else {
                discoveringPending = true
                mBluetoothGatt?.discoverServices()
            }
        }
    }

    fun forceEnableBluetooth() {
        mBluetoothAdapter.enable()
    }

    fun probeConnection(): Boolean {
        if (!mBluetoothAdapter.isEnabled) {
            Algorithm.StatusCode.postValue(Algorithm.StatusCodes.BluetoothDisabled)
            return false
        } else {
            if (mBluetoothGatt == null || device == null) {
                Algorithm.updateStatusCode(Algorithm.StatusCodes.Disconnected)
                //connect(mBluetoothDeviceAddress)
                return false
            } else {
                if (discoveringPending) {
                    Algorithm.StatusCode.postValue(Algorithm.StatusCodes.GattDiscovering)
                    return false
                }
            }
        }
        return true
    }

    private val locker = Any()
    fun writeRXCharacteristic(value: ByteArray): Boolean {
        synchronized(locker) {
            if (mBluetoothGatt == null) {
                Algorithm.StatusCode.postValue(Algorithm.StatusCodes.Disconnected)
                throw FormatException("Gatt disconnected")
            }
            val rxService = mBluetoothGatt!!.getService(RX_SERVICE_UUID) ?: return false
            val rxChar = rxService.getCharacteristic(RX_CHAR_UUID) ?: return false
            rxChar.value = value
            return mBluetoothGatt!!.writeCharacteristic(rxChar)
        }
    }

    companion object {
        private var mBluetoothGatt: BluetoothGatt? = null

        var PowerServiceUUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb")
        var PowerTXUUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb")
        var PowerDescriptor = UUID.randomUUID()

        var RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        var RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        var TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        var TXServiceDesctiptor = UUID.randomUUID()
    }
}
