package com.example.smarttravelmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class AirplaneModeReceiver extends BroadcastReceiver {

    private final MainActivity activity;

    public AirplaneModeReceiver(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        boolean isOn = Settings.Global.getInt(
                ctx.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        activity.onAirplaneModeChanged(isOn);
    }
}
