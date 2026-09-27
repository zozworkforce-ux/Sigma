package com.nile.nursery;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationSyncReceiver extends BroadcastReceiver {

    public static final String ACTION_CHECK_NOTIFS = "com.nile.nursery.CHECK_NOTIFICATIONS";
    private static final String NOTIF_CHANNEL_ID = "sigma_labs_notifications";
    private static final String PREFS_NAME = "sigma_labs_prefs";
    private static final String BASE_URL = "https://sigma.great-site.net/";
    private static final long INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    @Override
    public void onReceive(Context context, Intent intent) {
        // Run network check in a background thread
        new Thread(() -> {
            try {
                performNotificationCheck(context.getApplicationContext());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Schedule next alarm to ensure continuous periodic background checks
                schedulePeriodicSync(context.getApplicationContext());
            }
        }).start();
    }

    public static void schedulePeriodicSync(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Intent intent = new Intent(context, NotificationSyncReceiver.class);
            intent.setAction(ACTION_CHECK_NOTIFS);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 2001, intent, flags);
            long triggerAtMillis = System.currentTimeMillis() + INTERVAL_MS;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void performNotificationCheck(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedCookie = prefs.getString("user_cookie", "");
        String userType = prefs.getString("user_type", "");
        String accountId = prefs.getString("account_id", "");
        int lastNotifiedId = prefs.getInt("last_notified_id", 0);

        // If no user has ever logged in, skip checking
        if (savedCookie.isEmpty() && accountId.isEmpty()) {
            return;
        }

        HttpURLConnection conn = null;
        try {
            String requestUrl = BASE_URL + "api/get_notifications.php?app_user_type=" + userType +
                    "&app_account_id=" + accountId + "&t=" + System.currentTimeMillis();

            URL url = new URL(requestUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) NileNurseryApp/1.0");
            conn.setRequestProperty("Accept", "application/json");

            if (!savedCookie.isEmpty()) {
                conn.setRequestProperty("Cookie", savedCookie);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                if ("success".equals(json.optString("status"))) {
                    JSONArray notifs = json.optJSONArray("notifications");
                    if (notifs != null && notifs.length() > 0) {
                        int highestId = lastNotifiedId;

                        for (int i = 0; i < notifs.length(); i++) {
                            JSONObject item = notifs.getJSONObject(i);
                            int id = item.optInt("id", 0);
                            int isRead = item.optInt("is_read", 0);

                            if (id > lastNotifiedId && isRead == 0) {
                                String title = item.optString("title", "إشعار جديد من معامل سيجما");
                                String message = item.optString("message", "");
                                String link = item.optString("link", "");

                                showSystemNotification(context, id, title, message, link);

                                if (id > highestId) {
                                    highestId = id;
                                }
                            }
                        }

                        if (highestId > lastNotifiedId) {
                            prefs.edit().putInt("last_notified_id", highestId).apply();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void showSystemNotification(Context context, int notifId, String title, String message, String targetUrl) {
        try {
            ensureNotificationChannel(context);

            Intent intent = new Intent(context, MainActivity.class);
            if (targetUrl != null && !targetUrl.isEmpty()) {
                String fullUrl = targetUrl.startsWith("http") ? targetUrl : BASE_URL + targetUrl;
                intent.putExtra("target_url", fullUrl);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    notifId,
                    intent,
                    flags
            );

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                            .setSmallIcon(R.drawable.logo_sigma)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .setDefaults(NotificationCompat.DEFAULT_ALL)
                            .setContentIntent(pendingIntent);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(notifId, builder.build());
                }
            } else {
                notificationManager.notify(notifId, builder.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "إشعارات معامل سيجما للتحاليل الطبية";
            String description = "نتائج التحاليل والعروض وتنبيهات الحجوزات الطبية";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(NOTIF_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
