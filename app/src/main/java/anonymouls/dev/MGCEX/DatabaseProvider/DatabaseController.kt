package anonymouls.dev.MGCEX.DatabaseProvider

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import android.os.Handler
import android.os.Looper
import anonymouls.dev.MGCEX.App.Algorithm
import anonymouls.dev.MGCEX.util.HRAnalyzer
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
        db.execSQL(SleepSessionsTable.GetCreateTableCommand());
        db.execSQL(SleepRecordsTable.GetCreateTableCommand());
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

    private fun tempDataMerger( db: SQLiteDatabase){
        val tmp = SQLiteDatabase.openDatabase( Environment.getExternalStorageDirectory().toString()+File.separator+"MGCEX.db", null, SQLiteDatabase.OPEN_READONLY)
        val curs = tmp.query(MainRecordsTableName,
        arrayOf(MainRecordsTable.ColumnNames[1], MainRecordsTable.ColumnNames[2], MainRecordsTable.ColumnNames[3]),
        null, null, null, null, null)
        curs?.moveToFirst()
        do{
            val cc = ContentValues()
            cc.put(MainRecordsTable.ColumnNames[1], curs?.getLong(0))
            cc.put(MainRecordsTable.ColumnNames[2], curs?.getInt(1))
            cc.put(MainRecordsTable.ColumnNames[3], curs?.getInt(2))
            db.insert(MainRecordsTableName+"COPY", null, cc)
        }while (curs!!.moveToNext())
        curs?.close()
    }


    companion object {
        var DatabaseDir = Environment.getExternalStorageDirectory().toString() + File.separator + "Android" + File.separator + "Data" + File.separator + "dev.anonymouls.MGCEX"
        var DatabaseName = Environment.getExternalStorageDirectory().toString() + File.separator + "Android" + File.separator + "Data" + File.separator + "dev.anonymouls.MGCEX" + File.separator + "MGCEX.db"
        val SleepSessionTableName = "SleepSessions"
        val SleepRecordsTableName = "SleepRecords"
        val AlarmsTableName = "Alarms"
        val HRRecordsTableName = "HRRecords"
        val MainRecordsTableName = "MainRecords"
        val NotifyFilterTableName = "Notify"
        private val DatabaseVersion = 1

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