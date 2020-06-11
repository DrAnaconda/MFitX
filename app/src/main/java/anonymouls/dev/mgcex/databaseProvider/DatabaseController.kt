package anonymouls.dev.mgcex.databaseProvider

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Environment
import anonymouls.dev.mgcex.util.HRAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class DatabaseController(context: Context, name: String, factory: SQLiteDatabase.CursorFactory?, version: Int) :
        SQLiteOpenHelper(context, name, null, version) {

    private fun checkCreateAllTables(db: SQLiteDatabase) {
        db.execSQL(HRRecordsTable.getCreateTableCommand())
        db.execSQL(AlarmsTable.GetCreateTableCommand())
        db.execSQL(NotifyFilterTable.getCreateTableCommand())
        db.execSQL(SleepSessionsTable.getCreateTableCommand())
        db.execSQL(SleepRecordsTable.getCreateTableCommand())
        db.execSQL(MainRecordsTable.getCreateTableCommand())
        db.execSQL(MainRecordsTable.getCreateTableCommandClone())
        db.execSQL(AdvancedActivityTracker.getCreateCommand())
    }

    private fun fixTableByDate(db: SQLiteDatabase, table: String) {
        val now = CustomDatabaseUtils.CalendarToLong(Calendar.getInstance(), true)
        val dateStr = if (table.toLowerCase().contains("sleep")) "Timestamp" else "Date"
        var curs = db.query(table, arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("COUNT", "*")), " $dateStr > ?",
                arrayOf(now.toString()), null, null, null, null)
        curs.moveToFirst()
        val brokenCount = curs.getInt(0)
        curs.close()
        curs = db.query(table, arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("COUNT", "*")), null, null, null, null, null)
        curs.moveToFirst()
        val overall = curs.getInt(0)
        curs.close()
        val brokenPercent = (brokenCount.toFloat() / overall.toFloat()) * 100.0f
        if (brokenPercent > 0 && brokenPercent < 5)
            db.delete(table, " DATE > ?", arrayOf(now.toString()))
        db.delete(table, " $dateStr < 100000000000 OR $dateStr > 999999999999", null)
    }

    private fun fixData(db: SQLiteDatabase) {
        fixTableByDate(db, MainRecordsTable.TableName)
        fixTableByDate(db, HRRecordsTableName)
        fixTableByDate(db, MainRecordsTable.TableName + "COPY")
        fixTableByDate(db, SleepRecordsTable.TableName)
        SleepRecordsTable.fixDurations(db)
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

    init {
        if (!File(DatabaseName).exists()) {
            File(DatabaseDir).mkdirs()
        }
    }

//region overloads

    override fun onCreate(db: SQLiteDatabase) {
        checkCreateAllTables(db)
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        super.onDowngrade(db, oldVersion, newVersion)
    }

    override fun onOpen(db: SQLiteDatabase?) {
        super.onOpen(db)
        if (db != null) {
            checkCreateAllTables(db)
            fixData(db)
            GlobalScope.launch(Dispatchers.IO) { SleepRecordsTable.executeAnalyze(db) }
            GlobalScope.launch(Dispatchers.IO) { HRAnalyzer.analyzeShadowMainData(db) }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion <= 1 && newVersion == VersionCodes.AdvancedTracking.code) db.execSQL("drop table if exists " + SleepSessionsTable.TableName)
        if (newVersion == VersionCodes.SyncFlags.code && oldVersion <= VersionCodes.AdvancedTracking.code) {
            db.execSQL("alter table " + MainRecordsTable.TableName + "COPY" + " add column " + MainRecordsTable.ColumnNamesCloneAdditional[0] + " boolean default false")
            db.execSQL("alter table " + MainRecordsTable.TableName + "COPY" + " add column " + MainRecordsTable.ColumnNamesCloneAdditional[1] + " boolean default false")
        }
        checkCreateAllTables(db)
    }

//endregion

    companion object {
        private enum class VersionCodes(val code: Int) { Default(0), AdvancedTracking(2), SyncFlags(3) }

        private var DatabaseDir = ""
        private var DatabaseName = ""
        const val AlarmsTableName = "Alarms"
        const val HRRecordsTableName = "HRRecords"
        const val NotifyFilterTableName = "Notify"

        var DCObject: DatabaseController? = null

        private fun nameResolver(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    DatabaseDir = context.applicationInfo.dataDir
                    DatabaseName = DatabaseDir + File.separator + "MGCEX.db"
                } else {
                    DatabaseDir = context.getExternalFilesDir(null)?.absolutePath + File.separator
                    DatabaseName = context.getExternalFilesDir(null)?.absolutePath + File.separator + "MGCEX.db"
                }
            } else {
                DatabaseDir = Environment.getExternalStorageDirectory().toString() + File.separator + "Android" + File.separator + "Data" + File.separator + "dev.anonymouls.MGCEX"
                DatabaseName = Environment.getExternalStorageDirectory().toString() + File.separator + "Android" + File.separator + "Data" + File.separator + "dev.anonymouls.MGCEX" + File.separator + "MGCEX.db"
            }
        }

        fun getDCObject(context: Context): DatabaseController {

            if (!File(DatabaseDir).exists()) File(DatabaseDir).mkdirs()
            if (DCObject == null) {
                nameResolver(context)
                DCObject = DatabaseController(context, DatabaseName, null, VersionCodes.SyncFlags.code)
            }
            return DCObject as DatabaseController
        }
    }
}