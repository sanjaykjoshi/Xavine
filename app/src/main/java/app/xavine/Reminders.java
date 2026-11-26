package app.xavine;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;

/** Phone notifications for reminders that are due soon: daily at 9 am and when you get in the car. */
public final class Reminders extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            schedule(c);
        }
        notifyDue(c);
    }

    /** Daily check around 9 am. Safe to call often. */
    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 9);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR, 1);
        PendingIntent pi = PendingIntent.getBroadcast(c, 3,
                new Intent(c, Reminders.class).setAction("app.xavine.DAILY_CHECK"),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.setInexactRepeating(AlarmManager.RTC, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    /** Posts one notification per due reminder, at most once a day each. */
    public static void notifyDue(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel("reminders", "Car reminders",
                NotificationManager.IMPORTANCE_DEFAULT));
        int odo = Store.stats(c).latestOdo;
        PendingIntent open = PendingIntent.getActivity(c, 2,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        for (Store.Reminder r : Store.reminders(c)) {
            if (!Store.hasDue(r) || !Store.isDueSoon(r, odo)) continue;
            if (!Store.firstNoticeToday(c, r.id)) continue;
            Notification n = new Notification.Builder(c, "reminders")
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(r.name)
                    .setContentText("Due " + Store.describe(r, odo))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build();
            nm.notify((int) (r.id % 100000) + 100, n);
        }
    }
}
