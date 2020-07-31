package anonymouls.dev.mgcex.app.backend

import anonymouls.dev.mgcex.util.Utils
import java.nio.ByteBuffer
import java.util.*

@ExperimentalStdlibApi
class MGCOOL4CommandInterpreter : CommandInterpreter() {

    companion object {
        const val CodeName = "BAND4"
        private const val GetMainInfo = "AB000EFF51800012"
        private const val HRRealTimeHeader = "AB0004FF8480"
        private const val HRHistoryHeader = "AB000EFF518000"
        private const val TimeSyncHeader = "AB000BFF938000"
        private const val SleepHistoryHeader = "AB0009FF52800012"
        private const val RestoreCommandHeader = "AB0003FFFF80"
        private const val EraseDataHeader = "AB0004FF238000"
        private const val FindCommand = "AB0003FF7180"
        private const val GyroActionCommandHeader = "AB0004FF7780"
        private const val AlarmHeader = "AB0008FF7380"
        private const val LongMessageHeaderPartOne = "AB00"
        private const val LongMessageHeaderPartTwo = "FF72800102"
        private const val ShortMessageHeader = "AB0029FF72800302"
        private const val StopLongAlarmHeader = "AB0005FF72800202"
    }

    //region Special features

    override val hRRealTimeControlSupport: Boolean
        get() = true
    override val stepsTargetSettingSupport: Boolean
        get() = false
    override val sittingReminderSupport: Boolean
        get() = true // TODO not realized
    override val vibrationSupport: Boolean
        get() = false

    //endregion

    //region UUIDs

    override val mUARTServiceUUIDString: String
        get() = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    override val mUARTRXUUIDString: String
        get() = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    override val mARTTXUUIDString: String
        get() = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
    override val mUARTDescriptor: String
        get() = "00002902-0000-1000-8000-00805f9b34fb"
    override val powerServiceString: String
        get() = "00000000-0000-0000-0000-000000000000" // not suppoted
    override val powerTXString: String
        get() = "00000000-0000-0000-0000-000000000000"
    override val powerTX2String: String
        get() = "00000000-0000-0000-0000-000000000000"
    override val powerDescriptor: String
        get() = "00000000-0000-0000-0000-000000000000"

    //endregion

    override fun getMainInfoRequest() {
        synchronized(this::class) {
            val cUtil = Calendar.getInstance()
            // 00 00        // + 12 + Month + Day + FF FF
            val request = (GetMainInfo +
                    getCalendarValueInHex(cUtil, Calendar.MONTH) +
                    getCalendarValueInHex(cUtil, Calendar.DAY_OF_MONTH) +
                    getCalendarValueInHex(cUtil, Calendar.HOUR_OF_DAY) + "00"
                    + "12" +
                    getCalendarValueInHex(cUtil, Calendar.MONTH) +
                    getCalendarValueInHex(cUtil, Calendar.DAY_OF_MONTH) + "FFFF")
            postCommand(hexStringToByteArray(request))
        }
    }

    override fun hRRealTimeControl(Enable: Boolean) {
        synchronized(this::class) {
            val request: String = if (Enable) {
                HRRealTimeHeader + "01"
            } else {
                HRRealTimeHeader + "00"
            }
            postCommand(hexStringToByteArray(request))
        }
    }

    override fun requestHRHistory(FromDate: Calendar?) {
        var fromDate = FromDate
        if (fromDate == null) {
            fromDate = Calendar.getInstance()
            fromDate!!.add(Calendar.DAY_OF_MONTH, -3)
        }
        val request = HRHistoryHeader + "12" + getCalendarValueInHex(fromDate, Calendar.MONTH) +
                getCalendarValueInHex(fromDate, Calendar.DAY_OF_MONTH) +
                getCalendarValueInHex(fromDate, Calendar.HOUR_OF_DAY) + "0012" +
                getCalendarValueInHex(fromDate, Calendar.MONTH) +
                getCalendarValueInHex(fromDate, Calendar.DAY_OF_MONTH) +
                getCalendarValueInHex(fromDate, Calendar.HOUR_OF_DAY) +
                getCalendarValueInHex(fromDate, Calendar.MINUTE)
        postCommand(hexStringToByteArray(request))
    }

    override fun syncTime(SyncTime: Calendar?) {
        synchronized(this::class) {
            var syncTime = SyncTime
            if (syncTime == null) syncTime = Calendar.getInstance()
            val request = TimeSyncHeader + getCalendarValueInHex(syncTime!!, Calendar.YEAR) +
                    getCalendarValueInHex(syncTime, Calendar.MONTH) +
                    getCalendarValueInHex(syncTime, Calendar.DAY_OF_MONTH) +
                    getCalendarValueInHex(syncTime, Calendar.HOUR_OF_DAY) +
                    getCalendarValueInHex(syncTime, Calendar.MINUTE) +
                    getCalendarValueInHex(syncTime, Calendar.SECOND)
            postCommand(hexStringToByteArray(request))
        }
    }

    override fun requestSleepHistory(FromDate: Calendar) {
        synchronized(this::class) {
            var request: String = SleepHistoryHeader
            request += getCalendarValueInHex(FromDate, Calendar.MONTH)
            request += getCalendarValueInHex(FromDate, Calendar.DAY_OF_MONTH) + "0000"
            postCommand(hexStringToByteArray(request))
        }
    }

    override fun eraseDatabase() {
        synchronized(this::class) {
            postCommand(hexStringToByteArray(EraseDataHeader))
        }
    }

    override fun restoreToDefaults() {
        synchronized(this::class) {
            postCommand(hexStringToByteArray(RestoreCommandHeader))
        }
    }

    override fun setAlarm(AlarmID: Long, IsEnabled: Boolean, Hour: Int, Minute: Int, Days: Int) {
        synchronized(this::class) {
            var command = AlarmHeader
            command += Utils.subIntegerConversionCheck(java.lang.Long.toHexString(AlarmID))
            command += if (IsEnabled) "01" else "00"
            command += Utils.subIntegerConversionCheck(Integer.toHexString(Hour))
            command += Utils.subIntegerConversionCheck(Integer.toHexString(Minute))
            command += Utils.subIntegerConversionCheck(Integer.toHexString(Days))
            postCommand(hexStringToByteArray(command))
        }
    }

    override fun stopLongAlarm() {
        synchronized(this::class) {
            postCommand(hexStringToByteArray(StopLongAlarmHeader))
        }
    }

    override fun setGyroAction(IsEnabled: Boolean) {
        synchronized(this::class) {
            var command = GyroActionCommandHeader
            if (IsEnabled) command += "01" else command = "00"
            postCommand(hexStringToByteArray(command))
        }
    }

    private fun buildNotify(Message: String): ByteArray {
        var request = ShortMessageHeader
        val msg = Message.toByteArray()
        var offset = 0
        for (i in 0..34) {
            if (i == 12) {
                request += "00"
                offset++
                continue
            }
            if (i == 32) {
                request += "01"
                offset++
                continue
            }
            request += if (i < msg.size && i < msg.size) {
                if (msg[i - offset] > 0)
                    Utils.subIntegerConversionCheck(Integer.toHexString(msg[i - offset].toInt()))
                else
                    Utils.subIntegerConversionCheck(Integer.toHexString((msg[i - offset]).toInt() and 0xFF))
            } else
                "00"
        }
        return hexStringToByteArray(request + "2E2E2E")
    }

    override fun buildLongNotify(Message: String) {
        synchronized(this::class){
            val messageBytes = Message.toByteArray()
            var lenght = 5 + messageBytes.size//MAX 12 bytes
            if (lenght > 17) lenght = 17
            var request = (LongMessageHeaderPartOne + Utils.subIntegerConversionCheck(Integer.toHexString(lenght))
                    + LongMessageHeaderPartTwo)
            for (i in 0..11) {
                request += if (i < messageBytes.size) {
                    if (messageBytes[i] > 0)
                        Utils.subIntegerConversionCheck(Integer.toHexString(messageBytes[i].toInt()))
                    else
                        Utils.subIntegerConversionCheck(Integer.toHexString(messageBytes[i].toInt() and 0xFF))
                } else break
            }
            postCommand(hexStringToByteArray(request))
        }
    }

    private fun commandID14(Input: ByteArray) {
        if (Input[4].toInt() != 81 && Input[5].toInt() != 8) return
        var buff: ByteBuffer = ByteBuffer.wrap(Input, 7, 2)
        val steps = buff.short
        buff = ByteBuffer.wrap(Input, 10, 2)
        val calories = buff.short
        callback?.mainInfo(steps.toInt(), calories.toInt())
    }

    private fun hRRTHandler(Input: ByteArray) {
        if (Input[4].toInt() != -124 || Input[5].toInt() != -128) return
        callback?.hrIncome(Calendar.getInstance(), Input[Input.size - 1].toInt())
    }

    private fun hRHistoryHandler(Input: ByteArray) {
        try {
            val cRecord = Calendar.getInstance()
            cRecord.set(cRecord.get(Calendar.YEAR), Input[7] - 1, Input[8].toInt(), Input[9].toInt(), Input[10].toInt())
            callback?.hrHistoryRecord(cRecord, Input[11].toInt())
        } catch (Ex: Exception) {}

    }

    private fun mainHistoryHandler(Input: ByteArray) {
        if (Input[4].toInt() != 81 && Input[5].toInt() != 32) return
        val recordTime = Calendar.getInstance()
        recordTime.set(recordTime.get(Calendar.YEAR), Input[7].toInt(), Input[8].toInt(), Input[9].toInt(), 0)
        var buff = ByteBuffer.wrap(Input, 11, 2)
        val steps = buff.short
        buff = ByteBuffer.wrap(Input, 14, 2)
        val calories = buff.short
        callback?.mainHistoryRecord(recordTime, steps.toInt(), calories.toInt())
    }

    private fun batteryCommandHandler(Input: ByteArray) {
        callback?.batteryInfo(Input[Input.size - 1].toInt())
    }

    private fun sleepHistoryHandler(Input: ByteArray) {
        val recordTime = Calendar.getInstance()
        recordTime.set(recordTime.get(Calendar.YEAR), Input[7] - 1, Input[8].toInt(),
                Input[9].toInt(), Input[10].toInt(), 0)
        val type: Int = Input[11].toInt()
        val duration: Int = Input[13].toInt()
        callback?.sleepHistoryRecord(recordTime, duration, type)
    }

    override fun commandAction(Input: ByteArray, characteristic: UUID) {
        if (characteristic == UUID.fromString(mARTTXUUIDString)) {
            when (Input[2]) {
                (14).toByte() -> commandID14(Input)
                (9).toByte() -> hRHistoryHandler(Input)
                (5).toByte() -> batteryCommandHandler(Input)
                (4).toByte() -> hRRTHandler(Input)
                (22).toByte() -> mainHistoryHandler(Input)
                (11).toByte() -> sleepHistoryHandler(Input)
            }
        }
    }

    override fun fireNotification(Input: String) {
        synchronized(this::class) {
            val req = buildNotify(Input)
            var req1 = req.copyOfRange(0, 20)
            postCommand(req1)
            req1 = req.copyOfRange(20, 40)
            postCommand(req1)
            req1 = req.copyOfRange(40, 46)
            postCommand(req1)
        }
    }

    override fun requestBatteryStatus() {
        // TODO ???
        return
    }

    override fun requestManualHRMeasure(cancel: Boolean) {
        return // not supported
    }

    override fun setVibrationSetting(enabled: Boolean) {
        return // unsupported
    }

    override fun setTargetSteps(count: Int) {
        return // unsupported
    }

    override fun setSittingReminder(enabled: Boolean) {
        return // unsupported
    }

    override fun requestSettings() {
        return // unsupported
    }


}