package anonymouls.dev.mgcex.app.backend

import android.os.Handler
import anonymouls.dev.mgcex.app.main.SettingsActivity
import anonymouls.dev.mgcex.util.Utils
import java.nio.ByteBuffer
import java.util.*

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
        if (Algorithm.SelfPointer != null)
            Utils.getSharedPrefs(Algorithm.SelfPointer!!.baseContext).edit().remove(SettingsActivity.targetSteps).apply()
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
        callback?.mainInfo(Steps.toInt(), Calories.toInt())
    }

    private fun hRRTHandler(Input: ByteArray) {
        if (Input[4].toInt() != -124 || Input[5].toInt() != -128) return
        callback?.hrIncome(Calendar.getInstance(), Input[Input.size - 1].toInt())
    }

    private fun hRHistoryHandler(Input: ByteArray) {
        try {
            val CRecord = Calendar.getInstance()
            CRecord.set(CRecord.get(Calendar.YEAR), Input[7] - 1, Input[8].toInt(), Input[9].toInt(), Input[10].toInt())
            callback?.hrHistoryRecord(CRecord, Input[11].toInt())
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
        callback?.mainHistoryRecord(RecordTime, Steps.toInt(), Calories.toInt())
    }

    private fun batteryCommandHandler(Input: ByteArray) {
        callback?.batteryInfo(Input[Input.size - 1].toInt())
    }

    private fun sleepHistoryHandler(Input: ByteArray) {
        val RecordTime = Calendar.getInstance()
        RecordTime.set(RecordTime.get(Calendar.YEAR), Input[7] - 1, Input[8].toInt(),
                Input[9].toInt(), Input[10].toInt(), 0)
        val type: Int = Input[11].toInt()
        val duration: Int = Input[13].toInt()
        callback?.sleepHistoryRecord(RecordTime, duration, type)
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
        var Req1 = Req.copyOfRange(0, 20)
        postCommand(Req1)
        Utils.safeThreadSleep(50, false)
        Req1 = Req.copyOfRange(20, 40)
        Handler().postDelayed({ postCommand(Req1) }, 50)
        Req1 = Req.copyOfRange(40, 46)
        Handler().postDelayed({ postCommand(Req1) }, 110)
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