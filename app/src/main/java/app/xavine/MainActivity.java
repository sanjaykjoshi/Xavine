package app.xavine;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The phone side: stats, fill-up history, parking spot, reminders and setup. */
public class MainActivity extends Activity {

    private static final int ACCENT = 0xFF4FC3F7;
    private static final int CARD = 0xFF1A1E24;
    private static final int DIM = 0xB3FFFFFF;

    private LinearLayout content;
    private float dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(16), px(24), px(16), px(32));
        scroll.addView(content);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets in = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(in.left, in.top, in.right, in.bottom);
                return insets;
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        render();
    }

    private void render() {
        content.removeAllViews();

        TextView title = text("Xavine", 30, Color.WHITE, true);
        content.addView(title);
        content.addView(text("Tata Altroz XTA · petrol · 37 L tank", 14, DIM, false));
        space(16);

        renderSetup();
        renderStats();
        renderParking();
        renderReminders();
        renderHistory();
    }

    // ------------------------------------------------------------ setup

    private boolean needsPermissions() {
        boolean loc = Locations.hasPermission(this);
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return !(loc && notif);
    }

    private void renderSetup() {
        LinearLayout card = card("Set up");
        if (needsPermissions()) {
            card.addView(text("Xavine needs location (for fuel stations and your parking spot) "
                    + "and notifications (to tell you where you parked).", 15, DIM, false));
            card.addView(button("Allow location & notifications", v -> askPermissions()));
        } else {
            card.addView(text("✓ Location and notifications allowed", 15, 0xFF81C784, false));
        }
        card.addView(button("How to show Xavine in Android Auto", v -> showAutoHelp()));
    }

    private void askPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS);
        requestPermissions(perms.toArray(new String[0]), 5);
    }

    private void showAutoHelp() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Show Xavine in Android Auto")
                .setMessage("Android Auto only shows Play Store apps unless you allow others once:\n\n"
                        + "1. Open Android Auto settings on your phone (Settings → search \"Android Auto\").\n\n"
                        + "2. Scroll to the bottom and tap \"Version\" about 10 times, then allow developer settings.\n\n"
                        + "3. Tap the ⋮ menu (top right) → Developer settings → turn on \"Unknown sources\".\n\n"
                        + "4. Unplug and reconnect to the car. Xavine appears in the car's app list. "
                        + "If not, add it under Android Auto settings → Customise launcher.\n\n"
                        + "Typing (fill-ups) works when the car is stopped, e.g. at the pump.")
                .setPositiveButton("OK", null)
                .show();
    }

    // ------------------------------------------------------------ stats

    private void renderStats() {
        Store.Stats s = Store.stats(this);
        LinearLayout card = card("Mileage & spend");
        card.addView(statLine("Average mileage", s.avgKmpl > 0 ? Store.one(s.avgKmpl) + " km/l" : "after 2 full tanks"));
        if (s.lastKmpl > 0) card.addView(statLine("Last full tank", Store.one(s.lastKmpl) + " km/l"));
        card.addView(statLine("This month", Store.rupees(s.monthSpend)));
        if (s.costPerKm > 0) card.addView(statLine("Fuel cost", String.format(Locale.US, "₹%.2f per km", s.costPerKm)));
        if (s.lastPrice > 0) card.addView(statLine("Last price", String.format(Locale.US, "₹%.2f per litre", s.lastPrice)));
        if (s.latestOdo > 0) card.addView(statLine("Odometer", s.latestOdo + " km"));
        card.addView(button("Add fill-up", v -> addFillUpDialog()));
    }

    private void addFillUpDialog() {
        LinearLayout form = form();
        EditText amount = field(form, "Amount paid (₹)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText litres = field(form, "Litres", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText odo = field(form, "Odometer (km)", InputType.TYPE_CLASS_NUMBER);
        CheckBox full = new CheckBox(this);
        full.setText("Filled to full tank");
        full.setTextColor(Color.WHITE);
        full.setChecked(true);
        form.addView(full);

        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Add fill-up")
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double a = num(amount), l = num(litres);
            int o = (int) Math.round(num(odo));
            List<Store.FillUp> list = Store.fillUps(this);
            int last = list.isEmpty() ? 0 : list.get(list.size() - 1).odo;
            if (a <= 0 || l <= 0 || o <= 0) {
                toast("Fill in amount, litres and odometer");
            } else if (l > Store.TANK_LITRES + 3) {
                toast("The Altroz tank holds about 37 L");
            } else if (o < last) {
                toast("Odometer should be at least " + last + " km");
            } else {
                Store.addFillUp(this, o, a, l, full.isChecked());
                d.dismiss();
                render();
            }
        }));
        d.show();
    }

    // ------------------------------------------------------------ parking

    private void renderParking() {
        LinearLayout card = card("Parking spot");
        Store.Parking p = Store.parking(this);
        if (p == null) {
            card.addView(text("Saved automatically when you disconnect from Android Auto.", 15, DIM, false));
        } else {
            String when = new SimpleDateFormat("h:mm a, d MMM", Locale.getDefault()).format(new Date(p.ts));
            card.addView(text("Parked at " + when, 17, Color.WHITE, false));
            card.addView(button("Walk to my car", v -> {
                try {
                    startActivity(Locations.walkTo(p.lat, p.lon));
                } catch (Exception e) {
                    toast("Google Maps isn't available");
                }
            }));
        }
        card.addView(button("Save my location as parking spot", v -> {
            if (!Locations.hasPermission(this)) {
                askPermissions();
            } else if (Locations.saveParkingHere(this)) {
                toast("Parking spot saved");
                render();
            } else {
                toast("No location yet. Try again outdoors.");
            }
        }));
    }

    // ------------------------------------------------------------ reminders

    private void renderReminders() {
        LinearLayout card = card("Reminders");
        int odo = Store.stats(this).latestOdo;
        for (Store.Reminder r : Store.reminders(this)) {
            boolean due = Store.hasDue(r) && Store.isDueSoon(r, odo);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, px(8), 0, px(8));
            row.addView(text((due ? "⚠ " : "") + r.name, 17, due ? 0xFFFFB74D : Color.WHITE, false));
            row.addView(text(Store.describe(r, odo), 14, DIM, false));
            row.setOnClickListener(v -> reminderDialog(r));
            card.addView(row);
        }
        card.addView(button("Add reminder", v -> reminderDialog(null)));
    }

    private void reminderDialog(Store.Reminder existing) {
        LinearLayout form = form();
        EditText name = field(form, "Name, e.g. Periodic service", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText km = field(form, "Due at odometer (km), optional", InputType.TYPE_CLASS_NUMBER);
        final long[] date = {existing != null ? existing.dueDate : 0};
        Button pick = new Button(this);
        pick.setAllCaps(false);
        Runnable label = () -> pick.setText(date[0] > 0
                ? "Due date: " + new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(date[0]))
                : "Set due date (optional)");
        label.run();
        pick.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            if (date[0] > 0) c.setTimeInMillis(date[0]);
            new DatePickerDialog(this, (dp2, y, m, d) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(y, m, d, 9, 0, 0);
                sel.set(Calendar.MILLISECOND, 0);
                date[0] = sel.getTimeInMillis();
                label.run();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        form.addView(pick);
        Button clearDate = new Button(this);
        clearDate.setAllCaps(false);
        clearDate.setText("Clear date");
        clearDate.setOnClickListener(v -> { date[0] = 0; label.run(); });
        form.addView(clearDate);

        if (existing != null) {
            name.setText(existing.name);
            if (existing.dueKm > 0) km.setText(String.valueOf(existing.dueKm));
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(existing == null ? "New reminder" : "Edit reminder")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        toast("Give the reminder a name");
                        return;
                    }
                    List<Store.Reminder> list = Store.reminders(this);
                    Store.Reminder r = null;
                    if (existing != null) {
                        for (Store.Reminder x : list) if (x.id == existing.id) r = x;
                    }
                    if (r == null) {
                        r = new Store.Reminder();
                        r.id = System.currentTimeMillis();
                        list.add(r);
                    }
                    r.name = n;
                    r.dueKm = (int) Math.round(num(km));
                    r.dueDate = date[0];
                    Store.saveReminders(this, list);
                    render();
                })
                .setNegativeButton("Cancel", null);
        if (existing != null) {
            b.setNeutralButton("Delete", (d, w) -> {
                List<Store.Reminder> list = Store.reminders(this);
                for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id == existing.id) list.remove(i);
                Store.saveReminders(this, list);
                render();
            });
        }
        b.show();
    }

    // ------------------------------------------------------------ history

    private void renderHistory() {
        List<Store.FillUp> list = Store.fillUps(this);
        LinearLayout card = card("Fill-ups");
        if (list.isEmpty()) {
            card.addView(text("No fill-ups yet. Log them here or on the car screen.", 15, DIM, false));
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        int shown = 0;
        for (int i = list.size() - 1; i >= 0 && shown < 40; i--, shown++) {
            Store.FillUp f = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, px(8), 0, px(8));
            row.addView(text(Store.rupees(f.amount) + "  ·  " + Store.one(f.litres) + " L"
                    + (f.full ? "  ·  full" : "  ·  partial"), 16, Color.WHITE, false));
            row.addView(text(fmt.format(new Date(f.ts)) + "  ·  " + f.odo + " km  ·  "
                    + String.format(Locale.US, "₹%.2f/L", f.pricePerLitre()), 13, DIM, false));
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Delete this fill-up?")
                        .setPositiveButton("Delete", (d, w) -> {
                            Store.deleteFillUp(this, f.ts);
                            render();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
            card.addView(row);
        }
        card.addView(text("Long-press a fill-up to delete it.", 12, 0x80FFFFFF, false));
    }

    // ------------------------------------------------------------ view helpers

    private LinearLayout card(String heading) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(16), px(14), px(16), px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(px(16));
        card.setBackground(bg);
        card.addView(text(heading, 13, ACCENT, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(14);
        content.addView(card, lp);
        return card;
    }

    private View statLine(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, px(6), 0, px(6));
        TextView l = text(label, 15, DIM, false);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value, 17, Color.WHITE, true);
        v.setGravity(Gravity.END);
        row.addView(v);
        return row;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(8);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout form() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(px(20), px(8), px(20), 0);
        return f;
    }

    private EditText field(LinearLayout form, String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        form.addView(e);
        return e;
    }

    private void space(int h) {
        content.addView(new View(this), new LinearLayout.LayoutParams(1, px(h)));
    }

    private static double num(EditText e) {
        try {
            return Double.parseDouble(e.getText().toString().trim().replace(",", ""));
        } catch (Exception ex) {
            return 0;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int px(int v) {
        return Math.round(v * dp);
    }
}
