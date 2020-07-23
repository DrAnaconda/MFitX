package anonymouls.dev.mgcex.app.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.main.ui.main.MainFragment
import anonymouls.dev.mgcex.app.scanner.ScanFragment
import anonymouls.dev.mgcex.util.Utils

@ExperimentalStdlibApi
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            if (Utils.getSharedPrefs(this.baseContext).contains("BandAddress")) {
                supportFragmentManager.beginTransaction()
                        .replace(R.id.container, MainFragment.newInstance())
                        .commitNow()
            } else {
                supportFragmentManager.beginTransaction()
                        .replace(R.id.container, ScanFragment.newInstance())
                        .commitNow()
            }
        }
    }
}