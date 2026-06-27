package id.zahra.studymate;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Periodically checks every local account and reminds unfinished quests once per day. */
public class DeadlineNotificationService extends JobService {
    private static final int JOB_ID=0x53544D;
    private static final String CHANNEL_ID="quest_deadline_reminders";

    static void schedule(Context context){
        createChannel(context);
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler!=null){
            JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(context,DeadlineNotificationService.class))
                    .setPeriodic(TimeUnit.HOURS.toMillis(6))
                    .setPersisted(true)
                    .build();
            scheduler.schedule(job);
        }
        scheduleDailyAlarm(context.getApplicationContext());
        new Thread(()->checkNow(context.getApplicationContext()),"deadline-check").start();
    }

    static void scheduleDailyAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(context, DeadlineBroadcastReceiver.class);
            intent.setAction("id.zahra.studymate.TRIGGER_REMINDER");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR_OF_DAY, 8);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            }
        }
    }

    private static void createChannel(Context context){
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.deadline_channel_name),NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.deadline_channel_description));
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(manager!=null)manager.createNotificationChannel(channel);
    }

    static void checkNow(Context context){
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        SharedPreferences global=context.getSharedPreferences("studymate",Context.MODE_PRIVATE);
        try{
            JSONArray accounts=new JSONArray(global.getString("accounts","[]"));
            for(int accountIndex=0;accountIndex<accounts.length();accountIndex++){
                JSONObject account=accounts.optJSONObject(accountIndex);if(account==null)continue;
                String email=account.optString("email","");if(email.isEmpty())continue;
                SharedPreferences user=context.getSharedPreferences(MainActivity.accountStoreName(email),Context.MODE_PRIVATE);
                JSONArray tasks=new JSONArray(user.getString("tasks","[]"));
                notifyUpcoming(context,user,email,account.optString("name","Petualang"),tasks);
            }
        }catch(Exception ignored){}
    }

    private static void notifyUpcoming(Context context,SharedPreferences user,String email,String accountName,JSONArray tasks){
        long now=System.currentTimeMillis();String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(now));
        NotificationManagerCompat notifications=NotificationManagerCompat.from(context);
        for(int index=0;index<tasks.length();index++){
            JSONObject task=tasks.optJSONObject(index);if(task==null||"Selesai".equals(task.optString("status")))continue;
            long deadline=task.optLong("deadline",0),remaining=deadline-now;
            if(!GameRules.shouldNotifyDeadline(now,deadline,task.optString("status")))continue;
            long taskId=task.optLong("id",index);String key="deadline_notified_"+taskId;
            if(today.equals(user.getString(key,"")))continue;
            long hours=Math.max(1,TimeUnit.MILLISECONDS.toHours(remaining));
            String remainingText=hours<24?"kurang dari 1 hari":Math.max(1,(long)Math.ceil(hours/24d))+" hari";
            String title=task.optString("title","Quest");String course=task.optString("course","");
            String message=title+" • deadline "+remainingText+(course.isEmpty()?"":" • "+course);
            Intent open=new Intent(context,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content=PendingIntent.getActivity(context,(email+taskId).hashCode(),open,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder builder=new NotificationCompat.Builder(context,CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_deadline)
                    .setContentTitle("Deadline quest semakin dekat")
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message+"\nAkun: "+accountName))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(content);
            try{notifications.notify((email+":"+taskId).hashCode(),builder.build());user.edit().putString(key,today).apply();}
            catch(SecurityException ignored){}
        }
    }

    @Override public boolean onStartJob(JobParameters params){
        new Thread(()->{checkNow(getApplicationContext());jobFinished(params,false);},"deadline-job").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params){return true;}
}
