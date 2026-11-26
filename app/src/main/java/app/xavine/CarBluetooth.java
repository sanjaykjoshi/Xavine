package app.xavine;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Watches the Altroz's Bluetooth. Connect → show due reminders.
 * Disconnect → save the parking spot.
 */
public final class CarBluetooth extends BroadcastReceiver {

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context c, Intent intent) {
        BluetoothDevice d = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class)
                : intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String car = Store.carAddress(c);
        if (d == null || car == null || !car.equalsIgnoreCase(d.getAddress())) return;

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
            Reminders.notifyDue(c);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
            PendingResult pending = goAsync();
            Context app = c.getApplicationContext();
            Locations.fetchFresh(app, loc -> {
                try {
                    if (loc != null) {
                        Store.saveParking(app, loc.getLatitude(), loc.getLongitude());
                        Locations.notifyParked(app);
                    }
                } finally {
                    pending.finish();
                }
            });
        }
    }
}
