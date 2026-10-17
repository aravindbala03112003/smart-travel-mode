package com.example.smarttravelmode;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smarttravelmode.databinding.ActivityMainBinding;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnSuccessListener;

import android.text.util.Linkify;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 1001;
    private static final int REQUEST_CHECK_SETTINGS = 1002;

    private static final String ACTION_SMS_SENT = "com.example.smarttravelmode.SMS_SENT";
    private static final String ACTION_SMS_DELIVERED = "com.example.smarttravelmode.SMS_DELIVERED";

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET
    };

    private ActivityMainBinding binding;
    private ExecutorService executor;
    private AirplaneModeReceiver airplaneModeReceiver;

    private final BroadcastReceiver smsSentReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int rc = getResultCode();
            switch (rc) {
                case RESULT_OK:
                    Toast.makeText(MainActivity.this, "SMS sent", Toast.LENGTH_SHORT).show();
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    Toast.makeText(MainActivity.this, "SMS: generic failure", Toast.LENGTH_LONG).show();
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    Toast.makeText(MainActivity.this, "SMS: no service", Toast.LENGTH_LONG).show();
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    Toast.makeText(MainActivity.this, "SMS: null PDU", Toast.LENGTH_LONG).show();
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    Toast.makeText(MainActivity.this, "SMS: radio off", Toast.LENGTH_LONG).show();
                    break;
                default:
                    Toast.makeText(MainActivity.this, "SMS result " + rc, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        executor = Executors.newFixedThreadPool(3);
        binding.statusText.setText("Ready to monitor airplane mode");

        setupUi();
        requestAllPermissions();

        // Register SENT/DELIVERED trackers (unregister in onDestroy)
        registerReceiver(smsSentReceiver, new IntentFilter(ACTION_SMS_SENT));
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                Toast.makeText(MainActivity.this, "SMS delivered", Toast.LENGTH_SHORT).show();
            }
        }, new IntentFilter(ACTION_SMS_DELIVERED));
    }

    private void setupUi() {
        binding.testButton.setOnClickListener(v -> testNotifications());
        binding.settingsFab.setOnClickListener(v ->
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show());

        binding.locationSwitch.setOnCheckedChangeListener((btn, on) -> {
            if (on) checkLocationSettings();
            else {
                binding.locationText.setAutoLinkMask(0);
                binding.locationText.setText("Location disabled");
            }
        });
    }

    private void requestAllPermissions() {
        boolean all = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                all = false; break;
            }
        }
        if (!all) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
        } else {

            registerAirplaneReceiver();
        }
    }

    private void registerAirplaneReceiver() {
        airplaneModeReceiver = new AirplaneModeReceiver(this);
        registerReceiver(airplaneModeReceiver, new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == PERMISSIONS_REQUEST_CODE) {
            boolean granted = true;
            for (int r : res) { if (r != PackageManager.PERMISSION_GRANTED) { granted = false; break; } }
            if (granted) {
                registerAirplaneReceiver();
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Called by AirplaneModeReceiver
    public void onAirplaneModeChanged(boolean isOn) {
        runOnUiThread(() -> {
            if (isOn) {
                binding.statusText.setText("✈️ Airplane ON – boarding sent");
                binding.statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.orange_500));
                sendBoarding();
            } else {
                binding.statusText.setText("📱 Airplane OFF – landed sending…");
                binding.statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.green_500));
                sendLanding();
            }
        });
    }

    // ---------- Maps helpers ----------
    // Universal Maps URL works in SMS/email and opens in Maps if available [21][22]
    private String mapsUrl(double lat, double lon) {
        return "https://maps.google.com/?q=" + lat + "," + lon;
    }
    private String buildLocationMessage(String prefix, double lat, double lon) {
        return prefix + "\n\n" +
                String.format("Coordinates: %.6f, %.6f\n", lat, lon) +
                "Open in Google Maps: " + mapsUrl(lat, lon);
    }
    private void showLocationInCard(double lat, double lon) {
        String url = mapsUrl(lat, lon);
        binding.locationText.setText(String.format("📍 %.6f, %.6f\n%s", lat, lon, url));
        Linkify.addLinks(binding.locationText, Linkify.WEB_URLS); // clickable link [23][24]
    }

    // ---------- Boarding / Landing ----------
    private void sendBoarding() {
        executor.execute(() -> {
            String to = binding.familyNumberEdit.getText().toString().trim();
            String em = binding.emailEdit.getText().toString().trim();
            if (binding.smsSwitch.isChecked() && !to.isEmpty())
                safeSendSMS(to, "Boarding flight ✈️ – will be unreachable until landing.");
            if (binding.emailSwitch.isChecked() && !em.isEmpty())
                sendEmail(em, "Boarding Flight", "I'm boarding now ✈️ and will contact after landing.");
        });
    }

    private void sendLanding() {
        executor.execute(() -> {
            // Give the modem time to reattach after Airplane OFF
            try { Thread.sleep(7000); } catch (InterruptedException ignored) {}
            String to = binding.familyNumberEdit.getText().toString().trim();
            String em = binding.emailEdit.getText().toString().trim();
            if (binding.locationSwitch.isChecked()) {
                fetchLocationAndNotify(to, em);
            } else {
                if (binding.smsSwitch.isChecked() && !to.isEmpty())
                    safeSendSMS(to, "Landed safely 🛬");
                if (binding.emailSwitch.isChecked() && !em.isEmpty())
                    sendEmail(em, "Landed Safely", "I have landed safely 🛬");
            }
        });
    }

    // ---------- Location fetch then notify ----------
    private void checkLocationSettings() {
        LocationRequest req = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10_000)
                .setFastestInterval(5_000);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(req);
        SettingsClient client = LocationServices.getSettingsClient(this);

        client.checkLocationSettings(builder.build())
                .addOnSuccessListener((OnSuccessListener<LocationSettingsResponse>) response ->
                        fetchLocationAndNotify(
                                binding.familyNumberEdit.getText().toString().trim(),
                                binding.emailEdit.getText().toString().trim()))
                .addOnFailureListener(e -> {
                    if (e instanceof ResolvableApiException) {
                        try { ((ResolvableApiException) e).startResolutionForResult(this, REQUEST_CHECK_SETTINGS); }
                        catch (Exception ignored) {}
                    } else {
                        Toast.makeText(this, "Enable location services", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void fetchLocationAndNotify(String phone, String email) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;

        LocationServices.getFusedLocationProviderClient(this)
                .getLastLocation()
                .addOnSuccessListener(loc -> {
                    String msg;
                    if (loc != null) {
                        double lat = loc.getLatitude(), lon = loc.getLongitude();
                        msg = buildLocationMessage("Landed safely 🛬", lat, lon);
                        showLocationInCard(lat, lon);
                    } else {
                        msg = "Landed safely 🛬";
                        binding.locationText.setText("📍 unavailable");
                    }

                    if (binding.smsSwitch.isChecked() && !phone.isEmpty()) safeSendSMS(phone, msg);
                    if (binding.emailSwitch.isChecked() && !email.isEmpty()) sendEmail(email, "Landed", msg);
                });
    }

    // ---------- SMS / Email ----------
    private void safeSendSMS(String to, String text) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread(() -> ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, PERMISSIONS_REQUEST_CODE));
            return;
        }
        sendSMS(to, text);
    }

    private SmsManager smsForDefaultSub() {
        int subId = SubscriptionManager.getDefaultSmsSubscriptionId(); // dual-SIM aware [6]
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return SmsManager.getSmsManagerForSubscriptionId(subId);
        }
        return SmsManager.getDefault(); // fallback [3]
    }

    private void sendSMS(String to, String body) {
        try {
            SmsManager sms = smsForDefaultSub();

            PendingIntent sentPI = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_SMS_SENT), PendingIntent.FLAG_IMMUTABLE);
            PendingIntent deliveredPI = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_SMS_DELIVERED), PendingIntent.FLAG_IMMUTABLE);

            ArrayList<String> parts = sms.divideMessage(body); // handle long SMS [3][1]
            ArrayList<PendingIntent> sentList = new ArrayList<>();
            ArrayList<PendingIntent> deliveredList = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) { sentList.add(sentPI); deliveredList.add(deliveredPI); }

            sms.sendMultipartTextMessage(to, null, parts, sentList, deliveredList); // [3][1][4]
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "SMS failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void sendEmail(String to, String subject, String body) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("message/rfc822");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, body);
        try { startActivity(Intent.createChooser(i, "Send Email")); }
        catch (Exception e) { Toast.makeText(this, "No email client", Toast.LENGTH_SHORT).show(); }
    }

    private void testNotifications() {
        String num = binding.familyNumberEdit.getText().toString().trim();
        String em  = binding.emailEdit.getText().toString().trim();

        if (binding.locationSwitch.isChecked()) {
            checkLocationSettings(); // will call fetchLocationAndNotify on success
            Toast.makeText(this, "Testing with location…", Toast.LENGTH_SHORT).show();
            return;
        }

        if (binding.smsSwitch.isChecked() && !num.isEmpty()) safeSendSMS(num, "Test SMS 📱");
        if (binding.emailSwitch.isChecked() && !em.isEmpty()) sendEmail(em, "Test Email", "This is a test.");
        Toast.makeText(this, "Test sent", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (airplaneModeReceiver != null) unregisterReceiver(airplaneModeReceiver);
        try { unregisterReceiver(smsSentReceiver); } catch (Exception ignore) {}
        executor.shutdown();
    }
}
