package app.xavine;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Location, nearby fuel stations (OpenStreetMap) and the parked-car notification. */
public final class Locations {

    private Locations() { }

    private static volatile Location latest;
    private static LocationListener tracker;

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Freshest fix we know about, or null. */
    @SuppressWarnings("MissingPermission")
    public static Location best(Context c) {
        Location best = latest;
        if (!hasPermission(c)) return best;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return best;
        List<String> providers = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) providers.add(LocationManager.FUSED_PROVIDER);
        providers.add(LocationManager.GPS_PROVIDER);
        providers.add(LocationManager.NETWORK_PROVIDER);
        for (String p : providers) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {
            }
        }
        return best;
    }

    /** Keeps a live fix while the car screen is connected (used for the parking spot). */
    @SuppressWarnings("MissingPermission")
    public static synchronized void startTracking(Context c) {
        if (tracker != null || !hasPermission(c)) return;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        // Full anonymous class (not a lambda): Android 8–10 call all four methods.
        tracker = new LocationListener() {
            @Override public void onLocationChanged(Location loc) { latest = loc; }
            @Override public void onStatusChanged(String p, int s, android.os.Bundle b) { }
            @Override public void onProviderEnabled(String p) { }
            @Override public void onProviderDisabled(String p) { }
        };
        String provider = (Build.VERSION.SDK_INT >= 31 && lm.getAllProviders().contains(LocationManager.FUSED_PROVIDER))
                ? LocationManager.FUSED_PROVIDER : LocationManager.GPS_PROVIDER;
        try {
            lm.requestLocationUpdates(provider, 15000L, 20f, tracker, Looper.getMainLooper());
        } catch (Exception e) {
            tracker = null;
        }
    }

    public static synchronized void stopTracking(Context c) {
        if (tracker == null) return;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        try {
            if (lm != null) lm.removeUpdates(tracker);
        } catch (Exception ignored) {
        }
        tracker = null;
    }

    /** Gets a fresh fix (up to ~10 s), falling back to the last known one. Callback may get null. */
    @SuppressWarnings("MissingPermission")
    public static void fetchFresh(Context c, java.util.function.Consumer<Location> done) {
        if (!hasPermission(c)) {
            done.accept(null);
            return;
        }
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || Build.VERSION.SDK_INT < 30) {
            done.accept(best(c));
            return;
        }
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);
        android.os.Handler h = new android.os.Handler(Looper.getMainLooper());
        Runnable timeout = () -> {
            if (finished.compareAndSet(false, true)) done.accept(best(c));
        };
        h.postDelayed(timeout, 10000);
        String provider = (Build.VERSION.SDK_INT >= 31 && lm.getAllProviders().contains(LocationManager.FUSED_PROVIDER))
                ? LocationManager.FUSED_PROVIDER : LocationManager.GPS_PROVIDER;
        try {
            lm.getCurrentLocation(provider, null, c.getMainExecutor(), loc -> {
                if (finished.compareAndSet(false, true)) {
                    h.removeCallbacks(timeout);
                    done.accept(loc != null ? loc : best(c));
                }
            });
        } catch (Exception e) {
            if (finished.compareAndSet(false, true)) {
                h.removeCallbacks(timeout);
                done.accept(best(c));
            }
        }
    }

    public static boolean hasBackgroundPermission(Context c) {
        return Build.VERSION.SDK_INT < 29
                || c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Saves where the car is now. Returns false if no location is available. */
    public static boolean saveParkingHere(Context c) {
        Location l = best(c);
        if (l == null) return false;
        Store.saveParking(c, l.getLatitude(), l.getLongitude());
        return true;
    }

    public static Intent walkTo(double lat, double lon) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + lat + "," + lon + "&mode=w"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    public static void notifyParked(Context c) {
        Store.Parking p = Store.parking(c);
        if (p == null) return;
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel("parking", "Parking spot", NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getActivity(c, 1, walkTo(p.lat, p.lon),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, "parking")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Parking spot saved")
                .setContentText("Tap to walk back to your Altroz")
                .setWhen(p.ts)
                .setShowWhen(true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(7, n);
    }

    // ------------------------------------------------------------ fuel stations

    public static final class Station {
        public String name;
        public String detail;
        public double lat, lon;
        public float metres;
    }

    /** Blocking network call: run off the main thread. */
    public static List<Station> nearbyStations(double lat, double lon, int radiusMetres, int max) throws Exception {
        String query = "[out:json][timeout:20];nwr[\"amenity\"=\"fuel\"](around:" + radiusMetres + ","
                + lat + "," + lon + ");out center 80;";
        String[] servers = {
                "https://overpass-api.de/api/interpreter",
                "https://overpass.kumi.systems/api/interpreter",
        };
        Exception last = null;
        for (String server : servers) {
            try {
                return parse(post(server, query), lat, lon, max);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("No server");
    }

    private static String post(String server, String query) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(server).openConnection();
        con.setConnectTimeout(12000);
        con.setReadTimeout(25000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("User-Agent", "Xavine/1.0 (personal Android Auto app)");
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        byte[] body = ("data=" + URLEncoder.encode(query, "UTF-8")).getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = con.getOutputStream()) {
            os.write(body);
        }
        if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static List<Station> parse(String json, double lat, double lon, int max) throws Exception {
        JSONArray els = new JSONObject(json).getJSONArray("elements");
        List<Station> out = new ArrayList<>();
        float[] d = new float[1];
        for (int i = 0; i < els.length(); i++) {
            JSONObject e = els.getJSONObject(i);
            double la, lo;
            if (e.has("lat")) {
                la = e.getDouble("lat");
                lo = e.getDouble("lon");
            } else if (e.has("center")) {
                la = e.getJSONObject("center").getDouble("lat");
                lo = e.getJSONObject("center").getDouble("lon");
            } else {
                continue;
            }
            JSONObject tags = e.optJSONObject("tags");
            String name = tags == null ? "" : tags.optString("name", "");
            String brand = tags == null ? "" : tags.optString("brand", tags.optString("operator", ""));
            Station s = new Station();
            s.name = !name.isEmpty() ? name : (!brand.isEmpty() ? brand : "Fuel station");
            List<String> bits = new ArrayList<>();
            if (!brand.isEmpty() && !brand.equals(s.name)) bits.add(brand);
            if (tags != null && "yes".equals(tags.optString("fuel:cng"))) bits.add("CNG");
            if (tags != null && "24/7".equals(tags.optString("opening_hours"))) bits.add("24 hours");
            s.detail = android.text.TextUtils.join(" · ", bits);
            s.lat = la;
            s.lon = lo;
            Location.distanceBetween(lat, lon, la, lo, d);
            s.metres = d[0];
            out.add(s);
        }
        Collections.sort(out, (a, b) -> Float.compare(a.metres, b.metres));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }
}
