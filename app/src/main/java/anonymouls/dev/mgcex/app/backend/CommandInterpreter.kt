@file:Suppress("NAME_SHADOWING")

package anonymouls.dev.mgcex.app.backend

import android.content.Context
import android.widget.Toast
import anonymouls.dev.mgcex.app.main.SettingsActivity
import anonymouls.dev.mgcex.util.Utils
import java.nio.ByteBuffer
import java.util.*


abstract class CommandInterpreter {
    interface CommandReaction {
        fun MainInfo(Steps: Int, Calories: Int)
        fun BatteryInfo(Charge: Int)
        fun HRIncome(Time: Calendar, HRValue: Int)
        fun HRHistoryRecord(Time: Calendar, HRValue: Int)
        fun MainHistoryRecord(Time: Calendar, Steps: Int, Calories: Int)
        fun SleepHistoryRecord(Time: Calendar, Duration: Int, Type: Int)
    }

    var callback: CommandReaction? = null
    var hRRealTimeControlSupport = false

    companion object {
        private var ActiveInterpreter: CommandInterpreter? = null

        fun getInterpreter(context: Context?): CommandInterpreter? {
            if (ActiveInterpreter == null && context != null) {
                if (Utils.getSharedPrefs(context).contains(SettingsActivity.bandIDConst)) {
                    when (Utils.getSharedPrefs(context).getString(SettingsActivity.bandIDConst, "")?.toUpperCase()) {
                        MGCOOL4CommandInterpreter.CodeName -> ActiveInterpreter = MGCOOL4CommandInterpreter()
                        LM517CommandInterpreter.CodeName -> ActiveInterpreter = LM517CommandInterpreter()
                        else -> Toast.makeText(context, "This device is not supported", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return ActiveInterpreter
        }
    }

    protected fun hexStringToByteArray(s: String): ByteArray {
        var s = s
        s = s.toUpperCase(Locale.ROOT)
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    protected fun getCalendarValueInHex(CUtil: Calendar, CField: Int): String {
        var Value: Int? = CUtil.get(CField)
        if (CField == Calendar.MONTH) Value = Value?.plus(1)
        return if (Value!! < 10) {
            "0$Value"
        } else {
            Utils.subIntegerConversionCheck(Integer.toHexString(Value))
        }
    }

    fun messageToHexValue(Message: String, limiter: Int): String {
        var result = ""
        for (x in 0 until limiter) {
            if (x < Message.length) {
                if (Message[x].toInt() > 0)
                    result += Utils.subIntegerConversionCheck(Integer.toHexString(Message[x].toInt()))
                else
                    result += Utils.subIntegerConversionCheck(Integer.toHexString(Message[x].toInt() and 0xFF))
            } else result += "00"
        }
        return result
    }

    fun postCommand(Request: ByteArray) {
        UartService.instance!!.writeRXCharacteristic(Request)
    }

    abstract fun syncTime(SyncTime: Calendar?)
    abstract fun eraseDatabase()
    abstract fun restoreToDefaults()
    abstract fun stopLongAlarm()
    abstract fun setGyroAction(IsEnabled: Boolean)
    abstract fun buildLongNotify(Message: String)
    abstract fun commandAction(Input: ByteArray, characteristic: UUID)
    abstract fun getMainInfoRequest()
    abstract fun requestSleepHistory(FromDate: Calendar)
    abstract fun requestHRHistory(FromDate: Calendar?)
    abstract fun hRRealTimeControl(Enable: Boolean)
    abstract fun setAlarm(AlarmID: Long, IsEnabled: Boolean, Hour: Int, Minute: Int, Days: Int)
    abstract fun fireNotification(Input: String)
    abstract fun requestBatteryStatus()
    abstract fun requestManualHRMeasure()
}

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
        private const val UARTRXServiceUUIDString = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
        private const val UARTRXUUIDString = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
        private const val UARTTXUUIDString = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
        private const val UARTDescriptor = "00002902-0000-1000-8000-00805f9b34fb"
    }

    init {
        this.hRRealTimeControlSupport = true

        UartService.RX_CHAR_UUID = UUID.fromString(UARTRXUUIDString)
        UartService.TX_CHAR_UUID = UUID.fromString(UARTTXUUIDString)
        UartService.RX_SERVICE_UUID = UUID.fromString(UARTRXServiceUUIDString)
        UartService.TXServiceDesctiptor = UUID.fromString(UARTDescriptor)
    }

    override fun getMainInfoRequest() {
        val CUtil = Calendar.getInstance()
        // 00 00        // + 12 + Month + Day + FF FF
        val Request = (GetMainInfo +
                getCalendarValueInHex(CUtil, Calendar.MONTH) +
                getCalendarValueInHex(CUtil, Calendar.DAY_OF_MONTH) +
                getCalendarValueInHex(CUtil, Calendar.HOUR_OF_DAY) + "00"
                + "12" +
                getCalendarValueInHex(CUtil, Calendar.MONTH) +
                getCalendarValueInHex(CUtil, Calendar.DAY_OF_MONTH) + "FFFF")
        postCommand(hexStringToByteArray(Request))
    }

    override fun hRRealTimeControl(Enable: Boolean) {
        val Request: String
        if (Enable) {
            Request = HRRealTimeHeader + "01"
        } else {
            Request = HRRealTimeHeader + "00"
        }
        postCommand(hexStringToByteArray(Request))
    }

    override fun requestHRHistory(FromDate: Calendar?) {
        var FromDate = FromDate
        if (FromDate == null) {
            FromDate = Calendar.getInstance()
            FromDate!!.add(Calendar.DAY_OF_MONTH, -3)
        }
        val Request = HRHistoryHeader + "12" + getCalendarValueInHex(FromDate, Calendar.MONTH) +
                getCalendarValueInHex(FromDate, Calendar.DAY_OF_MONTH) +
                getCalendarValueInHex(FromDate, Calendar.HOUR_OF_DAY) + "0012" +
                getCalendarValueInHex(FromDate, Calendar.MONTH) +
                getCalendarValueInHex(FromDate, Calendar.DAY_OF_MONTH) +
                getCalendarValueInHex(FromDate, Calendar.HOUR_OF_DAY) +
                getCalendarValueInHex(FromDate, Calendar.MINUTE)
        postCommand(hexStringToByteArray(Request))
    }

    override fun syncTime(SyncTime: Calendar?) {
        var SyncTime = SyncTime
        if (SyncTime == null) SyncTime = Calendar.getInstance()
        val Request = TimeSyncHeader + getCalendarValueInHex(SyncTime!!, Calendar.YEAR) +
                getCalendarValueInHex(SyncTime, Calendar.MONTH) +
                getCalendarValueInHex(SyncTime, Calendar.DAY_OF_MONTH) +
                getCalendarValueInHex(SyncTime, Calendar.HOUR_OF_DAY) +
                getCalendarValueInHex(SyncTime, Calendar.MINUTE) +
                getCalendarValueInHex(SyncTime, Calendar.SECOND)
        postCommand(hexStringToByteArray(Request))
    }

    override fun requestSleepHistory(FromDate: Calendar) {
        var request: String = SleepHistoryHeader
        request += getCalendarValueInHex(FromDate, Calendar.MONTH)
        request += getCalendarValueInHex(FromDate, Calendar.DAY_OF_MONTH) + "0000"
        postCommand(hexStringToByteArray(request))
    }

    override fun eraseDatabase() {
        postCommand(hexStringToByteArray(EraseDataHeader))
    }

    override fun restoreToDefaults() {
        postCommand(hexStringToByteArray(RestoreCommandHeader))
    }

    override fun setAlarm(AlarmID: Long, IsEnabled: Boolean, Hour: Int, Minute: Int, Days: Int) {
        var Command = AlarmHeader
        Command += Utils.subIntegerConversionCheck(java.lang.Long.toHexString(AlarmID))
        if (IsEnabled) Command += "01" else Command += "00"
        Command += Utils.subIntegerConversionCheck(Integer.toHexString(Hour))
        Command += Utils.subIntegerConversionCheck(Integer.toHexString(Minute))
        Command += Utils.subIntegerConversionCheck(Integer.toHexString(Days))
        postCommand(hexStringToByteArray(Command))
    }

    override fun stopLongAlarm() {
        postCommand(hexStringToByteArray(StopLongAlarmHeader))
    }

    override fun setGyroAction(IsEnabled: Boolean) {
        var command = GyroActionCommandHeader
        if (IsEnabled) command += "01" else command = "00"
        postCommand(hexStringToByteArray(command))
    }

    private fun buildNotify(Message: String): ByteArray {
        var Request = ShortMessageHeader
        val Msg = Message.toByteArray()
        var Offset = 0
        for (i in 0..34) {
            if (i == 12) {
                Request += "00"
                Offset++
                continue
            }
            if (i == 32) {
                Request += "01"
                Offset++
                continue
            }
            if (i < Msg.size && !(i >= Msg.size)) {
                if (Msg[i - Offset] > 0)
                    Request += Utils.subIntegerConversionCheck(Integer.toHexString(Msg[i - Offset].toInt()))
                else
                    Request += Utils.subIntegerConversionCheck(Integer.toHexString((Msg[i - Offset]).toInt() and 0xFF))
            } else
                Request += "00"
        }
        return hexStringToByteArray(Request + "2E2E2E")
    }

    override fun buildLongNotify(Message: String) {
        val MessageBytes = Message.toByteArray()
        var Lenght = 5 + MessageBytes.size//MAX 12 bytes
        if (Lenght > 17) Lenght = 17
        var Request = (LongMessageHeaderPartOne + Utils.subIntegerConversionCheck(Integer.toHexString(Lenght))
                + LongMessageHeaderPartTwo)
        for (i in 0..11) {
            if (i < MessageBytes.size) {
                if (MessageBytes[i] > 0)
                    Request += Utils.subIntegerConversionCheck(Integer.toHexString(MessageBytes[i].toInt()))
                else
                    Request += Utils.subIntegerConversionCheck(Integer.toHexString(MessageBytes[i].toInt() and 0xFF))
            } else break
        }
        postCommand(hexStringToByteArray(Request))
    }

    private fun commandID14(Input: ByteArray) {
        if (Input[4].toInt() != 81 && Input[5].toInt() != 8) return
        var Buff: ByteBuffer
        Buff = ByteBuffer.wrap(Input, 7, 2)
        val Steps = Buff.short
        Buff = ByteBuffer.wrap(Input, 10, 2)
        val Calories = Buff.short
        callback?.MainInfo(Steps.toInt(), Calories.toInt())
    }

    private fun hRRTHandler(Input: ByteArray) {
        if (Input[4].toInt() != -124 || Input[5].toInt() != -128) return
        callback?.HRIncome(Calendar.getInstance(), Input[Input.size - 1].toInt())
    }

    private fun hRHistoryHandler(Input: ByteArray) {
        try {
            val CRecord = Calendar.getInstance()
            CRecord.set(CRecord.get(Calendar.YEAR), Input[7] - 1, Input[8].toInt(), Input[9].toInt(), Input[10].toInt())
            callback?.HRHistoryRecord(CRecord, Input[11].toInt())
        } catch (Ex: Exception) {

        }

    }

    private fun mainHistoryHandler(Input: ByteArray) {
        if (Input[4].toInt() != 81 && Input[5].toInt() != 32) return
        val RecordTime = Calendar.getInstance()
        RecordTime.set(RecordTime.get(Calendar.YEAR), Input[7].toInt(), Input[8].toInt(), Input[9].toInt(), 0)
        var Buff = ByteBuffer.wrap(Input, 11, 2)
        val Steps = Buff.short
        Buff = ByteBuffer.wrap(Input, 14, 2)
        val Calories = Buff.short
        callback?.MainHistoryRecord(RecordTime, Steps.toInt(), Calories.toInt())
    }

    private fun batteryCommandHandler(Input: ByteArray) {
        callback?.BatteryInfo(Input[Input.size - 1].toInt())
    }

    private fun sleepHistoryHandler(Input: ByteArray) {
        val RecordTime = Calendar.getInstance()
        RecordTime.set(RecordTime.get(Calendar.YEAR), Input[7] - 1, Input[8].toInt(),
                Input[9].toInt(), Input[10].toInt(), 0)
        val type: Int = Input[11].toInt()
        val duration: Int = Input[13].toInt()
        callback?.SleepHistoryRecord(RecordTime, duration, type)
    }

    override fun commandAction(Input: ByteArray, characteristic: UUID) {
        if (characteristic == UUID.fromString(UARTTXUUIDString)) {
            val CommandID = Input[2]
            when (CommandID) {
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
        val Req = buildNotify(Input)
        var Req1 = Arrays.copyOfRange(Req, 0, 20)
        postCommand(Req1)
        Thread.sleep(50)
        Req1 = Arrays.copyOfRange(Req, 20, 40)
        postCommand(Req1)
        Thread.sleep(50)
        Req1 = Arrays.copyOfRange(Req, 40, 46)
        postCommand(Req1)
        Thread.sleep(50)
    }

    override fun requestBatteryStatus() {
        // TODO ???
        return
    }

    override fun requestManualHRMeasure() {
        return // not supported
    }


}

class LM517CommandInterpreter : CommandInterpreter() {
    companion object {
        const val CodeName = "LM716"

        private const val syncTimeCommandHeader = "CD00091201010004"
        private const val longAlarmHeader = "CD0014120111000F"
        private const val notifyHeader = "CD00291201120024010000"

        private const val UARTServiceUUIDString = "6e400001-b5a3-f393-e0a9-e50e24dcca9d"
        private const val UARTRXUUIDString = "6e400002-b5a3-f393-e0a9-e50e24dcca9d"
        private const val UARTTXUUIDString = "6e400003-b5a3-f393-e0a9-e50e24dcca9d"
        private const val UARTDescriptor = "00002902-0000-1000-8000-00805f9b34fb" // 00002902-0000-1000-8000-00805f9b34fb
        private const val PowerServiceString = "0000180f-0000-1000-8000-00805f9b34fb"
        private const val PowerTXString = "00002a19-0000-1000-8000-00805f9b34fb"
        private const val PowerTX2String = "00002a19-0000-0000-8000-00805f9b34fb"
        private const val PowerDescriptor = "00002902-0000-1000-8000-00805f9b34fb"
    }

    init {
        UartService.RX_SERVICE_UUID = UUID.fromString(UARTServiceUUIDString)
        UartService.RX_CHAR_UUID = UUID.fromString(UARTRXUUIDString)
        UartService.TX_CHAR_UUID = UUID.fromString(UARTTXUUIDString)
        UartService.TXServiceDesctiptor = UUID.fromString(UARTDescriptor)
        UartService.PowerServiceUUID = UUID.fromString(PowerServiceString)
        UartService.PowerTXUUID = UUID.fromString(PowerTXString)
        UartService.PowerDescriptor = UUID.fromString(PowerDescriptor)
    }

    //region Helpers
    // TODO Maybe this function is wrong
    private fun createSpecialCalendar(): Calendar {
        val result = Calendar.getInstance()
        result.set(Calendar.MONTH, 11)
        result.set(Calendar.DAY_OF_MONTH, 8)
        result.set(Calendar.YEAR, 1991)
        result.set(Calendar.HOUR, 0)
        result.set(Calendar.MINUTE, 0)
        result.set(Calendar.SECOND, 0)
        return result
    }

    private fun decryptDays(offset: Int, targetCalendar: Calendar?): Calendar {
        val result = targetCalendar ?: createSpecialCalendar()
        result.add(Calendar.DAY_OF_YEAR, offset)
        return result
    }

    private fun decryptTime(offset: Int, targetCalendar: Calendar): Calendar {
        targetCalendar.set(Calendar.HOUR, 0)
        targetCalendar.set(Calendar.MINUTE, 0)
        targetCalendar.set(Calendar.SECOND, 0)
        targetCalendar.add(Calendar.SECOND, offset)
        return targetCalendar
    }

//endregion

    //region proceeders

    private fun hrRecordProceeder(Input: ByteArray) {
        // WARNING. Shit like pressure and ox% is ignoring
        var buffer = ByteBuffer.wrap(Input, 8, 2)
        var recordTime = decryptDays(buffer.int, null)
        buffer = ByteBuffer.wrap(Input, 13, 3)
        recordTime = decryptTime(buffer.int, recordTime)
        val hrValue = Input.get(Input.size - 1)
        callback?.HRHistoryRecord(recordTime, hrValue.toInt())
    }

    private fun commandsEntryPoint(Input: ByteArray) {
        if (Input[0].toInt() != 205) return
        if (Input[1].toInt() != 0) return
        if (Input[2].toInt() != 21) return
        when (Input[4].toInt()) {
            14 -> hrRecordProceeder(Input)
        }
        // TODO
    }

    //endregion

    override fun syncTime(SyncTime: Calendar?) {
        val targetTime = SyncTime ?: Calendar.getInstance()
        var mainNumber = (targetTime.get(Calendar.YEAR) - 2000) shl 26
        mainNumber = mainNumber or (targetTime.get(Calendar.MONTH) + 1 shl 22)
        var dayOfMonth = targetTime.get(Calendar.DAY_OF_MONTH) * 2
        var time = targetTime.get(Calendar.HOUR_OF_DAY)
        if (time >= 16) {
            dayOfMonth += 1
            time -= 16
        }
        mainNumber = mainNumber or (dayOfMonth shl 16)
        mainNumber = mainNumber or (time shl 12)
        mainNumber = mainNumber or (targetTime.get(Calendar.MINUTE) shl 6)
        mainNumber = mainNumber or (targetTime.get(Calendar.SECOND))
        postCommand(hexStringToByteArray(syncTimeCommandHeader + Integer.toHexString(mainNumber.toInt())))
    }

    override fun eraseDatabase() {
        postCommand(hexStringToByteArray("CD0006150106000101"))
    }

    override fun restoreToDefaults() {
        postCommand(hexStringToByteArray("CD00021D01"))
    }

    override fun stopLongAlarm() {
        // TODO: TEST
        postCommand(hexStringToByteArray(longAlarmHeader + "000000000000"))
    }

    override fun setGyroAction(IsEnabled: Boolean) {
        //TODO("Not yet implemented")
    }

    private fun buildNotify(Message: String): ByteArray {
        // TODO Investigate Types
        // 2 nd package up to 20 bytes
        // 3 rd up to 3?
        return hexStringToByteArray(notifyHeader + messageToHexValue(Message, 32))
    }

    override fun buildLongNotify(Message: String) {
        postCommand(hexStringToByteArray(longAlarmHeader + "01" + messageToHexValue(Message, 9)))
    }

    override fun commandAction(Input: ByteArray, characteristic: UUID) {
        when (characteristic) {
            UUID.fromString(UARTTXUUIDString) -> {
                commandsEntryPoint(Input)
            }
            UUID.fromString(PowerTX2String), UUID.fromString(PowerTXString) -> {
                this.callback?.BatteryInfo(Input[Input.size - 1].toInt())
            }
        }
        //TODO("Not yet implemented")
    }

    override fun getMainInfoRequest() {
        //TODO("Not yet implemented")
    }

    override fun requestSleepHistory(FromDate: Calendar) {
        //TODO("Not yet implemented")
    }

    override fun requestHRHistory(FromDate: Calendar?) {
        // TODO TEST this.
        //postCommand(hexStringToByteArray("dc00051501001001".toUpperCase())) No data?
        requestManualHRMeasure() // for tests
    }

    override fun hRRealTimeControl(Enable: Boolean) {
        return // NOT SUPPORTED
    }

    override fun setAlarm(AlarmID: Long, IsEnabled: Boolean, Hour: Int, Minute: Int, Days: Int) {
        //TODO("Not yet implemented")
    }

    override fun fireNotification(Input: String) {
        val Req = buildNotify(Input)
        var Req1 = Req.copyOfRange(0, 20)
        postCommand(Req1)
        Thread.sleep(50)
        Req1 = Req.copyOfRange(20, 40)
        postCommand(Req1)
        Thread.sleep(50)
        Req1 = Req.copyOfRange(40, 46) // TODO CHECK
        postCommand(Req1)
        Thread.sleep(50)
    }

    override fun requestBatteryStatus() {
        UartService.instance?.readCharacteristic(UartService.PowerServiceUUID, UartService.PowerTXUUID)
    }

    override fun requestManualHRMeasure() {
        postCommand(hexStringToByteArray("CD0006120118000101"))
    }
}