@file:Suppress("NAME_SHADOWING")

package anonymouls.dev.mgcex.app.backend

import android.content.Context
import android.widget.Toast
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.main.SettingsActivity
import anonymouls.dev.mgcex.util.Utils
import java.util.*

abstract class CommandInterpreter {
    interface CommandReaction {
        fun mainInfo(Steps: Int, Calories: Int)
        fun batteryInfo(Charge: Int)
        fun hrIncome(Time: Calendar, HRValue: Int)
        fun hrHistoryRecord(Time: Calendar, HRValue: Int)
        fun mainHistoryRecord(Time: Calendar, Steps: Int, Calories: Int)
        fun sleepHistoryRecord(Time: Calendar, Duration: Int, Type: Int)
    }

    var callback: CommandReaction? = null

    //region Special Features

    var hRRealTimeControlSupport = false
    var stepsTargetSettingSupport = false
    var sittingReminderSupport = false
    var vibrationSupport = false

    //endregion

    companion object {
        private var ActiveInterpreter: CommandInterpreter? = null

        fun getInterpreter(context: Context): CommandInterpreter {
            if (ActiveInterpreter == null) {
                if (Utils.getSharedPrefs(context).contains(SettingsActivity.bandIDConst)) {
                    when (Utils.getSharedPrefs(context).getString(SettingsActivity.bandIDConst, "")?.toUpperCase()) {
                        MGCOOL4CommandInterpreter.CodeName -> ActiveInterpreter = MGCOOL4CommandInterpreter()
                        LM517CommandInterpreter.CodeName -> ActiveInterpreter = LM517CommandInterpreter()
                        else -> Toast.makeText(context,
                                context.getString(R.string.device_not_supported),
                                Toast.LENGTH_LONG).show()
                    }
                }
            }
            if (ActiveInterpreter == null) ActiveInterpreter = MGCOOL4CommandInterpreter()
            return ActiveInterpreter!!
        }
    }

    protected fun hexStringToByteArray(s: String): ByteArray {
        var s = s
        s = s.toUpperCase(Locale.ROOT)
        s = s.replace(":", "")
        s = s.trim()
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

    protected fun byteArrayToInt(b: ByteArray, offset: Int, count: Int): Int {
        var value = 0
        var shift = (count - 1) * 8
        for (i in 0..3) {
            if (i > count || shift < 0) break
            if (shift > 0)
                value += ((b[i + offset].toInt() and 0xFF) shl shift)
            else
                value += b[i + offset].toInt() and 0xFF
            shift -= 8
        }
        return value.toInt()
    }

    fun messageToHexValue(Message: String, limiter: Int, fillFreeSpace: Boolean = true): String {
        var result = ""
        for (x in 0 until limiter) {
            if (x < Message.length) {
                if (Message[x].toInt() > 0)
                    result += Utils.subIntegerConversionCheck(Integer.toHexString(Message[x].toInt())) + ':'
                else
                    result += Utils.subIntegerConversionCheck(Integer.toHexString(Message[x].toInt() and 0xFF)) + ':'
            } else {
                if (fillFreeSpace) result += "00:"; else return result
            }
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
    abstract fun requestManualHRMeasure(cancel: Boolean)
    abstract fun setVibrationSetting(enabled: Boolean)
    abstract fun setTargetSteps(count: Int)
    abstract fun setSittingReminder(enabled: Boolean)
    abstract fun requestSettings()
}