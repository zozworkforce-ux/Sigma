package com.nile.nursery;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.onesignal.OneSignal;
import com.onesignal.Continue;
import com.onesignal.debug.LogLevel;

public class NileApplication extends Application {

    public static final String ONESIGNAL_APP_ID = "19219beb-7b6e-45b5-a38e-4363ed2b688c";

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Enable Verbose Logging for debugging push events
        OneSignal.getDebug().setLogLevel(LogLevel.VERBOSE);

        // 2. Initialize OneSignal with Application context
        SharedPreferences prefs = getSharedPreferences("nursery_app_prefs", Context.MODE_PRIVATE);
        String appId = prefs.getString("onesignal_app_id", ONESIGNAL_APP_ID);
        if (appId == null || appId.trim().isEmpty()) {
            appId = ONESIGNAL_APP_ID;
        }

        try {
            OneSignal.initWithContext(this, appId.trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
