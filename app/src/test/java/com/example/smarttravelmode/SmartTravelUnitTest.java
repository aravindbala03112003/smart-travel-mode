package com.example.smarttravelmode;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

public class SmartTravelUnitTest {

    private Locale originalDefaultLocale;

    @Before
    public void setUp() {
        originalDefaultLocale = Locale.getDefault();
    }

    @After
    public void tearDown() {
        Locale.setDefault(originalDefaultLocale);
    }

    @Test
    public void testMapsUrlFormat_standardCoordinates() {
        double lat = 37.422065;
        double lon = -122.084090;
        String url = MainActivity.mapsUrl(lat, lon);
        assertEquals("https://maps.google.com/?q=37.422065,-122.084090", url);
    }

    @Test
    public void testMapsUrlFormat_commaLocaleDoesNotCorruptUrl() {
        // In German or French locale, decimal separator is comma (,) instead of period (.)
        Locale.setDefault(Locale.GERMANY);
        double lat = 48.856614;
        double lon = 2.352222;
        String url = MainActivity.mapsUrl(lat, lon);
        // Ensure decimal separator remains dot (.) so Maps URL is valid
        assertEquals("https://maps.google.com/?q=48.856614,2.352222", url);
        assertFalse("URL must not contain comma within numbers", url.contains("48,856614"));
    }

    @Test
    public void testMapsUrlFormat_zeroAndEquator() {
        String url = MainActivity.mapsUrl(0.0, 0.0);
        assertEquals("https://maps.google.com/?q=0.000000,0.000000", url);
    }

    @Test
    public void testMapsUrlFormat_boundaryCoordinates() {
        String northPole = MainActivity.mapsUrl(90.0, 0.0);
        assertEquals("https://maps.google.com/?q=90.000000,0.000000", northPole);

        String southPole = MainActivity.mapsUrl(-90.0, 0.0);
        assertEquals("https://maps.google.com/?q=-90.000000,0.000000", southPole);

        String antimeridian = MainActivity.mapsUrl(0.0, 180.0);
        assertEquals("https://maps.google.com/?q=0.000000,180.000000", antimeridian);
    }

    @Test
    public void testBuildLocationMessage_containsExpectedContent() {
        double lat = 12.971599;
        double lon = 77.594566;
        String prefix = "Landed safely 🛬";
        String message = MainActivity.buildLocationMessage(prefix, lat, lon);

        assertTrue("Message should contain prefix", message.startsWith("Landed safely 🛬"));
        assertTrue("Message should contain coordinates header", message.contains("Coordinates: 12.971599, 77.594566"));
        assertTrue("Message should contain Google Maps link", message.contains("https://maps.google.com/?q=12.971599,77.594566"));
    }

    @Test
    public void testAirplaneModeReceiver_defaultConstructorExists() {
        // Required for manifest instantiation
        AirplaneModeReceiver receiver = new AirplaneModeReceiver();
        assertNotNull(receiver);
    }

    @Test
    public void testAirplaneModeReceiver_listenerCallbackRegistration() {
        final boolean[] callbackInvoked = {false};
        final boolean[] recordedState = {false};

        AirplaneModeReceiver.AirplaneModeListener listener = isOn -> {
            callbackInvoked[0] = true;
            recordedState[0] = isOn;
        };

        AirplaneModeReceiver receiver = new AirplaneModeReceiver(listener);
        assertNotNull(receiver);

        AirplaneModeReceiver.setGlobalListener(listener);
        AirplaneModeReceiver.clearGlobalListener(listener);
    }

    @Test
    public void testSharedPreferencesConstants_keysAreValid() {
        assertEquals("smart_travel_prefs", MainActivity.PREFS_NAME);
        assertEquals("family_phone", MainActivity.KEY_PHONE);
        assertEquals("family_email", MainActivity.KEY_EMAIL);
        assertEquals("sms_enabled", MainActivity.KEY_SMS_ENABLED);
        assertEquals("email_enabled", MainActivity.KEY_EMAIL_ENABLED);
        assertEquals("location_enabled", MainActivity.KEY_LOCATION_ENABLED);
    }
}
