package app.xavine.car;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.OnClickListener;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import java.util.Locale;

import app.xavine.Locations;
import app.xavine.Store;

/** The first screen on the car display. */
public final class HomeScreen extends Screen {

    public HomeScreen(@NonNull CarContext carContext) {
        super(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        Context app = getCarContext().getApplicationContext();
        Store.Stats s = Store.stats(app);
        ItemList.Builder list = new ItemList.Builder();

        String title = s.avgKmpl > 0
                ? "Mileage " + Store.one(s.avgKmpl) + " km/l"
                : "Mileage appears after 2 full-tank fill-ups";
        String sub = "This month " + Store.rupees(s.monthSpend)
                + (s.costPerKm > 0 ? String.format(Locale.US, " · ₹%.2f per km", s.costPerKm) : "");
        list.addItem(new Row.Builder().setTitle(title).addText(sub).build());

        int shown = 0;
        for (Store.Reminder r : Store.reminders(app)) {
            if (shown >= 2) break;
            if (Store.hasDue(r) && Store.isDueSoon(r, s.latestOdo)) {
                list.addItem(new Row.Builder()
                        .setTitle("⚠ " + r.name)
                        .addText(Store.describe(r, s.latestOdo))
                        .build());
                shown++;
            }
        }

        list.addItem(row("Log full-tank fill-up", "Filled to the top · used for mileage",
                () -> FillUpFlow.start(getScreenManager(), getCarContext(), true)));
        list.addItem(row("Log partial fill-up", "Topped up only part of the tank",
                () -> FillUpFlow.start(getScreenManager(), getCarContext(), false)));
        list.addItem(row("Fuel stations near me", "Petrol pumps within 10 km",
                () -> getScreenManager().push(new StationsScreen(getCarContext()))));

        ActionStrip strip = new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("Save parking")
                        .setOnClickListener(() -> {
                            boolean ok = Locations.saveParkingHere(app);
                            CarToast.makeText(getCarContext(),
                                    ok ? "Parking spot saved" : "No location yet. Open Xavine on your phone and allow location.",
                                    CarToast.LENGTH_LONG).show();
                        })
                        .build())
                .build();

        return new ListTemplate.Builder()
                .setTitle("Xavine")
                .setHeaderAction(Action.APP_ICON)
                .setSingleList(list.build())
                .setActionStrip(strip)
                .build();
    }

    private static Row row(String title, String text, OnClickListener click) {
        return new Row.Builder().setTitle(title).addText(text).setOnClickListener(click).build();
    }
}
