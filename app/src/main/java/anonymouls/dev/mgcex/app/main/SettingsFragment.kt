package anonymouls.dev.mgcex.app.main

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import anonymouls.dev.mgcex.app.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}