package org.akvo.mapexperiments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.offline.OfflineRegion;
import com.mapbox.mapboxsdk.offline.OfflineRegionDefinition;
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MapBoxActivity extends LocationAwareActivity
        implements RegionsListDialogFragment.RegionsSelectionListener {

    public static final String PREFERENCE_NAME = "Prefs";
    public static final String PREF_SHAPE = "shape";
    public static final double ZOOM_LEVEL = 16;

    private final BitmapGenerator bitmapGenerator = new BitmapGenerator();
    private final List<LatLng> locations = new ArrayList<>();
    private final Gson gson = new Gson();

    private MapboxMap mapboxMap;
    private MapView mapView;
    private SharedPreferences sharedPreferences;
    private boolean manualAreaSelected;
    private Icon icon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapbox);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        sharedPreferences = getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE);

        IconFactory iconFactory = IconFactory.getInstance(MapBoxActivity.this);
        Bitmap bmp = bitmapGenerator.getBitmap();
        icon = iconFactory.fromBitmap(bmp);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                MapBoxActivity.this.mapboxMap = mapboxMap;
                checkLocation();
            }
        });
    }

    @Override
    protected void updateNewLocation(@Nullable Location location) {
        if (location == null) {
            return;
        }
        LatLngAcc latestLatLng = new LatLngAcc(location);

        LatLng lastLatLong = locations.size() == 0 ? null : locations.get(locations.size() - 1);
        //1 meters minimum distance for the point to be added
        if (lastLatLong == null
                || latestLatLng.distanceTo(lastLatLong) > MapOptions.MINIMUM_DISTANCE) {
            if (!manualAreaSelected) {
                double zoom = lastLatLong == null ? ZOOM_LEVEL : mapboxMap.getCameraPosition().zoom;
                if (zoom == 0) {
                    zoom = ZOOM_LEVEL;
                }
                mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(location.getLatitude(), location.getLongitude()), zoom));
            }
            locations.add(latestLatLng);
            redrawMap();
        }
    }

    @Override
    protected void enableMapLocation() {
        //Ignore
    }

    private void redrawMap() {
        mapboxMap.clear();

        //TODO: save geolines to json each time

        mapboxMap.addPolyline(new PolylineOptions()
                .addAll(locations)
                .color(MapOptions.LINE_COLOR)
                .width(MapOptions.LINE_WIDHT));
        for (LatLng latLng : locations) {
            mapboxMap.addMarker(new MarkerViewOptions()
                    .anchor(MapOptions.MARKER_ANCHOR, MapOptions.MARKER_ANCHOR)
                    .icon(icon)
                    .position(latLng)
                    .title(((LatLngAcc) latLng).getTitle()));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mapbox_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save:
                if (locations.size() > 0) {
                    sharedPreferences.edit()
                            .putString(PREF_SHAPE, gson.toJson(locations)).apply();
                    toast("Shape saved");
                }
                return true;
            case R.id.load:
                String savedLocationsString = sharedPreferences.getString(PREF_SHAPE, null);
                if (!TextUtils.isEmpty(savedLocationsString)) {
                    toast("Loading shape");
                    Type type = new TypeToken<List<LatLngAcc>>() {
                    }.getType();
                    List<LatLngAcc> locationsPref = gson.fromJson(savedLocationsString, type);
                    locations.clear();
                    locations.addAll(locationsPref);
                    //TODO: center map on last location item.
                    redrawMap();
                }
                return true;
            case R.id.load_offline:
                RegionsListDialogFragment fragment = RegionsListDialogFragment.newInstance();
                fragment.show(getSupportFragmentManager(), RegionsListDialogFragment.TAG);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAddNewSelected() {
        startActivity(new Intent(MapBoxActivity.this, OfflineManagerActivity.class));
    }

    @Override
    public void onRegionSelected(OfflineRegion region) {
        OfflineRegionDefinition definition = region.getDefinition();
        LatLngBounds bounds = definition
                .getBounds();
        double regionZoom = ((OfflineTilePyramidRegionDefinition)
                definition)
                .getMinZoom();

        // Create new camera position
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(bounds.getCenter())
                .zoom(regionZoom)
                .build();

        // Move camera to new position
        mapboxMap.moveCamera(CameraUpdateFactory
                .newCameraPosition(cameraPosition));
        manualAreaSelected = true;
    }

    public class LatLngAcc extends LatLng {

        private float accuracy = 0.0f;

        LatLngAcc(Location location) {
            super(location);
            this.accuracy = location.getAccuracy();
        }

        public float getAccuracy() {
            return accuracy;
        }

        public String getTitle() {
            return "Loc: " + getLatitude() + ", " + getLongitude()
                    + ", acc: " + accuracy;
        }
    }
}
