package com.example.smarttravelmode;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.util.Linkify;
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
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements AirplaneModeReceiver.AirplaneModeListener {

    public static final String PREFS_NAME = "smart_travel_prefs";
    public static final String KEY_PHONE = "family_phone";
    public static final String KEY_EMAIL = "family_email";
    public static final String KEY_SMS_ENABLED = "sms_enabled";
    public static final String KEY_EMAIL_ENABLED = "email_enabled";
    public static final String KEY_LOCATION_ENABLED = "location_enabled";

    private static final int PERMISSIONS_REQUEST_CODE = 1001;
    private static final int REQUEST_CHECK_SETTINGS = 1002;

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private ActivityMainBinding binding;
    private ExecutorService executor;
    private AirplaneModeReceiver airplaneModeReceiver;
    private boolean isAirplaneReceiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        executor = Executors.newFixedThreadPool(3);

        setupUi();
        loadSettings();
        checkInitialAirplaneMode();
        requestAllPermissions();
    }

    private void checkInitialAirplaneMode() {
        boolean isOn = AirplaneModeReceiver.isAirplaneModeOn(this);
        if (isOn) {
            binding.statusText.setText("✈️ Airplane Mode ON");
            binding.statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.orange_500));
        } else {
            binding.statusText.setText("Ready to monitor airplane mode");
            binding.statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.blue_500));
        }
    }

    private void setupUi() {
        binding.testButton.setOnClickListener(v -> testNotifications());
        binding.settingsFab.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        });

        binding.locationSwitch.setOnCheckedChangeListener((btn, on) -> {
            if (on) {
                checkLocationSettingsPromptOnly();
            } else {
                binding.locationText.setAutoLinkMask(0);
                binding.locationText.setText("Location disabled");
            }
            saveSettings();
        });

        binding.smsSwitch.setOnCheckedChangeListener((btn, on) -> saveSettings());
        binding.emailSwitch.setOnCheckedChangeListener((btn, on) -> saveSettings());
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_PHONE, binding.familyNumberEdit.getText().toString().trim())
                .putString(KEY_EMAIL, binding.emailEdit.getText().toString().trim())
                .putBoolean(KEY_SMS_ENABLED, binding.smsSwitch.isChecked())
                .putBoolean(KEY_EMAIL_ENABLED, binding.emailSwitch.isChecked())
                .putBoolean(KEY_LOCATION_ENABLED, binding.locationSwitch.isChecked())
                .apply();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String phone = prefs.getString(KEY_PHONE, "");
        String email = prefs.getString(KEY_EMAIL, "");
        boolean sms = prefs.getBoolean(KEY_SMS_ENABLED, true);
        boolean em = prefs.getBoolean(KEY_EMAIL_ENABLED, true);
        boolean loc = prefs.getBoolean(KEY_LOCATION_ENABLED, true);

        binding.familyNumberEdit.setText(phone);
        binding.emailEdit.setText(email);
        binding.smsSwitch.setChecked(sms);
        binding.emailSwitch.setChecked(em);
        binding.locationSwitch.setChecked(loc);

        if (!loc) {
            binding.locationText.setAutoLinkMask(0);
            binding.locationText.setText("Location disabled");
        }
    }

    private void requestAllPermissions() {
        boolean all = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                all = false;
                break;
            }
        }
        if (!all) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
        } else {
            registerAirplaneReceiver();
        }
    }

    private void registerAirplaneReceiver() {
        if (!isAirplaneReceiverRegistered) {
            airplaneModeReceiver = new AirplaneModeReceiver(this);
            registerReceiver(airplaneModeReceiver, new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));
            isAirplaneReceiverRegistered = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == PERMISSIONS_REQUEST_CODE) {
            registerAirplaneReceiver();
            boolean fineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean coarseLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (fineLoc || coarseLoc) {
                Toast.makeText(this, "Location permission granted ✅", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission recommended for flight notifications", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Called by AirplaneModeReceiver
    @Override
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
    public static String mapsUrl(double lat, double lon) {
        return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon);
    }

    public static String buildLocationMessage(String prefix, double lat, double lon) {
        return prefix + "\n\n" +
                String.format(Locale.US, "Coordinates: %.6f, %.6f\n", lat, lon) +
                "Open in Google Maps: " + mapsUrl(lat, lon);
    }

    private void showLocationInCard(double lat, double lon) {
        String url = mapsUrl(lat, lon);
        binding.locationText.setText(String.format(Locale.US, "📍 %.6f, %.6f\n%s", lat, lon, url));
        Linkify.addLinks(binding.locationText, Linkify.WEB_URLS);
    }

    // ---------- Boarding / Landing ----------
    private void sendBoarding() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String to = binding.familyNumberEdit.getText().toString().trim();
        if (to.isEmpty()) to = prefs.getString(KEY_PHONE, "");

        String em = binding.emailEdit.getText().toString().trim();
        if (em.isEmpty()) em = prefs.getString(KEY_EMAIL, "");

        final String finalTo = to;
        final String finalEm = em;

        executor.execute(() -> {
            fetchLocationAndDispatch(
                    "Boarding flight ✈️ – will be unreachable until landing.",
                    "Boarding Flight",
                    finalTo,
                    finalEm,
                    false
            );
        });
    }

    private void sendLanding() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String to = binding.familyNumberEdit.getText().toString().trim();
        if (to.isEmpty()) to = prefs.getString(KEY_PHONE, "");

        String em = binding.emailEdit.getText().toString().trim();
        if (em.isEmpty()) em = prefs.getString(KEY_EMAIL, "");

        final String finalTo = to;
        final String finalEm = em;

        executor.execute(() -> {
            // Give modem time to reattach to network after airplane mode turns off
            try { Thread.sleep(7000); } catch (InterruptedException ignored) {}
            fetchLocationAndDispatch(
                    "Landed safely 🛬",
                    "Landed Safely",
                    finalTo,
                    finalEm,
                    false
            );
        });
    }

    // ---------- Location fetch then notify ----------
    private void checkLocationSettingsPromptOnly() {
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000)
                .setMinUpdateIntervalMillis(5_000)
                .build();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(req);
        SettingsClient client = LocationServices.getSettingsClient(this);

        client.checkLocationSettings(builder.build())
                .addOnFailureListener(e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            ((ResolvableApiException) e).startResolutionForResult(this, REQUEST_CHECK_SETTINGS);
                        } catch (Exception ignored) {}
                    } else {
                        Toast.makeText(this, "Enable location services", Toast.LENGTH_LONG).show();
                    }
                });
    }

    public void checkLocationSettings() {
        checkLocationSettingsPromptOnly();
    }

    private void fetchLocationAndDispatch(String prefix, String subject, String phone, String email, boolean isTest) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            dispatchNotifications(phone, email, subject, prefix);
            return;
        }

        // Automatically fetch location without any manual human interaction
        LocationServices.getFusedLocationProviderClient(this)
                .getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        double lat = loc.getLatitude(), lon = loc.getLongitude();
                        String msg = buildLocationMessage(prefix, lat, lon);
                        runOnUiThread(() -> showLocationInCard(lat, lon));
                        dispatchNotifications(phone, email, subject, msg);
                    } else {
                        // Request fresh location if cached location is null
                        requestFreshLocationAndDispatch(prefix, subject, phone, email);
                    }
                })
                .addOnFailureListener(e -> {
                    requestFreshLocationAndDispatch(prefix, subject, phone, email);
                });
    }

    private void requestFreshLocationAndDispatch(String prefix, String subject, String phone, String email) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            dispatchNotifications(phone, email, subject, prefix);
            return;
        }

        CancellationTokenSource cts = new CancellationTokenSource();
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(loc -> {
                    String msg;
                    if (loc != null) {
                        double lat = loc.getLatitude(), lon = loc.getLongitude();
                        msg = buildLocationMessage(prefix, lat, lon);
                        runOnUiThread(() -> showLocationInCard(lat, lon));
                    } else {
                        msg = prefix;
                        runOnUiThread(() -> binding.locationText.setText("📍 unavailable"));
                    }
                    dispatchNotifications(phone, email, subject, msg);
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> binding.locationText.setText("📍 unavailable"));
                    dispatchNotifications(phone, email, subject, prefix);
                });
    }

    private void dispatchNotifications(String phone, String email, String subject, String message) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean smsEnabled = binding.smsSwitch.isChecked() || prefs.getBoolean(KEY_SMS_ENABLED, true);
        boolean emailEnabled = binding.emailSwitch.isChecked() || prefs.getBoolean(KEY_EMAIL_ENABLED, true);

        if (smsEnabled && !phone.isEmpty()) {
            safeSendSMS(phone, message);
        }
        if (emailEnabled && !email.isEmpty()) {
            sendEmail(email, subject, message);
        }
    }

    // ---------- SMS / Email ----------
    private void safeSendSMS(String to, String text) {
        sendSMS(to, text);
    }

    private void sendSMS(String to, String body) {
        runOnUiThread(() -> {
            try {
                Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
                smsIntent.setData(Uri.parse("smsto:" + Uri.encode(to)));
                smsIntent.putExtra("sms_body", body);
                smsIntent.putExtra(Intent.EXTRA_TEXT, body);
                smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(smsIntent);
            } catch (Exception e) {
                // Fallback to generic SMS intent
                try {
                    Intent fallback = new Intent(Intent.ACTION_VIEW);
                    fallback.setData(Uri.parse("sms:" + Uri.encode(to)));
                    fallback.putExtra("sms_body", body);
                    fallback.putExtra(Intent.EXTRA_TEXT, body);
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(fallback);
                } catch (Exception ex) {
                    Toast.makeText(this, "No SMS app found", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void sendEmail(String to, String subject, String body) {
        runOnUiThread(() -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:" + Uri.encode(to)));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT, body);
            emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                Intent chooser = Intent.createChooser(emailIntent, "Send Flight Alert Email");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            } catch (Exception e) {
                // Fallback to generic ACTION_SEND if ACTION_SENDTO has no target
                Intent genericIntent = new Intent(Intent.ACTION_SEND);
                genericIntent.setType("message/rfc822");
                genericIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
                genericIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                genericIntent.putExtra(Intent.EXTRA_TEXT, body);
                genericIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    Intent chooser = Intent.createChooser(genericIntent, "Send Flight Alert Email");
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(chooser);
                } catch (Exception ex) {
                    Toast.makeText(this, "No email client found", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void testNotifications() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String num = binding.familyNumberEdit.getText().toString().trim();
        if (num.isEmpty()) num = prefs.getString(KEY_PHONE, "");

        String em = binding.emailEdit.getText().toString().trim();
        if (em.isEmpty()) em = prefs.getString(KEY_EMAIL, "");

        boolean smsOn = binding.smsSwitch.isChecked() || prefs.getBoolean(KEY_SMS_ENABLED, true);
        boolean emailOn = binding.emailSwitch.isChecked() || prefs.getBoolean(KEY_EMAIL_ENABLED, true);

        if (!smsOn && !emailOn) {
            Toast.makeText(this, "Enable SMS or Email notification first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (smsOn && num.isEmpty()) {
            binding.familyNumberEdit.setError("Phone number required for SMS");
            Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (emailOn && em.isEmpty()) {
            binding.emailEdit.setError("Email required for Email notification");
            Toast.makeText(this, "Please enter an email address", Toast.LENGTH_SHORT).show();
            return;
        }

        saveSettings();
        Toast.makeText(this, "Testing automatic notifications with location…", Toast.LENGTH_SHORT).show();

        fetchLocationAndDispatch(
                "Test Flight Notification 🧪",
                "Test Flight Notification",
                num,
                em,
                true
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (airplaneModeReceiver != null && isAirplaneReceiverRegistered) {
            try {
                unregisterReceiver(airplaneModeReceiver);
            } catch (Exception ignored) {}
            isAirplaneReceiverRegistered = false;
        }
        AirplaneModeReceiver.clearGlobalListener(this);
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
