package anonymouls.dev.mgcex.util

import android.app.TimePickerDialog
import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import anonymouls.dev.mgcex.app.R
import java.text.SimpleDateFormat
import java.util.*


class TimePreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    private var  calendar: Calendar = Calendar.getInstance()
    private lateinit var picker: TimePickerDialog

    private val targetTimeSet = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
        onDialogClosed(hourOfDay, minute)
    }

    private fun onDialogClosed(hour: Int, minute: Int) {
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            summary = summary
            if (callChangeListener(calendar.timeInMillis)) {
                persistLong(calendar.timeInMillis)
                notifyChanged()
            }
    }

    override fun onDependencyChanged(dependency: Preference?, disableDependent: Boolean) {
        this.isVisible = !disableDependent
    }

    override fun onClick() {
        picker = TimePickerDialog(context, targetTimeSet,
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
        picker.show()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        if (restoreValue) {
            calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
        } else {
            if (defaultValue == null) {
                calendar.timeInMillis = System.currentTimeMillis()
            } else {
                calendar.timeInMillis = (defaultValue as String).toLong()
            }
        }
        summary = summary
    }

    override fun getSummary(): CharSequence {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)+
                context.getString(R.string.unlimited_monitor)
    }

}

class RedifinedEditTextPreference(context: Context, attrs: AttributeSet): EditTextPreference(context, attrs){
    override fun onDependencyChanged(dependency: Preference?, disableDependent: Boolean) {
        this.isVisible = !disableDependent
    }
}

class RedifinedSwitchPreference(context: Context, attrs: AttributeSet): SwitchPreferenceCompat(context, attrs){
    override fun onDependencyChanged(dependency: Preference?, disableDependent: Boolean) {
        this.isVisible = !disableDependent
    }
}

class RedifinedPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs){
    override fun onDependencyChanged(dependency: Preference?, disableDependent: Boolean) {
        this.isVisible = !disableDependent
    }
}