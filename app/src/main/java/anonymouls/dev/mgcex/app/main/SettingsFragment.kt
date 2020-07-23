package anonymouls.dev.mgcex.app.main

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.Algorithm
import anonymouls.dev.mgcex.app.backend.CommandInterpreter
import anonymouls.dev.mgcex.app.scanner.ScanFragment
import anonymouls.dev.mgcex.util.Utils.promptSimpleDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


@ExperimentalStdlibApi
class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var ci: CommandInterpreter
    private lateinit var prefs: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        proceedStaticContent()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        GlobalScope.launch(Dispatchers.Default) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context)
            proceedDynamicContent(); }
    }

    private fun createApproval(taskforce: ()->Any){
        promptSimpleDialog(this.requireActivity(),
                this.requireActivity().getString(R.string.confirmneeded), taskforce)
    }
    private fun clickListenerUniversal(key: String){
        when(key){
            SettingsActivity.bandAddress -> createApproval(deAuthDevice)
            "ResetToDefault" -> createApproval(sendResetCommand)
            "WipeSmartData" -> createApproval(sendEraseDatabaseCommand)
            // TODO List fragment +_notify filter
        }
    }
    private fun proceedStaticContent(){
        findPreference<Preference>(SettingsActivity.bandAddress)?.setOnPreferenceClickListener {
            clickListenerUniversal(SettingsActivity.bandAddress); true
        }
        findPreference<Preference>("ResetToDefault")?.setOnPreferenceClickListener {
            clickListenerUniversal("ResetToDefault"); true
        }
        findPreference<Preference>("WipeSmartData")?.setOnPreferenceClickListener {
            clickListenerUniversal("WipeSmartData"); true
        }
        findPreference<Preference>("NFilter")?.setOnPreferenceClickListener {
            clickListenerUniversal("NFilter"); true
        }
        if (prefs.contains("BandAddress"))
            findPreference<Preference>("BandAddress")?.title = this.requireContext().getString(R.string.current_connection) + prefs.getString("BandAddress", null)
        // TODO Extract strings???
    }
    private fun proceedDynamicContent(){
        ci = CommandInterpreter.getInterpreter(this.requireContext())
        findPreference<Preference>(SettingsActivity.vibrationSetting)?.isVisible = ci.vibrationSupport
        findPreference<Preference>(SettingsActivity.targetSteps)?.isVisible = ci.stepsTargetSettingSupport
        findPreference<Preference>(SettingsActivity.longSittingSetting)?.isVisible = ci.sittingReminderSupport
        findPreference<PreferenceCategory>("HRMon")?.isVisible = !ci.hRRealTimeControlSupport
    }

    private fun showNotConnectedErrorToast() {
        Toast.makeText(this.requireContext(), getString(R.string.connection_not_established), Toast.LENGTH_LONG).show()
    }


    //region actions

    private val deAuthDevice = {
        prefs.edit().remove(SettingsActivity.bandAddress).apply()
        prefs.edit().remove(SettingsActivity.bandIDConst).apply()
        Algorithm.IsActive = false
        Algorithm.updateStatusCode(Algorithm.StatusCodes.Dead)
        val frag = ScanFragment()
        val transaction = this.requireActivity().supportFragmentManager.beginTransaction()
        val fm = this.requireActivity().supportFragmentManager
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        transaction.disallowAddToBackStack()
        transaction.replace(R.id.container, frag)
        transaction.commit()
    }
    private val sendEraseDatabaseCommand = {
        if (Algorithm.StatusCode.value!!.code >= Algorithm.StatusCodes.Connected.code) {
             ci.eraseDatabase()
        } else {
            showNotConnectedErrorToast()
        }
    }
    private val sendResetCommand = {
        if (Algorithm.StatusCode.value!!.code >= Algorithm.StatusCodes.Connected.code) {
            ci.restoreToDefaults()
        } else {
            showNotConnectedErrorToast()
        }
    }

    //endregion

}