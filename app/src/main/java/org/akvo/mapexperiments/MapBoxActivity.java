package org.akvo.mapexperiments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
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
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.location.LocationEnginePriority;
import com.mapbox.services.android.telemetry.location.LostLocationEngine;
import com.mapbox.services.android.telemetry.permissions.PermissionsListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MapBoxActivity extends AppCompatActivity implements LocationEngineListener,
        PermissionsListener, RegionsListDialogFragment.RegionsSelectionListener {

    public static final String PREFERENCE_NAME = "Prefs";
    public static final String PREF_SHAPE = "shape";
    public static final int ZOOM_LEVEL = 16;

    private final BitmapGenerator bitmapGenerator = new BitmapGenerator();
    private final List<LatLng> locations = new ArrayList<>();
    private final Gson gson = new Gson();

    private PermissionsManager permissionsManager;
    private LocationLayerPlugin locationPlugin;
    private LocationEngine locationEngine;
    private MapboxMap mapboxMap;
    private MapView mapView;
    private SharedPreferences sharedPreferences;
    private boolean manualAreaSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapbox);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        sharedPreferences = getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                MapBoxActivity.this.mapboxMap = mapboxMap;
                MapBoxActivity.this.mapboxMap.addOnCameraMoveListener(
                        new MapboxMap.OnCameraMoveListener() {
                            @Override
                            public void onCameraMove() {
                                redrawMap();
                            }
                        });
                enableLocationPlugin();
            }
        });
    }

    @SuppressWarnings({ "MissingPermission" })
    private void enableLocationPlugin() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            // Create an instance of LOST location engine
            initializeLocationEngine();
            locationPlugin = new LocationLayerPlugin(mapView, mapboxMap, locationEngine);
            locationPlugin.setLocationLayerEnabled(LocationLayerMode.TRACKING);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @SuppressWarnings({ "MissingPermission" })
    private void initializeLocationEngine() {
        locationEngine = new LostLocationEngine(MapBoxActivity.this);
        locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
        locationEngine.activate();

        Location lastLocation = locationEngine.getLastLocation();

        if (lastLocation != null) {
            updateLocations(lastLocation);
            if (!manualAreaSelected){
                // if manual area selected we do not want to navigate out of it
                setCameraPosition(lastLocation);
            }
        }
        locationEngine.addLocationEngineListener(this);
    }

    private void setCameraPosition(Location location) {
        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(location.getLatitude(), location.getLongitude()), ZOOM_LEVEL));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        //TODO:
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocationPlugin();
        } else {
            toast("Location permission not granted");
            finish();
        }
    }

    @Override
    @SuppressWarnings({ "MissingPermission" })
    public void onConnected() {
        locationEngine.requestLocationUpdates();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            setCameraPosition(location);
            updateLocations(location);
        }
    }

    private void updateLocations(Location location) {
        LatLngAcc latestLatLng = new LatLngAcc(location);
        LatLng lastLatLong = locations.size() == 0 ? null : locations.get(locations.size() - 1);
        //1 meters minimum distance for the point to be added
        if (lastLatLong == null || latestLatLng.distanceTo(lastLatLong) > 1) {
            locations.add(latestLatLng);
            redrawMap();
        }
    }

    private void redrawMap() {
        mapboxMap.clear();

        //TODO: save geolines to json each time

        IconFactory iconFactory = IconFactory.getInstance(MapBoxActivity.this);
        Bitmap bmp = bitmapGenerator.getBitmap();
        Icon icon = iconFactory.fromBitmap(bmp);
        mapboxMap.addPolyline(new PolylineOptions()
                .addAll(locations)
                .color(MapOptions.LINE_COLOR)
                .width(MapOptions.LINE_WIDHT));
        for (LatLng latLng : locations) {
            mapboxMap.addMarker(new MarkerViewOptions()
                    .anchor(0.5f, 0.5f)
                    .icon(icon)
                    .position(latLng)
                    .title(((LatLngAcc) latLng).getTitle()));
        }
    }

    @Override
    @SuppressWarnings({ "MissingPermission" })
    protected void onStart() {
        super.onStart();
        if (locationPlugin != null) {
            locationPlugin.onStart();
        }
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (mapboxMap != null) {
            enableLocationPlugin();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        stopUpdatingLocation();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopUpdatingLocation();
        if (locationPlugin != null) {
            locationPlugin.onStop();
        }
        mapView.onStop();
    }

    private void stopUpdatingLocation() {
        if (locationEngine != null) {
            locationEngine.removeLocationEngineListener(this);
        }
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
        if (locationEngine != null) {
            locationEngine.deactivate();
        }
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
                    Type type = new TypeToken<List<LatLngAcc>>(){}.getType();
                    List<LatLngAcc> locationsPref = gson.fromJson(savedLocationsString, type);
                    locations.clear();
                    locations.addAll(locationsPref);
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
