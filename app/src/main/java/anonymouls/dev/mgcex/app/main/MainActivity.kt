package anonymouls.dev.mgcex.app.main

import android.os.Bundle
import android.os.StrictMode
import androidx.appcompat.app.AppCompatActivity
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.main.ui.main.MainFragment

@ExperimentalStdlibApi
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectAll()
                .build())
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, MainFragment.newInstance())
                    .commitNow()
        }
    }
}