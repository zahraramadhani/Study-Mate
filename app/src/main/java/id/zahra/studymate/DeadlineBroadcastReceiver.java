package id.zahra.studymate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DeadlineBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "id.zahra.studymate.TRIGGER_REMINDER".equals(action)) {
            
            // Run the check in the background
            new Thread(() -> {
                DeadlineNotificationService.checkNow(context.getApplicationContext());
            }, "deadline-check-alarm").start();
            
            // Re-schedule the daily 8:00 AM alarm for the next day
            DeadlineNotificationService.scheduleDailyAlarm(context.getApplicationContext());
        }
    }
}
