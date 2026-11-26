package app.xavine;

import android.content.Intent;
import android.location.Location;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.media.MediaBrowserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Car screen as a "media" app: Android Auto shows sideloaded media apps when
 * Unknown sources is on. Three tabs: Mileage & spend, Reminders, Fuel stations.
 */
public final class XavineMediaService extends MediaBrowserService {

    private static final String ROOT = "root";
    private static final String TAB_STATS = "stats";
    private static final String TAB_REMINDERS = "reminders";
    private static final String TAB_STATIONS = "stations";

    // Android Auto content-style hints: show lists rather than grids.
    private static final String STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED";
    private static final String STYLE_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT";
    private static final String STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT";

    private MediaSession session;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        session = new MediaSession(this, "Xavine");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                onItemChosen(mediaId);
            }
        });
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID)
                .setState(PlaybackState.STATE_NONE, 0, 1f)
                .build());
        setSessionToken(session.getSessionToken());
    }

    @Override
    public void onDestroy() {
        if (session != null) session.release();
        super.onDestroy();
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        Bundle extras = new Bundle();
        extras.putBoolean(STYLE_SUPPORTED, true);
        extras.putInt(STYLE_BROWSABLE, 1);
        extras.putInt(STYLE_PLAYABLE, 1);
        return new BrowserRoot(ROOT, extras);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {
        List<MediaItem> items = new ArrayList<>();
        switch (parentId) {
            case ROOT:
                items.add(folder(TAB_STATS, "Mileage"));
                items.add(folder(TAB_REMINDERS, "Reminders"));
                items.add(folder(TAB_STATIONS, "Fuel"));
                result.sendResult(items);
                return;
            case TAB_STATS: {
                Store.Stats s = Store.stats(this);
                items.add(info("s1", s.avgKmpl > 0 ? "Average " + Store.one(s.avgKmpl) + " km/l" : "Mileage: log 2 full tanks on the phone",
                        s.lastKmpl > 0 ? "Last full tank " + Store.one(s.lastKmpl) + " km/l" : null));
                items.add(info("s2", "This month " + Store.rupees(s.monthSpend),
                        s.costPerKm > 0 ? String.format(Locale.US, "₹%.2f per km", s.costPerKm) : null));
                if (s.lastPrice > 0) {
                    items.add(info("s3", String.format(Locale.US, "Last price ₹%.2f/L", s.lastPrice),
                            s.latestOdo > 0 ? "Odometer " + s.latestOdo + " km" : null));
                }
                result.sendResult(items);
                return;
            }
            case TAB_REMINDERS: {
                int odo = Store.stats(this).latestOdo;
                for (Store.Reminder r : Store.reminders(this)) {
                    if (!Store.hasDue(r)) continue;
                    boolean soon = Store.isDueSoon(r, odo);
                    items.add(info("r" + r.id, (soon ? "⚠ " : "") + r.name, "Due " + Store.describe(r, odo)));
                }
                if (items.isEmpty()) items.add(info("r0", "No reminders set", "Set due dates in Xavine on your phone"));
                result.sendResult(items);
                return;
            }
            case TAB_STATIONS:
                result.detach();
                new Thread(() -> {
                    List<MediaItem> out = new ArrayList<>();
                    Location here = Locations.best(this);
                    if (here == null) {
                        out.add(info("e1", "Location not available yet", "Allow location for Xavine on your phone"));
                    } else {
                        try {
                            for (Locations.Station st : Locations.nearbyStations(here.getLatitude(), here.getLongitude(), 10000, 12)) {
                                String dist = st.metres < 1000
                                        ? Math.round(st.metres) + " m"
                                        : String.format(Locale.US, "%.1f km", st.metres / 1000f);
                                out.add(info("st:" + st.lat + "," + st.lon + ":" + st.name, st.name,
                                        dist + (st.detail.isEmpty() ? "" : " · " + st.detail)));
                            }
                            if (out.isEmpty()) out.add(info("e2", "No fuel stations within 10 km", null));
                        } catch (Exception e) {
                            out.add(info("e3", "Couldn't load stations", "Check the phone's internet"));
                        }
                    }
                    main.post(() -> result.sendResult(out));
                }).start();
                return;
            default:
                result.sendResult(items);
        }
    }

    /** Tapping a station tries to start Google Maps navigation. */
    private void onItemChosen(String id) {
        if (id == null || !id.startsWith("st:")) return;
        try {
            String[] parts = id.substring(3).split(":", 2);
            Intent nav = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q=" + parts[0]));
            nav.setPackage("com.google.android.apps.maps");
            nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(nav);
        } catch (Exception ignored) {
            // Android may block starting navigation from here; then use Maps directly.
        }
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID)
                .setState(PlaybackState.STATE_STOPPED, 0, 1f)
                .build());
    }

    private static MediaItem folder(String id, String title) {
        return new MediaItem(new MediaDescription.Builder().setMediaId(id).setTitle(title).build(),
                MediaItem.FLAG_BROWSABLE);
    }

    private static MediaItem info(String id, String title, String subtitle) {
        MediaDescription.Builder d = new MediaDescription.Builder().setMediaId(id).setTitle(title);
        if (subtitle != null) d.setSubtitle(subtitle);
        return new MediaItem(d.build(), MediaItem.FLAG_PLAYABLE);
    }
}
