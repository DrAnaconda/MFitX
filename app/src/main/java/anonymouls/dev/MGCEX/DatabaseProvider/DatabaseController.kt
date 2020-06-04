package anonymouls.dev.MGCEX.DatabaseProvider

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Environment
import java.io.File
import java.util.*

class DatabaseController(context: Context, name: String, factory: SQLiteDatabase.CursorFactory?, version: Int) : 
	SQLiteOpenHelper(context, DatabaseName, null, DatabaseVersion) 
	{
    private var CurrentDataBase: SQLiteDatabase? = null

    val currentDataBase: SQLiteDatabase?
        get() {
            if (CurrentDataBase == null) {
                CurrentDataBase = this.writableDatabase
                fixData(CurrentDataBase!!)
            }
            return CurrentDataBase
        }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(HRRecordsTable.GetCreateTableCommand())
        db.execSQL(AlarmsTable.GetCreateTableCommand())
        db.execSQL(NotifyFilterTable.GetCreateTableCommand())
        db.execSQL(SleepSessionsTable.GetCreateTableCommand())
        db.execSQL(SleepRecordsTable.GetCreateTableCommand())
        db.execSQL(MainRecordsTable.GetCreateTableCommand())
        db.execSQL(MainRecordsTable.getCreateTableCommandClone())
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }
    private fun fixTableByDate(db:SQLiteDatabase, table: String){
        val now = CustomDatabaseUtils.CalendarToLong(Calendar.getInstance(), true)
        var curs = db.query(table, arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("COUNT","*")), " Date > ?",
                arrayOf(now.toString()), null, null, null, null)
        curs.moveToFirst()
        val brokenCount = curs.getInt(0)
        curs.close()
        curs = db.query(table, arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("COUNT","*")), null, null, null, null, null)
        curs.moveToFirst()
        val overall = curs.getInt(0)
        curs.close()
        val brokenPercent = (brokenCount.toFloat()/overall.toFloat())*100.0f
        if (brokenPercent > 0 && brokenPercent < 5)
            db.delete(table, " DATE > ?", arrayOf(now.toString()))
        db.delete(table, " DATE < 100000000000 OR DATE > 999999999999", null)
    }
    private fun fixData(db: SQLiteDatabase){
        fixTableByDate(db, MainRecordsTableName)
        fixTableByDate(db, HRRecordsTableName)
        fixTableByDate(db, MainRecordsTableName+"COPY")
        SleepRecordsTable.fixDurations(db)
    }

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    DatabaseDir = context.applicationInfo.dataDir
                    DatabaseName = DatabaseDir + File.separator + "MGCEX.db"
                } else {
                    DatabaseDir = context.getExternalFilesDir(null)?.absolutePath + File.separator
                    DatabaseName = context.getExternalFilesDir(null)?.absolutePath + File.separator + "MGCEX.db"
                }
            }

            if (!File(DatabaseName).exists()) {
                File(DatabaseDir).mkdirs()
                File(DatabaseName).mkdirs()
                val newDatabase = SQLiteDatabase.openOrCreateDatabase(DatabaseName, null)
                onCreate(newDatabase)
                CurrentDataBase = newDatabase
            }
            CurrentDataBase = SQLiteDatabase.openOrCreateDatabase(DatabaseName, null)
        }

        fun migrateToExternal(activity: Activity?) {
            val newDatabaseDir = Environment.getExternalStorageDirectory().toString() + File.separator + "Android" + File.separator + "Data" + File.separator + "dev.anonymouls.MGCEX"
            val newDatabaseName = Environment.getExternalStorageDirectory().toString() + File.separator + "Android" + File.separator + "Data" + File.separator + "dev.anonymouls.MGCEX" + File.separator + "MGCEX.db"
            if (!File(newDatabaseName).exists()) {
                File(newDatabaseDir).mkdirs()
                File(newDatabaseDir).mkdir()
                File(DatabaseName).renameTo(File(newDatabaseName))
            }
        }

    companion object {
        var DatabaseDir = Environment.getExternalStorageDirectory().toString() + File.separator + "Android" + File.separator + "Data" + File.separator + "dev.anonymouls.MGCEX"
        var DatabaseName = Environment.getExternalStorageDirectory().toString() + File.separator + "Android" + File.separator + "Data" + File.separator + "dev.anonymouls.MGCEX" + File.separator + "MGCEX.db"
        const val SleepSessionTableName = "SleepSessions"
        const val SleepRecordsTableName = "SleepRecords"
        const val AlarmsTableName = "Alarms"
        const val HRRecordsTableName = "HRRecords"
        const val MainRecordsTableName = "MainRecords"
        const val NotifyFilterTableName = "Notify"
        private const val DatabaseVersion = 1

        var DCObject: DatabaseController? = null

        fun getDCObject(context: Context): DatabaseController {

            if (!File(DatabaseDir).exists()) File(DatabaseDir).mkdirs()
            if (DCObject == null) {
                DCObject = DatabaseController(context, DatabaseName, null, 0)
            }
            return DCObject as DatabaseController
        }
    }
}