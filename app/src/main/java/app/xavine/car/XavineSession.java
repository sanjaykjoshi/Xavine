package app.xavine.car;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import app.xavine.Locations;

/** One connection to the car. When it ends, we save where the car was parked. */
public final class XavineSession extends Session implements DefaultLifecycleObserver {

    public XavineSession() {
        getLifecycle().addObserver(this);
    }

    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull Intent intent) {
        return new HomeScreen(getCarContext());
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        Locations.startTracking(getCarContext().getApplicationContext());
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        Context app = getCarContext().getApplicationContext();
        if (Locations.saveParkingHere(app)) Locations.notifyParked(app);
        Locations.stopTracking(app);
    }
}
