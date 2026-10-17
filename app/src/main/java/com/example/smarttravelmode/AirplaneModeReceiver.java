package com.example.smarttravelmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import java.lang.ref.WeakReference;

public class AirplaneModeReceiver extends BroadcastReceiver {

    public interface AirplaneModeListener {
        void onAirplaneModeChanged(boolean isOn);
    }

    private static WeakReference<AirplaneModeListener> activeListenerRef;
    private AirplaneModeListener instanceListener;

    public AirplaneModeReceiver() {
        // Required public default constructor for AndroidManifest registration
    }

    public AirplaneModeReceiver(MainActivity activity) {
        this.instanceListener = activity;
        activeListenerRef = new WeakReference<>(activity);
    }

    public AirplaneModeReceiver(AirplaneModeListener listener) {
        this.instanceListener = listener;
        activeListenerRef = new WeakReference<>(listener);
    }

    public static void setGlobalListener(AirplaneModeListener listener) {
        activeListenerRef = new WeakReference<>(listener);
    }

    public static void clearGlobalListener(AirplaneModeListener listener) {
        if (activeListenerRef != null && activeListenerRef.get() == listener) {
            activeListenerRef = null;
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ctx == null) return;
        boolean isOn = isAirplaneModeOn(ctx);
        if (instanceListener != null) {
            instanceListener.onAirplaneModeChanged(isOn);
        } else if (activeListenerRef != null && activeListenerRef.get() != null) {
            activeListenerRef.get().onAirplaneModeChanged(isOn);
        }
    }

    public static boolean isAirplaneModeOn(Context context) {
        if (context == null) return false;
        return Settings.Global.getInt(
                context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
}
