package app.xavine.car;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarLocation;
import androidx.car.app.model.Distance;
import androidx.car.app.model.DistanceSpan;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Metadata;
import androidx.car.app.model.Place;
import androidx.car.app.model.PlaceListMapTemplate;
import androidx.car.app.model.PlaceMarker;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import java.util.List;

import app.xavine.Locations;

/** Nearest petrol pumps on a map; tap one to navigate there. */
final class StationsScreen extends Screen {

    private final Handler main = new Handler(Looper.getMainLooper());
    private List<Locations.Station> stations;
    private String error;
    private boolean started;

    StationsScreen(@NonNull CarContext c) {
        super(c);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        Context app = getCarContext().getApplicationContext();
        if (!Locations.hasPermission(app)) {
            return message("Open Xavine on your phone and allow location, then try again.");
        }
        if (error != null) return message(error);
        if (!started) {
            started = true;
            load(app);
        }

        PlaceListMapTemplate.Builder b = new PlaceListMapTemplate.Builder()
                .setTitle("Fuel stations")
                .setHeaderAction(Action.BACK)
                .setCurrentLocationEnabled(true);
        if (stations == null) return b.setLoading(true).build();

        ItemList.Builder list = new ItemList.Builder().setNoItemsMessage("No fuel stations found within 10 km");
        for (Locations.Station s : stations) {
            SpannableString text = new SpannableString(" " + (s.detail.isEmpty() ? "" : " · " + s.detail));
            Distance d = s.metres < 1000
                    ? Distance.create(Math.round(s.metres), Distance.UNIT_METERS)
                    : Distance.create(s.metres / 1000.0, Distance.UNIT_KILOMETERS);
            text.setSpan(DistanceSpan.create(d), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            Place place = new Place.Builder(CarLocation.create(s.lat, s.lon))
                    .setMarker(new PlaceMarker.Builder().build())
                    .build();
            list.addItem(new Row.Builder()
                    .setTitle(s.name)
                    .addText(text)
                    .setMetadata(new Metadata.Builder().setPlace(place).build())
                    .setOnClickListener(() -> navigate(s))
                    .build());
        }
        return b.setItemList(list.build()).build();
    }

    private void load(Context app) {
        new Thread(() -> {
            List<Locations.Station> found = null;
            String problem = null;
            Location here = Locations.best(app);
            if (here == null) {
                problem = "Still finding your location. Try again in a moment.";
            } else {
                try {
                    found = Locations.nearbyStations(here.getLatitude(), here.getLongitude(), 10000, 6);
                } catch (Exception e) {
                    problem = "Couldn't load stations. Check your phone's internet and try again.";
                }
            }
            final List<Locations.Station> result = found;
            final String err = problem;
            main.post(() -> {
                stations = result;
                error = err;
                invalidate();
            });
        }).start();
    }

    private void navigate(Locations.Station s) {
        try {
            getCarContext().startCarApp(new Intent(CarContext.ACTION_NAVIGATE,
                    Uri.parse("geo:" + s.lat + "," + s.lon)));
        } catch (Exception e) {
            CarToast.makeText(getCarContext(), "Couldn't start navigation", CarToast.LENGTH_LONG).show();
        }
    }

    private Template message(String text) {
        return new MessageTemplate.Builder(text)
                .setTitle("Fuel stations")
                .setHeaderAction(Action.BACK)
                .addAction(new Action.Builder()
                        .setTitle("Try again")
                        .setOnClickListener(() -> {
                            error = null;
                            stations = null;
                            started = false;
                            invalidate();
                        })
                        .build())
                .build();
    }
}
