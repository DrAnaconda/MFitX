@file:Suppress("NAME_SHADOWING")

package anonymouls.dev.MGCEX.App

import java.nio.ByteBuffer
import java.util.*

class CommandInterpreter {

    private object WeekDaysIDs {
        val Monday = 1
        val Tuesday = 2
        val Wednesday = 4
        val Thursday = 8
        val Friday = 16
        val Saturday = 32
        val Sunday = 64
    }

    interface CommandReaction {
        fun MainInfo(Steps: Int, Calories: Int)
        fun BatteryInfo(Charge: Int)
        fun HRIncome(Time: Calendar, HRValue: Int)
        fun HRHistoryRecord(Time: Calendar, HRValue: Int)
        fun MainHistoryRecord(Time: Calendar, Steps: Int, Calories: Int)
        fun SleepHistoryRecord(Time: Calendar, Duration: Int, Type: Int)
    }

    companion object {

        var Callback: CommandReaction? = null

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

        private fun hexStringToByteArray(s: String): ByteArray {
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
        fun SubIntegerConversionCheck(CheckIn: String): String {
            return if (CheckIn.length != 2) {
                "0" + CheckIn.toUpperCase(Locale.ROOT)
            } else
                CheckIn
        }
        private fun GetCalendarValueInHex(CUtil: Calendar, CField: Int): String {
            var Value: Int? = CUtil.get(CField)
            if (CField == Calendar.MONTH) Value = Value?.plus(1)
            return if (Value!! < 10) {
                "0$Value"
            } else {
                SubIntegerConversionCheck(Integer.toHexString(Value))
            }
        }
        fun GetMainInfoRequest(): ByteArray {
            val CUtil = Calendar.getInstance()
            // 00 00        // + 12 + Month + Day + FF FF
            val Request = (GetMainInfo +
                    GetCalendarValueInHex(CUtil, Calendar.MONTH) +
                    GetCalendarValueInHex(CUtil, Calendar.DAY_OF_MONTH) +
                    GetCalendarValueInHex(CUtil, Calendar.HOUR_OF_DAY) + "00"
                    + "12" +
                    GetCalendarValueInHex(CUtil, Calendar.MONTH) +
                    GetCalendarValueInHex(CUtil, Calendar.DAY_OF_MONTH) + "FFFF")
            return hexStringToByteArray(Request)
        }

        fun HRRealTimeControl(Enable: Boolean): ByteArray {
            val Request: String
            if (Enable) {
                Request = HRRealTimeHeader + "01"
            } else {
                Request = HRRealTimeHeader + "00"
            }
            return hexStringToByteArray(Request)
        }
        fun requestHRHistory(FromDate: Calendar?): ByteArray {
            var FromDate = FromDate
            if (FromDate == null) {
                FromDate = Calendar.getInstance()
                FromDate!!.add(Calendar.DAY_OF_MONTH, -3)
            }
            val Request = HRHistoryHeader + "12" + GetCalendarValueInHex(FromDate, Calendar.MONTH) +
                    GetCalendarValueInHex(FromDate, Calendar.DAY_OF_MONTH) +
                    GetCalendarValueInHex(FromDate, Calendar.HOUR_OF_DAY) + "0012" +
                    GetCalendarValueInHex(FromDate, Calendar.MONTH) +
                    GetCalendarValueInHex(FromDate, Calendar.DAY_OF_MONTH) +
                    GetCalendarValueInHex(FromDate, Calendar.HOUR_OF_DAY) +
                    GetCalendarValueInHex(FromDate, Calendar.MINUTE)
            return hexStringToByteArray(Request)
        }
        fun SyncTime(SyncTime: Calendar?): ByteArray {
            var SyncTime = SyncTime
            if (SyncTime == null) SyncTime = Calendar.getInstance()
            val Request = TimeSyncHeader + GetCalendarValueInHex(SyncTime!!, Calendar.YEAR) +
                    GetCalendarValueInHex(SyncTime, Calendar.MONTH) +
                    GetCalendarValueInHex(SyncTime, Calendar.DAY_OF_MONTH) +
                    GetCalendarValueInHex(SyncTime, Calendar.HOUR_OF_DAY) +
                    GetCalendarValueInHex(SyncTime, Calendar.MINUTE) +
                    GetCalendarValueInHex(SyncTime, Calendar.SECOND)
            return hexStringToByteArray(Request)
        }
        fun requestSleepHistory(FromDate: Calendar): ByteArray{
            var request: String = SleepHistoryHeader
            request += GetCalendarValueInHex(FromDate, Calendar.MONTH)
            request += GetCalendarValueInHex(FromDate, Calendar.DAY_OF_MONTH)+"0000"
            return hexStringToByteArray(request)
        }

        fun EraseDatabase(): ByteArray {
            return hexStringToByteArray(EraseDataHeader)
        }
        fun RestoreToDefaults(): ByteArray {
            return hexStringToByteArray(RestoreCommandHeader)
        }
        fun SetAlarm(AlarmID: Long, IsEnabled: Boolean, Hour: Int, Minute: Int, Days: Int): ByteArray {
            var Command = AlarmHeader
            Command += SubIntegerConversionCheck(java.lang.Long.toHexString(AlarmID))
            if (IsEnabled) Command += "01" else Command += "00"
            Command += SubIntegerConversionCheck(Integer.toHexString(Hour))
            Command += SubIntegerConversionCheck(Integer.toHexString(Minute))
            Command += SubIntegerConversionCheck(Integer.toHexString(Days))
            return hexStringToByteArray(Command)
        }
        fun StopLongAlarm(): ByteArray {
            return hexStringToByteArray(StopLongAlarmHeader)
        }
        fun SetGyroAction(IsEnabled: Boolean): ByteArray {
            var Command = GyroActionCommandHeader
            if (IsEnabled) Command += "01" else Command = "00"
            return hexStringToByteArray(Command)
        }
        fun BuildNotify(Message: String): ByteArray {
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
                if (i < Msg.size && !(i>=Msg.size)) {
                    if (Msg[i - Offset] > 0)
                        Request += SubIntegerConversionCheck(Integer.toHexString(Msg[i - Offset].toInt()))
                    else
                        Request += SubIntegerConversionCheck(Integer.toHexString((Msg[i - Offset]).toInt() and 0xFF))
                } else
                    Request += "00"
            }
            return hexStringToByteArray(Request + "2E2E2E")
        }
        fun BuildLongNotify(Message: String): ByteArray {
            val MessageBytes = Message.toByteArray()
            var Lenght = 5 + MessageBytes.size//MAX 12 bytes
            if (Lenght > 17) Lenght = 17
            var Request = (LongMessageHeaderPartOne + SubIntegerConversionCheck(Integer.toHexString(Lenght))
                    + LongMessageHeaderPartTwo)
            for (i in 0..11) {
                if (i < MessageBytes.size) {
                    if (MessageBytes[i] > 0)
                        Request += SubIntegerConversionCheck(Integer.toHexString(MessageBytes[i].toInt()))
                    else
                        Request += SubIntegerConversionCheck(Integer.toHexString(MessageBytes[i].toInt() and 0xFF))
                } else break
            }
            return hexStringToByteArray(Request)
        }
        private fun CommandID14(Input: ByteArray) {
            if (Input[4].toInt() != 81 && Input[5].toInt() != 8) return
            var Buff: ByteBuffer
            Buff = ByteBuffer.wrap(Input, 7, 2)
            val Steps = Buff.short
            Buff = ByteBuffer.wrap(Input, 10, 2)
            val Calories = Buff.short
            Callback?.MainInfo(Steps.toInt(), Calories.toInt())
        }

        private fun HRRTHandler(Input: ByteArray) {
            if (Input[4].toInt() != -124 || Input[5].toInt() != -128) return
            Callback?.HRIncome(Calendar.getInstance(), Input[Input.size - 1].toInt())
        }
        private fun HRHistoryHandler(Input: ByteArray) {
            try {
                val CRecord = Calendar.getInstance()
                CRecord.set(CRecord.get(Calendar.YEAR), Input[7] - 1, Input[8].toInt(), Input[9].toInt(), Input[10].toInt())
                Callback?.HRHistoryRecord(CRecord, Input[11].toInt())
            } catch (Ex: Exception) {

            }

        }
        private fun MainHistoryHandler(Input: ByteArray) {
            if (Input[4].toInt() != 81 && Input[5].toInt() != 32) return
            val RecordTime = Calendar.getInstance()
            RecordTime.set(RecordTime.get(Calendar.YEAR), Input[7].toInt(), Input[8].toInt(), Input[9].toInt(), 0)
            var Buff = ByteBuffer.wrap(Input, 11, 2)
            val Steps = Buff.short
            Buff = ByteBuffer.wrap(Input, 14, 2)
            val Calories = Buff.short
            Callback?.MainHistoryRecord(RecordTime, Steps.toInt(), Calories.toInt())
        }

        private fun BatteryCommandHandler(Input: ByteArray) {
            Callback?.BatteryInfo(Input[Input.size - 1].toInt())
        }

        private fun SleepHistoryHandler(Input: ByteArray){
            val RecordTime = Calendar.getInstance()
            RecordTime.set(RecordTime.get(Calendar.YEAR), Input[7]-1, Input[8].toInt(),
                    Input[9].toInt(), Input[10].toInt(), 0)
            val type: Int = Input[11].toInt()
            val duration: Int = Input[13].toInt()
            Callback?.SleepHistoryRecord(RecordTime, duration, type)
        }

        fun CommandAction(Input: ByteArray) {
            val CommandID = Input[2]
            when (CommandID) {
                (14).toByte()// OE, Main Info
                -> CommandID14(Input)
                (9).toByte()//HR history
                -> HRHistoryHandler(Input)
                (5).toByte() -> BatteryCommandHandler(Input)
                (4).toByte() -> HRRTHandler(Input)
                (22).toByte() -> MainHistoryHandler(Input)
                (11).toByte() -> SleepHistoryHandler(Input)
            }//SleepHistoryHandler(Input);
        }
    }
}