package anonymouls.dev.MGCEX.App;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import anonymouls.dev.MGCEX.App.CommandInterpreter.CommandReaction;
import anonymouls.dev.MGCEX.DatabaseProvider.DatabaseController;
import anonymouls.dev.MGCEX.DatabaseProvider.HRRecordsTable;
import anonymouls.dev.MGCEX.DatabaseProvider.MainRecordsTable;
import java.util.Calendar;
import kotlin.Metadata;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(
        mv = {1, 1, 16},
        bv = {1, 0, 3},
        k = 1,
        d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\b\b\u0000\u0018\u0000 \u00142\u00020\u0001:\u0001\u0014B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0002\u0010\u0004J\u0010\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0016J\u0018\u0010\u000b\u001a\u00020\b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\nH\u0016J\u0018\u0010\u000f\u001a\u00020\b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\nH\u0016J \u0010\u0010\u001a\u00020\b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0011\u001a\u00020\n2\u0006\u0010\u0012\u001a\u00020\nH\u0016J\u0018\u0010\u0013\u001a\u00020\b2\u0006\u0010\u0011\u001a\u00020\n2\u0006\u0010\u0012\u001a\u00020\nH\u0016R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006\u0015"},
        d2 = {"Lanonymouls/dev/MGCEX/App/CommandCallbacks;", "Lanonymouls/dev/MGCEX/App/CommandInterpreter$CommandReaction;", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "Database", "Landroid/database/sqlite/SQLiteDatabase;", "BatteryInfo", "", "Charge", "", "HRHistoryRecord", "Time", "Ljava/util/Calendar;", "HRValue", "HRIncome", "MainHistoryRecord", "Steps", "Calories", "MainInfo", "Companion", "app"}
)
public final class CommandCallbacks implements CommandReaction {
    private final SQLiteDatabase Database;
    @NotNull
    public static CommandCallbacks SelfPointer;
    public static final CommandCallbacks.Companion Companion = new CommandCallbacks.Companion((DefaultConstructorMarker)null);

    public void MainInfo(int Steps, int Calories) {
        try {
            MainRecordsTable var10000 = MainRecordsTable.INSTANCE;
            Calendar var10001 = Calendar.getInstance();
            Intrinsics.checkExpressionValueIsNotNull(var10001, "Calendar.getInstance()");
            var10000.InsertRecord(var10001, Steps, Calories, this.Database);
            Algorithm.Companion.setLastStepsIncomed(Steps);
            Algorithm.Companion.setLastCcalsIncomed(Calories);
        } catch (Exception var4) {
            Log.e("ContentValues", var4.getMessage());
        }

    }

    public void BatteryInfo(int Charge) {
        try {
            Algorithm.Companion.setBatteryHolder(Charge);
        } catch (Exception var3) {
        }

    }

    public void HRIncome(@NotNull Calendar Time, int HRValue) {
        Intrinsics.checkParameterIsNotNull(Time, "Time");
        int ResultHR = HRValue;
        if (HRValue < 0) {
            ResultHR = HRValue & 255;
        }

        if (Algorithm.Companion.getIsAlarmWaiting()) {
            Algorithm.Companion.getSelfPointer().AlarmTriggerDecider(ResultHR);
        }

        Algorithm.Companion.setLastHearthRateIncomed(ResultHR);
    }

    public void HRHistoryRecord(@NotNull Calendar Time, int HRValue) {
        Intrinsics.checkParameterIsNotNull(Time, "Time");

        try {
            int ResultHR = HRValue;
            if (HRValue < 0) {
                ResultHR = HRValue & 255;
            }

            HRRecordsTable.INSTANCE.InsertRecord(Time, ResultHR, this.Database);
            Calendar var10000 = Calendar.getInstance();
            Intrinsics.checkExpressionValueIsNotNull(var10000, "Calendar.getInstance()");
            Calendar CTime = var10000;
            long var5 = CTime.getTimeInMillis() - Time.getTimeInMillis();
            if (Math.abs(var5) < (long)905000) {
                Algorithm.Companion.setLastHearthRateIncomed(ResultHR);
            }

            if (Algorithm.Companion.getIsAlarmWaiting()) {
                Algorithm.Companion.getSelfPointer().AlarmTriggerDecider(ResultHR);
            }
        } catch (Exception var7) {
            Log.e("ContentValues", var7.getMessage());
        }

    }

    public void MainHistoryRecord(@NotNull Calendar Time, int Steps, int Calories) {
        Intrinsics.checkParameterIsNotNull(Time, "Time");
        if (Steps > 0 && Calories > 0) {
            try {
                MainRecordsTable.INSTANCE.InsertRecord(Time, Steps, Calories, this.Database);
                Algorithm.Companion.setLastCcalsIncomed(Calories);
                Algorithm.Companion.setLastStepsIncomed(Steps);
            } catch (Exception var5) {
                Log.e("ContentValues", var5.getMessage());
            }

        }
    }

    public CommandCallbacks(@NotNull Context context) {
        Intrinsics.checkParameterIsNotNull(context, "context");
        super();
        SQLiteDatabase var10001 = DatabaseController.Companion.getDCObject(context).getCurrentDataBase();
        if (var10001 == null) {
            Intrinsics.throwNpe();
        }

        this.Database = var10001;
    }

    @Metadata(
            mv = {1, 1, 16},
            bv = {1, 0, 3},
            k = 1,
            d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002R\u001a\u0010\u0003\u001a\u00020\u0004X\u0086.¢\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\b¨\u0006\t"},
            d2 = {"Lanonymouls/dev/MGCEX/App/CommandCallbacks$Companion;", "", "()V", "SelfPointer", "Lanonymouls/dev/MGCEX/App/CommandCallbacks;", "getSelfPointer", "()Lanonymouls/dev/MGCEX/App/CommandCallbacks;", "setSelfPointer", "(Lanonymouls/dev/MGCEX/App/CommandCallbacks;)V", "app"}
    )
    public static final class Companion {
        @NotNull
        public final CommandCallbacks getSelfPointer() {
            CommandCallbacks var10000 = CommandCallbacks.SelfPointer;
            if (var10000 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("SelfPointer");
            }

            return var10000;
        }

        public final void setSelfPointer(@NotNull CommandCallbacks var1) {
            Intrinsics.checkParameterIsNotNull(var1, "<set-?>");
            CommandCallbacks.SelfPointer = var1;
        }

        private Companion() {
        }

        // $FF: synthetic method
        public Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}
