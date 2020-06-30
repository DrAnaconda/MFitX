package anonymouls.dev.mgcex.app.backend

import anonymouls.dev.mgcex.app.main.SettingsActivity
import anonymouls.dev.mgcex.util.Utils
import java.nio.ByteBuffer
import java.util.*

class LM517CommandInterpreter : CommandInterpreter() {
    companion object {
        const val CodeName = "LM716"

        private const val syncTimeCommandHeader = "CD00091201010004"
        private const val longAlarmHeader = "CD0014120111000F"
        private const val notifyHeader = "CD002912011200"

        private const val UARTServiceUUIDString = "6e400001-b5a3-f393-e0a9-e50e24dcca9d"
        private const val UARTRXUUIDString = "6e400002-b5a3-f393-e0a9-e50e24dcca9d"
        private const val UARTTXUUIDString = "6e400003-b5a3-f393-e0a9-e50e24dcca9d"
        private const val UARTDescriptor = "00002902-0000-1000-8000-00805f9b34fb" // 00002902-0000-1000-8000-00805f9b34fb
        private const val PowerServiceString = "0000180f-0000-1000-8000-00805f9b34fb"
        private const val PowerTXString = "00002a19-0000-1000-8000-00805f9b34fb"
        private const val PowerTX2String = "00002a19-0000-0000-8000-00805f9b34fb"
        private const val PowerDescriptor = "00002902-0000-1000-8000-00805f9b34fb"
    }

    private var cancelTimer: Timer? = null

    init {
        UartService.RX_SERVICE_UUID = UUID.fromString(UARTServiceUUIDString)
        UartService.RX_CHAR_UUID = UUID.fromString(UARTRXUUIDString)
        UartService.TX_CHAR_UUID = UUID.fromString(UARTTXUUIDString)
        UartService.TXServiceDesctiptor = UUID.fromString(UARTDescriptor)
        UartService.PowerServiceUUID = UUID.fromString(PowerServiceString)
        UartService.PowerTXUUID = UUID.fromString(PowerTXString)
        UartService.PowerDescriptor = UUID.fromString(PowerDescriptor)
        this.hRRealTimeControlSupport = false
        this.vibrationSupport = true
        this.stepsTargetSettingSupport = true
        this.sittingReminderSupport = true
    }

    //region Helpers

    private fun createSpecialCalendar(): Calendar {
        val result = Calendar.getInstance()
        result.set(Calendar.MONTH, 10)
        result.set(Calendar.DAY_OF_MONTH, 8)
        result.set(Calendar.YEAR, 1991)
        result.set(Calendar.HOUR_OF_DAY, 0)
        result.set(Calendar.MINUTE, 0)
        result.set(Calendar.SECOND, 0)
        return result
    }

    private fun decryptDays(offset: Short, targetCalendar: Calendar?): Calendar {
        val result = targetCalendar ?: createSpecialCalendar()
        result.add(Calendar.DAY_OF_YEAR, offset.toInt())
        return result
    }

    private fun decryptTime(offset: Int, targetCalendar: Calendar): Calendar {
        targetCalendar.set(Calendar.HOUR_OF_DAY, 0)
        targetCalendar.set(Calendar.MINUTE, 0)
        targetCalendar.set(Calendar.SECOND, 0)
        targetCalendar.add(Calendar.SECOND, offset)
        return targetCalendar
    }

    private fun cutArray(Input: ByteArray, offset: Int): ByteArray {
        var end = offset
        for (x in 0 until 20) {
            if (Input[offset + x].toUByte() != (0).toUByte()) end++ else break
        }
        return Input.copyOfRange(offset, end)
    }

    //endregion

    //region Proceeders

    private fun hrRecordProceeder(Input: ByteArray) {
        // WARNING. Shit like pressure and ox% is ignoring
        if (Input.size != 20) return
        cancelTimer?.cancel(); cancelTimer?.purge()
        var buffer = ByteBuffer.wrap(Input, 8, 2)
        var recordTime = decryptDays(buffer.short, null)
        buffer = ByteBuffer.wrap(Input, 13, 4)
        recordTime = decryptTime(byteArrayToInt(buffer.array(), 13, 3), recordTime)
        val hrValue = Input[Input.size - 1]
        callback?.hrHistoryRecord(recordTime, hrValue.toInt())
    }

    private fun mainRecordProceeder(Input: ByteArray) {
        if (Input.size != 20) return
        var buffer = ByteBuffer.wrap(Input, 8, 2)
        val recordTime = decryptDays(buffer.short, null)
        recordTime.set(Calendar.HOUR_OF_DAY, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        recordTime.set(Calendar.MINUTE, Calendar.getInstance().get(Calendar.MINUTE))
        recordTime.set(Calendar.SECOND, Calendar.getInstance().get(Calendar.SECOND))

        buffer = ByteBuffer.wrap(Input, 12, 2)
        val steps = buffer.short.toInt()

        buffer = ByteBuffer.wrap(Input, 18, 2)
        val calories = buffer.short.toInt()
        this.callback?.mainHistoryRecord(recordTime, steps, calories)
    }

    private fun stepsSettingProceeder(Input: ByteArray) {
        if (Input.size != 11 && Input[3].toUByte() != (26).toUByte()) return
        val buffer = ByteBuffer.wrap(Input, Input.size - 2, 2)
        val steps = buffer.short
        Utils.getSharedPrefs(Algorithm.SelfPointer!!.baseContext).edit()
                .putInt(SettingsActivity.targetSteps, steps.toInt()).apply()
    }

    private fun commandsEntryPoint(Input: ByteArray) {
        val test = Utils.byteArrayToHexString(Input)
        if (Input[0].toUByte() != 205.toUByte()) return
        if (Input[1].toUByte() != 0.toUByte()) return
        //if (Input[2].toUByte() != 21.toUByte()) return
        when (Input[5].toUByte()) {
            (2).toUByte() -> stepsSettingProceeder(Input)
            (14).toUByte() -> hrRecordProceeder(Input)
            (12).toUByte() -> mainRecordProceeder(Input)
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
        var request = "CD:00:0A:12:01:09:00:05"
        request += if (IsEnabled) "0101" else "0001"
        request += Utils.subIntegerConversionCheck(Integer.toHexString(1048580))
        postCommand(hexStringToByteArray(request))
    }

    private fun buildNotify(Message: String): ByteArray {
        // TODO Investigate Types
        // 2 nd package up to 20 bytes
        // 3 rd up to 3?
        var request = notifyHeader + Message.length + "010000"
        return hexStringToByteArray(request + messageToHexValue(Message, 32))
    }

    override fun buildLongNotify(Message: String) {
        postCommand(hexStringToByteArray(longAlarmHeader + "0100" + messageToHexValue(Message, 10)))
    }

    override fun commandAction(Input: ByteArray, characteristic: UUID) {
        when (characteristic) {
            UUID.fromString(UARTTXUUIDString) -> {
                commandsEntryPoint(Input)
            }
            UUID.fromString(PowerTX2String), UUID.fromString(PowerTXString) -> {
                this.callback?.batteryInfo(Input[Input.size - 1].toInt())
            }
        }
        //TODO("Not yet implemented")
    }

    override fun getMainInfoRequest() {
        postCommand(hexStringToByteArray("cd:00:06:12:01:15:00:01:01"))
        Thread.sleep(1000)
        postCommand(hexStringToByteArray("cd:00:06:15:01:06:00:01:01"))
    }

    override fun requestSleepHistory(FromDate: Calendar) {
        //TODO("Not yet implemented")
    }

    override fun requestHRHistory(FromDate: Calendar?) {
        postCommand(hexStringToByteArray("CD:00:06:15:01:06:00:01:01"))
        // Warning! This device will send data ONCE and delete this record from device.
    }

    override fun hRRealTimeControl(Enable: Boolean) {
        return // NOT SUPPORTED
    }

    override fun setAlarm(AlarmID: Long, IsEnabled: Boolean, Hour: Int, Minute: Int, Days: Int) {
        //TODO("Not yet implemented")
    }

    override fun fireNotification(Input: String) {
        // TODO Dead fixes needed
        val Req = buildNotify(Input)
        var Req1 = Req.copyOfRange(0, 20)
        postCommand(Req1)
        var offset = 21
        do {
            Req1 = cutArray(Req, offset)
            Thread.sleep(50)
            postCommand(Req1)
            offset += 20
        } while (Req1.size == 20)

        Thread.sleep(50)
        Req1 = Req.copyOfRange(40, Req.size - 1) // TODO CHECK
        postCommand(Req1)
        Thread.sleep(50)
    }

    override fun requestBatteryStatus() {
        UartService.instance?.readCharacteristic(UartService.PowerServiceUUID, UartService.PowerTXUUID)
    }

    override fun requestManualHRMeasure(cancel: Boolean) {
        var request = "CD00061201180001"
        request += if (cancel) "00" else "01"
        postCommand(hexStringToByteArray(request))
        val taskForce = object : TimerTask() {
            override fun run() {
                requestManualHRMeasure(true); getMainInfoRequest()
            }
        }
        cancelTimer = Timer(); cancelTimer?.schedule(taskForce, 60000)
    }

    override fun setVibrationSetting(enabled: Boolean) {
        //TODO("Not yet implemented") UNK
    }

    override fun setTargetSteps(count: Int) {
        if (count < 500) return
        postCommand(hexStringToByteArray("CD:00:09:12:01:03:00:04:00:00" + Integer.toHexString(count)))
    }

    override fun setSittingReminder(enabled: Boolean) {
        var request = "CD:00:0D:12:01:05:00:08:00"
        request += if (enabled) "01" else "00"
        postCommand(hexStringToByteArray(request + "00:96:04:08:16:00"))
    }

    override fun requestSettings() {
//        buildLongNotify("+380505384503")
//        stopLongAlarm()
//        fireNotification("Hello.Hello?Hello!HIIIII")


        postCommand(hexStringToByteArray("CD:00:05:1A:01:02:00:00"))
    }
}