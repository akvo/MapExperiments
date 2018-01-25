package org.akvo.mapexperiments;

import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends LocationAwareActivity implements OnMapReadyCallback {

    private static final float ZOOM_LEVEL = 15.0F;

    private GoogleMap map;

    private final List<LatLng> locations = new ArrayList<>();
    private BitmapDescriptor bitmapDescriptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmapGenerator.getBitmap());
        initLocationFragment();
    }

    private void initLocationFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        checkLocation();
    }

    @SuppressLint("MissingPermission") @Override
    protected void enableMapLocation() {
        map.setMyLocationEnabled(true);
    }

    //TODO: unify code with MapBoxActivity
    @Override
    protected void updateNewLocation(@Nullable Location location) {
        if (location != null && map != null) {
            LatLng lastLatLong = locations.size() == 0 ? null : locations.get(locations.size() - 1);
            Location lastSavedLocation = null;
            if (lastLatLong != null) {
                lastSavedLocation = new Location("gps");
                lastSavedLocation.setLatitude(lastLatLong.latitude);
                lastSavedLocation.setLongitude(lastLatLong.longitude);
            }
            //1 meters minimum distance for the point to be added
            float distanceTo =
                    lastSavedLocation == null ? 0 : location.distanceTo(lastSavedLocation);
            if (lastSavedLocation == null || distanceTo > MapOptions.MINIMUM_DISTANCE) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                locations.add(latLng);
                float zoom = lastSavedLocation == null ? ZOOM_LEVEL : map.getCameraPosition().zoom;
                if (zoom == 0) {
                    zoom = ZOOM_LEVEL;
                }
                map.clear();
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));

                PolylineOptions polylineOptions =
                        new PolylineOptions().addAll(locations)
                                .width(MapOptions.LINE_WIDHT).color(MapOptions.LINE_COLOR);
                map.addPolyline(polylineOptions);

                for (LatLng l : locations) {
                    MarkerOptions markerOptions = new MarkerOptions().icon(bitmapDescriptor)
                            .position(l)
                            .anchor(MapOptions.MARKER_ANCHOR, MapOptions.MARKER_ANCHOR)
                            .title(l.toString());
                    map.addMarker(markerOptions);
                }
            }
        }
    }

}
