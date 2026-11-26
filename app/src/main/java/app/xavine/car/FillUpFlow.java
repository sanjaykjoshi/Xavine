package app.xavine.car;

import android.content.Context;

import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.ScreenManager;

import java.util.List;

import app.xavine.Store;

/** Amount → litres → odometer, then saved. Three short steps at the pump. */
final class FillUpFlow {

    private FillUpFlow() { }

    static void start(ScreenManager sm, CarContext car, boolean full) {
        final double[] amount = new double[1];
        final double[] litres = new double[1];
        Context app = car.getApplicationContext();

        sm.push(new NumberScreen(car,
                full ? "Full tank: amount paid" : "Partial fill: amount paid",
                "Rupees shown on the pump", "Amount in ₹", false, a -> {
            if (a < 50 || a > 20000) return "That doesn't look right. Enter the rupees paid.";
            amount[0] = a;
            sm.push(new NumberScreen(car, "Litres filled", "Litres shown on the pump, e.g. 25.43",
                    "Litres", true, l -> {
                if (l <= 0.5 || l > Store.TANK_LITRES + 3) {
                    return "Enter litres between 0.5 and " + (int) Store.TANK_LITRES;
                }
                litres[0] = l;
                int lastOdo = lastOdo(app);
                String hint = lastOdo > 0 ? "Last reading " + lastOdo + " km" : "Odometer in km";
                sm.push(new NumberScreen(car, "Odometer reading", "Total km on the dashboard",
                        hint, false, o -> {
                    int odo = (int) Math.round(o);
                    if (odo <= 0) return "Enter the odometer in km";
                    if (lastOdo > 0 && odo < lastOdo) return "Should be more than " + lastOdo + " km";
                    Store.addFillUp(app, odo, amount[0], litres[0], full);
                    Store.Stats s = Store.stats(app);
                    String msg = "Saved · ₹" + String.format(java.util.Locale.US, "%.2f", amount[0] / litres[0]) + "/L";
                    if (full && s.lastKmpl > 0) msg += " · this tank " + Store.one(s.lastKmpl) + " km/l";
                    CarToast.makeText(car, msg, CarToast.LENGTH_LONG).show();
                    sm.popToRoot();
                    return null;
                }));
                return null;
            }));
            return null;
        }));
    }

    private static int lastOdo(Context c) {
        List<Store.FillUp> list = Store.fillUps(c);
        return list.isEmpty() ? 0 : list.get(list.size() - 1).odo;
    }
}
