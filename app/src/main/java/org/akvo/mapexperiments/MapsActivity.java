package org.akvo.mapexperiments;

import android.annotation.SuppressLint;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    private static final int REQUEST_CHECK_SETTINGS = 3;

    private GoogleMap mMap;
    private boolean activityJustCreated;

    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private LocationSettingsRequest mLocationSettingsRequest;
    private final List<LatLng> locations = new ArrayList<>();

    private final BitmapGenerator bitmapGenerator = new BitmapGenerator();
    private BitmapDescriptor bitmapDescriptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        activityJustCreated = true;
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmapGenerator.getBitmap());

        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();
        initLocationFragment();
    }

    /**
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Creates a callback for receiving location events.
     */
    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                Location currentLocation = locationResult.getLastLocation();
                updateNewLocation(currentLocation);
            }
        };
    }

    private void updateNewLocation(Location currentLocation) {
        if (currentLocation != null && mMap != null) {
            LatLng lastLatLong = locations.size() == 0 ? null : locations.get(locations.size() - 1);
            Location lastSavedLocation = null;
            if (lastLatLong != null) {
                lastSavedLocation = new Location("gps");
                lastSavedLocation.setLatitude(lastLatLong.latitude);
                lastSavedLocation.setLongitude(lastLatLong.longitude);
            }
            //1 meters minimum distance for the point to be added
            if (lastSavedLocation == null || currentLocation.distanceTo(lastSavedLocation) > 1) {
                LatLng latLng = new LatLng(currentLocation.getLatitude(),
                        currentLocation.getLongitude());
                locations.add(latLng);
                mMap.clear();

                PolylineOptions line=
                        new PolylineOptions().addAll(locations)
                                .width(2).color(0xEE736357);

                mMap.addPolyline(line);

               for (LatLng l: locations) {
                    mMap.addMarker(new MarkerOptions().icon(bitmapDescriptor)
                            .position(l)
                            .title(l.toString()));
                }
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0F));
            }
        }
    }

    /**
     * Uses a {@link com.google.android.gms.location.LocationSettingsRequest.Builder} to build
     * a {@link com.google.android.gms.location.LocationSettingsRequest} that is used for checking
     * if a device has the needed location settings.
     */
    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    private void initLocationFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!activityJustCreated) {
            checkLocation();
        }
        activityJustCreated = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mFusedLocationClient != null && mLocationCallback != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.animateCamera(CameraUpdateFactory.zoomTo(17.0f));
        checkLocation();
    }

    @SuppressLint("MissingPermission")
    private void checkLocation() {
        if (PermissionUtils.isLocationPermissionGranted(this)) {
            mMap.setMyLocationEnabled(true);
            startLocationUpdates();
        } else {
            PermissionUtils.requestLocationPermissions(this);
        }
    }

    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    private void startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                mLocationCallback, Looper.myLooper());
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG,
                                        "Location settings are not satisfied. Attempting to upgrade "
                                                +
                                                "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(MapsActivity.this,
                                            REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage =
                                        "Location settings are inadequate, and cannot be " +
                                                "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                Toast.makeText(MapsActivity.this, errorMessage, Toast.LENGTH_LONG)
                                        .show();
                        }
                    }
                });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == PermissionUtils.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission granted");
                mMap.setMyLocationEnabled(true);
                startLocationUpdates();
            } else {
                // Permission denied.
                Log.i(TAG, "Permission denied");
                // Notify the user via a SnackBar that they have rejected a core permission for the
                // app, which makes the Activity useless. In a real app, core permissions would
                // typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                //TODO:
            }
        }
    }

    public class LatLngAcc {

        private LatLng latLng;
        private float accuracy = 0.0f;

        public LatLngAcc(Location location) {
            this.accuracy = location.getAccuracy();
            this.latLng = new LatLng(location.getLatitude(), location.getLongitude());
        }

        public float getAccuracy() {
            return accuracy;
        }

        public String getTitle() {
            return "Loc: " + latLng.latitude + ", " + latLng.longitude
                    + ", acc: " + accuracy;
        }
    }
}
