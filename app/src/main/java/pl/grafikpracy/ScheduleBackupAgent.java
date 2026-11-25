package pl.grafikpracy;

import android.app.backup.BackupAgentHelper;

public final class ScheduleBackupAgent extends BackupAgentHelper {
    @Override
    public void onRestoreFinished() {
        super.onRestoreFinished();
        // Calendar row IDs and permission prompt history belong to the original device.
        boolean saved = Store.prefs(this).edit()
                .remove("calendar_id")
                .remove("calendar_name")
                .remove("calendar_refresh")
                .remove("asked_calendar")
                .remove("asked_notifications")
                .commit();
        if (!saved) {
            throw new IllegalStateException("Nie udało się odłączyć kalendarza po przywróceniu kopii");
        }
    }
}
