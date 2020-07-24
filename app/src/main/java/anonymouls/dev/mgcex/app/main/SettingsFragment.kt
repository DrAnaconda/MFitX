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
import anonymouls.dev.mgcex.app.backend.NotificationService
import anonymouls.dev.mgcex.app.data.DataFragment
import anonymouls.dev.mgcex.app.scanner.ScanFragment
import anonymouls.dev.mgcex.util.DialogHelpers.promptSimpleDialog
import anonymouls.dev.mgcex.util.PreferenceListener
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

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        GlobalScope.launch(Dispatchers.Default) {
            proceedDynamicContent()
        }
    }
    override fun onAttach(context: Context) {
        super.onAttach(context)
        GlobalScope.launch(Dispatchers.Default) {
                prefs = PreferenceManager.getDefaultSharedPreferences(context)
                ci = CommandInterpreter.getInterpreter(context)
        }
    }

    override fun onResume() {
        super.onResume()
        proceedNotify()
    }

    private fun createApproval(taskforce: ()->Any){
        promptSimpleDialog(this.requireActivity(),
                getString(R.string.warning),
                this.requireActivity().getString(R.string.confirmneeded), android.R.drawable.stat_sys_warning, taskforce)
    }
    private fun switchToDataFragment(){
        val transaction = this.requireActivity().supportFragmentManager.beginTransaction()
        transaction.addToBackStack(null)
        transaction.replace(R.id.container, DataFragment.newInstance(DataFragment.DataTypes.Applications))
        transaction.commit()
    }
    private fun clickListenerUniversal(key: String){
        when(key){
            PreferenceListener.Companion.PrefsConsts.bandAddress -> createApproval(deAuthDevice)
            "ResetToDefault" -> createApproval(sendResetCommand)
            "WipeSmartData" -> createApproval(sendEraseDatabaseCommand)
            "NFilter" -> switchToDataFragment()
        }
    }
    private fun proceedStaticContent(){
        findPreference<Preference>(PreferenceListener.Companion.PrefsConsts.bandAddress)?.setOnPreferenceClickListener {
            clickListenerUniversal(PreferenceListener.Companion.PrefsConsts.bandAddress); true
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
        // TODO Extract strings???
    }
    private fun proceedDynamicContent(){
        while(!this::ci.isInitialized || !this::prefs.isInitialized) continue
        if (prefs.contains("BandAddress"))
            findPreference<Preference>("BandAddress")?.title = this.requireContext().getString(R.string.current_connection) + prefs.getString("BandAddress", null)
        findPreference<Preference>(PreferenceListener.Companion.PrefsConsts.vibrationSetting)?.isVisible = ci.vibrationSupport
        findPreference<Preference>(PreferenceListener.Companion.PrefsConsts.targetSteps)?.isVisible = ci.stepsTargetSettingSupport
        findPreference<Preference>(PreferenceListener.Companion.PrefsConsts.longSittingSetting)?.isVisible = ci.sittingReminderSupport
        findPreference<PreferenceCategory>("HRMon")?.isVisible = !ci.hRRealTimeControlSupport
        proceedNotify()
    }
    private fun proceedNotify(){
        prefs.edit().putBoolean("bindNotifyService",NotificationService.instance != null).apply()
        findPreference<Preference>("bindNotifyService")?.isVisible = NotificationService.instance == null
    }

    private fun showNotConnectedErrorToast() {
        Toast.makeText(this.requireContext(), getString(R.string.connection_not_established), Toast.LENGTH_LONG).show()
    }



    //region Bracelet Actions

    private val deAuthDevice = {
        prefs.edit().remove(PreferenceListener.Companion.PrefsConsts.bandAddress).apply()
        prefs.edit().remove(PreferenceListener.Companion.PrefsConsts.bandIDConst).apply()
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