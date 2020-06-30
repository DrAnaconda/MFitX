package anonymouls.dev.mgcex.app.backend

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import java.util.*

class UartService : Service() {
    var mBluetoothAdapter: BluetoothAdapter? = null
    var mConnectionState = STATE_DISCONNECTED

    private var mBluetoothDeviceAddress: String? = null
    private var timer: Timer = Timer(false)

    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val intentAction: String
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED
                mConnectionState = STATE_CONNECTED
                broadcastUpdate(intentAction)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED
                mConnectionState = STATE_DISCONNECTED
                mBluetoothGatt = null
                broadcastUpdate(intentAction)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mConnectionState = STATE_DISCOVERED
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
            }
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
    private val mBinder = LocalBinder()

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)
        intent.putExtra(EXTRA_DATA, characteristic.value)
        intent.putExtra(EXTRA_CHARACTERISTIC, characteristic.uuid.toString())
        sendBroadcast(intent)
    }

    inner class LocalBinder : Binder()

    override fun onBind(intent: Intent): IBinder? {
        instance = this
        CommandInterpreter.getInterpreter(this)
        Thread.currentThread().priority = Thread.NORM_PRIORITY
        Thread.currentThread().name = "UARTService"
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        close()
        return super.onUnbind(intent)
    }

    override fun stopService(name: Intent?): Boolean {
        disconnect()
        close()
        return super.stopService(name)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        Thread.currentThread().priority = Thread.NORM_PRIORITY
        Thread.currentThread().name = "UARTService"
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (Algorithm.SelfPointer == null) {
                    startService(Intent(instance, Algorithm::class.java))
                }
            }
        }, 60000, 15000)
        return super.onStartCommand(intent, flags, startId)
    }

    fun connect(address: String?): Boolean {
        if (mBluetoothDeviceAddress != null && address == mBluetoothDeviceAddress
                && mBluetoothGatt != null) {

            return if (mBluetoothGatt!!.connect()) {
                mConnectionState = STATE_CONNECTING
                true
            } else {
                false
            }
        }

        val device = mBluetoothAdapter!!.getRemoteDevice(address) ?: return false

        mBluetoothGatt = device.connectGatt(this, true, mGattCallback)
        return if (mBluetoothGatt != null) {
            mBluetoothDeviceAddress = address
            mConnectionState = STATE_CONNECTED
            //mBluetoothGatt!!.discoverServices()
            true
        } else {
            broadcastUpdate(ACTION_GATT_INIT_FAILED)
            false
        }
    }

    fun disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return
        }
        mBluetoothGatt!!.disconnect()
        // mBluetoothGatt.close();
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
        val RxService = mBluetoothGatt!!.getService(RX_SERVICE_UUID)
        // here possible error
        if (RxService == null) {
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART)
            return false
        }
        val RxChar = RxService.getCharacteristic(RX_CHAR_UUID)
        if (RxChar == null) {
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART)
            return false
        }
        RxChar.value = value
        return mBluetoothGatt!!.writeCharacteristic(RxChar)
    }

    fun retryDiscovering() {
        if (mConnectionState == STATE_CONNECTED) {
            mConnectionState = STATE_DISCOVERING
            if (mBluetoothGatt != null) mBluetoothGatt!!.discoverServices()
        } else {
            Algorithm.StatusCode.postValue(Algorithm.StatusCodes.Disconnected)
            connect(Algorithm.LockedAddress)
        }
    }

    override fun onCreate() {
        instance = this
        mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager!!.adapter
        mBluetoothDeviceAddress = Algorithm.LockedAddress
        this.startService(Intent(this, UartService::class.java))
    }

    companion object {

        var instance: UartService? = null

        var mBluetoothManager: BluetoothManager? = null
        private var mBluetoothGatt: BluetoothGatt? = null

        const val STATE_DISCONNECTED = 0
        const val STATE_CONNECTING = 1
        const val STATE_CONNECTED = 2
        const val STATE_DISCOVERING = 3
        const val STATE_DISCOVERED = 4

        const val ACTION_GATT_CONNECTED = "ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED = "ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "ACTION_DATA_AVAILABLE"
        const val ACTION_GATT_INIT_FAILED = "GATT_INIT_FAILED"
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_CHARACTERISTIC = "EXTRA_CHAR"
        const val DEVICE_DOES_NOT_SUPPORT_UART = "DEVICE_DOES_NOT_SUPPORT_UART"

        var PowerServiceUUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb")
        var PowerTXUUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb")
        var PowerDescriptor = UUID.randomUUID()

        var RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        var RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        var TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        var TXServiceDesctiptor = UUID.randomUUID()
    }
}
