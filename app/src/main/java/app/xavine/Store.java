package app.xavine;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** Everything Xavine saves, kept on the phone in SharedPreferences as JSON. */
public final class Store {

    public static final double TANK_LITRES = 37.0; // Tata Altroz petrol
    private static final long DAY = 24L * 60 * 60 * 1000;

    private Store() { }

    // ------------------------------------------------------------ models

    public static final class FillUp {
        public long ts;
        public int odo;
        public double amount;
        public double litres;
        public boolean full;

        public double pricePerLitre() {
            return litres > 0 ? amount / litres : 0;
        }
    }

    public static final class Reminder {
        public long id;
        public String name;
        public int dueKm;    // 0 = not set
        public long dueDate; // 0 = not set (millis)
    }

    public static final class Parking {
        public double lat, lon;
        public long ts;
    }

    public static final class Stats {
        public double avgKmpl;    // 0 if unknown
        public double lastKmpl;   // 0 if unknown
        public double monthSpend;
        public double costPerKm;  // 0 if unknown
        public int latestOdo;     // 0 if none
        public double lastPrice;  // 0 if none
        public int fillCount;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("xavine", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ fill-ups

    public static List<FillUp> fillUps(Context c) {
        List<FillUp> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("fillups", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                FillUp f = new FillUp();
                f.ts = o.getLong("ts");
                f.odo = o.getInt("odo");
                f.amount = o.getDouble("amount");
                f.litres = o.getDouble("litres");
                f.full = o.optBoolean("full", true);
                out.add(f);
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (x, y) -> x.odo != y.odo ? Integer.compare(x.odo, y.odo) : Long.compare(x.ts, y.ts));
        return out;
    }

    private static void saveFillUps(Context c, List<FillUp> list) {
        JSONArray a = new JSONArray();
        try {
            for (FillUp f : list) {
                JSONObject o = new JSONObject();
                o.put("ts", f.ts);
                o.put("odo", f.odo);
                o.put("amount", f.amount);
                o.put("litres", f.litres);
                o.put("full", f.full);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString("fillups", a.toString()).apply();
    }

    public static void addFillUp(Context c, int odo, double amount, double litres, boolean full) {
        List<FillUp> list = fillUps(c);
        FillUp f = new FillUp();
        f.ts = System.currentTimeMillis();
        f.odo = odo;
        f.amount = amount;
        f.litres = litres;
        f.full = full;
        list.add(f);
        saveFillUps(c, list);
    }

    public static void deleteFillUp(Context c, long ts) {
        List<FillUp> list = fillUps(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).ts == ts) list.remove(i);
        }
        saveFillUps(c, list);
    }

    /** Mileage uses the full-tank-to-full-tank method. */
    public static Stats stats(Context c) {
        List<FillUp> list = fillUps(c);
        Stats s = new Stats();
        s.fillCount = list.size();
        if (list.isEmpty()) return s;

        FillUp last = list.get(list.size() - 1);
        s.latestOdo = last.odo;
        s.lastPrice = last.pricePerLitre();

        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH), year = cal.get(Calendar.YEAR);
        for (FillUp f : list) {
            cal.setTimeInMillis(f.ts);
            if (cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year) s.monthSpend += f.amount;
        }

        double totalKm = 0, totalLitres = 0, totalCost = 0;
        int prevFull = -1;
        double segLitres = 0, segCost = 0;
        for (int i = 0; i < list.size(); i++) {
            FillUp f = list.get(i);
            if (prevFull >= 0) {
                segLitres += f.litres;
                segCost += f.amount;
            }
            if (f.full) {
                if (prevFull >= 0) {
                    int km = f.odo - list.get(prevFull).odo;
                    if (km > 0 && segLitres > 0) {
                        totalKm += km;
                        totalLitres += segLitres;
                        totalCost += segCost;
                        s.lastKmpl = km / segLitres;
                    }
                }
                prevFull = i;
                segLitres = 0;
                segCost = 0;
            }
        }
        if (totalLitres > 0) s.avgKmpl = totalKm / totalLitres;
        if (totalKm > 0) s.costPerKm = totalCost / totalKm;
        return s;
    }

    // ------------------------------------------------------------ reminders

    public static List<Reminder> reminders(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("seeded", false)) {
            List<Reminder> seed = new ArrayList<>();
            String[] names = {"Periodic service", "Car insurance renewal", "PUC certificate", "Tyre rotation"};
            for (int i = 0; i < names.length; i++) {
                Reminder r = new Reminder();
                r.id = System.currentTimeMillis() + i;
                r.name = names[i];
                seed.add(r);
            }
            saveReminders(c, seed);
            p.edit().putBoolean("seeded", true).apply();
        }
        List<Reminder> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("reminders", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Reminder r = new Reminder();
                r.id = o.getLong("id");
                r.name = o.getString("name");
                r.dueKm = o.optInt("dueKm", 0);
                r.dueDate = o.optLong("dueDate", 0);
                out.add(r);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void saveReminders(Context c, List<Reminder> list) {
        JSONArray a = new JSONArray();
        try {
            for (Reminder r : list) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("name", r.name);
                o.put("dueKm", r.dueKm);
                o.put("dueDate", r.dueDate);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString("reminders", a.toString()).apply();
    }

    public static boolean hasDue(Reminder r) {
        return r.dueKm > 0 || r.dueDate > 0;
    }

    /** Due within 500 km or 15 days, or overdue. */
    public static boolean isDueSoon(Reminder r, int odo) {
        if (r.dueKm > 0 && odo > 0 && r.dueKm - odo <= 500) return true;
        return r.dueDate > 0 && r.dueDate - System.currentTimeMillis() <= 15 * DAY;
    }

    /** e.g. "in 420 km · in 12 days", "overdue by 3 days". */
    public static String describe(Reminder r, int odo) {
        List<String> parts = new ArrayList<>();
        if (r.dueKm > 0) {
            if (odo <= 0) {
                parts.add("at " + r.dueKm + " km");
            } else {
                int left = r.dueKm - odo;
                parts.add(left >= 0 ? "in " + left + " km" : "overdue by " + (-left) + " km");
            }
        }
        if (r.dueDate > 0) {
            long days = Math.round((r.dueDate - startOfToday()) / (double) DAY);
            if (days > 1) parts.add("in " + days + " days");
            else if (days == 1) parts.add("tomorrow");
            else if (days == 0) parts.add("today");
            else parts.add("overdue by " + (-days) + (days == -1 ? " day" : " days"));
        }
        if (parts.isEmpty()) return "Tap to set when it's due";
        return android.text.TextUtils.join(" · ", parts);
    }

    private static long startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ------------------------------------------------------------ parking

    public static void saveParking(Context c, double lat, double lon) {
        prefs(c).edit()
                .putString("park_lat", String.valueOf(lat))
                .putString("park_lon", String.valueOf(lon))
                .putLong("park_ts", System.currentTimeMillis())
                .apply();
    }

    public static Parking parking(Context c) {
        SharedPreferences p = prefs(c);
        long ts = p.getLong("park_ts", 0);
        if (ts == 0) return null;
        try {
            Parking pk = new Parking();
            pk.lat = Double.parseDouble(p.getString("park_lat", "0"));
            pk.lon = Double.parseDouble(p.getString("park_lon", "0"));
            pk.ts = ts;
            return pk;
        } catch (Exception e) {
            return null;
        }
    }

    public static void clearParking(Context c) {
        prefs(c).edit().remove("park_lat").remove("park_lon").remove("park_ts").apply();
    }

    // ------------------------------------------------------------ car Bluetooth

    public static void setCar(Context c, String address, String name) {
        prefs(c).edit().putString("car_bt", address).putString("car_bt_name", name).apply();
    }

    public static String carAddress(Context c) {
        return prefs(c).getString("car_bt", null);
    }

    public static String carName(Context c) {
        return prefs(c).getString("car_bt_name", null);
    }

    /** True the first time it's called for this reminder today. */
    public static boolean firstNoticeToday(Context c, long reminderId) {
        Calendar cal = Calendar.getInstance();
        String today = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
        String key = "noticed_" + reminderId;
        if (today.equals(prefs(c).getString(key, ""))) return false;
        prefs(c).edit().putString(key, today).apply();
        return true;
    }

    // ------------------------------------------------------------ formatting

    public static String rupees(double v) {
        return "₹" + String.format(java.util.Locale.US, "%,.0f", v);
    }

    public static String one(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }
}
