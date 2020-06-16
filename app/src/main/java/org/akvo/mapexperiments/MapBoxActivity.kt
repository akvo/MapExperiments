package org.akvo.mapexperiments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.offline.OfflineRegion
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition
import com.mapbox.mapboxsdk.plugins.annotation.CircleManager
import com.mapbox.mapboxsdk.plugins.annotation.CircleOptions
import com.mapbox.mapboxsdk.plugins.annotation.FillManager
import com.mapbox.mapboxsdk.plugins.annotation.FillOptions
import com.mapbox.mapboxsdk.plugins.annotation.LineManager
import com.mapbox.mapboxsdk.plugins.annotation.LineOptions
import org.akvo.mapexperiments.RegionsListDialogFragment.RegionsSelectionListener
import java.util.ArrayList

class MapBoxActivity : LocationAwareActivity(), RegionsSelectionListener {
    private val locations: MutableList<LatLng> = ArrayList()
    private val gson = Gson()
    private lateinit var mapboxMap: MapboxMap
    private lateinit var mapView: MapView
    private var sharedPreferences: SharedPreferences? = null
    private var manualAreaSelected = false
    private var enableTracking = false

    private lateinit var circleManager: CircleManager
    private lateinit var lineManager: LineManager
    private lateinit var fillManager: FillManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapbox)
        val toolbar =
            findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        sharedPreferences = getSharedPreferences(
            PREFERENCE_NAME,
            Context.MODE_PRIVATE
        )
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { mapboxMap ->
            onMapReady(mapboxMap)
        }
        findViewById<Button>(R.id.button2).setOnClickListener { v -> onButtonClick(v as Button) }
    }

    private fun onButtonClick(v: Button) {
        if (enableTracking) {
            v.setText(R.string.start_recording)
            enableTracking = false
            saveLocations()
            findViewById<TextView>(R.id.textView2).text = ""
        } else {
            locations.clear()
            redrawMap()
            enableTracking = true
            v.setText(R.string.stop_recording)
        }
    }

    private fun onMapReady(mapboxMap: MapboxMap) {
        this@MapBoxActivity.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.LIGHT) { style ->
            lineManager = LineManager(mapView, mapboxMap, style)
            circleManager = CircleManager(mapView, mapboxMap, style)
            fillManager = FillManager(mapView, mapboxMap, style)
            checkLocation()
        }

    }

    override fun updateNewLocation(location: Location?) {
        if (enableTracking) {
            if (location == null) {
                return
            }
            val latestLatLng = LatLngAcc(location)
            findViewById<TextView>(R.id.textView2).text = getString(R.string.last_location, latestLatLng.title)
            val lastLatLong =
                if (locations.size == 0) null else locations[locations.size - 1]
            //5 meters minimum distance for the point to be added
            if (lastLatLong == null
                || latestLatLng.distanceTo(lastLatLong) > MapOptions.MINIMUM_DISTANCE
            ) {
                if (!manualAreaSelected) {
                    var zoom =
                        if (lastLatLong == null) ZOOM_LEVEL else mapboxMap.cameraPosition.zoom
                    if (zoom == 0.0) {
                        zoom = ZOOM_LEVEL
                    }
                    mapboxMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            zoom
                        )
                    )
                }
                locations.add(latestLatLng)
                redrawMap()
            }
        }
    }

    override fun enableMapLocation() {
        //Ignore
    }

    private fun redrawMap() {
        circleManager.deleteAll()
        lineManager.deleteAll()
        fillManager.deleteAll()

        fillManager.create(
            FillOptions()
                .withLatLngs(arrayListOf(locations))
                .withFillColor("#736357")
                .withFillOpacity(0.5f)
                .withDraggable(false)
        )

        lineManager.create(
            LineOptions()
                .withLineColor("#736357")
                .withLineWidth(4f)
                .withDraggable(false)
                .withLatLngs(locations)
        )

        for (latLng in locations) {
            circleManager.create(
                CircleOptions()
                    .withLatLng(latLng)
                    .withCircleColor("#00a79d")
                    .withCircleRadius(8f)
                    .withCircleStrokeColor("#027a73")
                    .withDraggable(false)
                    .withCircleStrokeWidth(1f)
            )
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.mapbox_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save -> {
                saveLocations()
                true
            }
            R.id.load -> {
                val savedLocationsString =
                    sharedPreferences!!.getString(PREF_SHAPE, null)
                if (!TextUtils.isEmpty(savedLocationsString)) {
                    toast("Loading shape")
                    val type =
                        object : TypeToken<List<LatLngAcc?>?>() {}.type
                    val locationsPref =
                        gson.fromJson<List<LatLngAcc>>(
                            savedLocationsString,
                            type
                        )
                    locations.clear()
                    locations.addAll(locationsPref)
                    //TODO: center map on last location item.
                    redrawMap()
                }
                true
            }
            R.id.load_offline -> {
                val fragment = RegionsListDialogFragment.newInstance()
                fragment.show(supportFragmentManager, RegionsListDialogFragment.TAG)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveLocations() {
        val size = locations.size
        if (size > 0) {
            if (size > 2) {
                locations.add(locations[0])
            }
            val toJson = gson.toJson(locations)
            sharedPreferences!!.edit()
                .putString(PREF_SHAPE, toJson)
                .apply()
            toast("Shape saved")
            redrawMap()
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    override fun onAddNewSelected() {
        startActivity(Intent(this@MapBoxActivity, OfflineManagerActivity::class.java))
    }

    override fun onRegionSelected(region: OfflineRegion) {
        val definition = region.definition
        val bounds = definition
            .bounds
        val regionZoom = (definition as OfflineTilePyramidRegionDefinition)
            .minZoom

        // Create new camera position
        val cameraPosition =
            CameraPosition.Builder()
                .target(bounds.center)
                .zoom(regionZoom)
                .build()

        // Move camera to new position
        mapboxMap.moveCamera(
            CameraUpdateFactory
                .newCameraPosition(cameraPosition)
        )
        manualAreaSelected = true
    }

    class LatLngAcc internal constructor(location: Location) :
        LatLng(location) {
        var accuracy = 0.0f

        val title: String
            get() = ("Loc: " + latitude + ", " + longitude
                    + ", acc: " + accuracy)

        init {
            accuracy = location.accuracy
        }
    }

    companion object {
        const val PREFERENCE_NAME = "Prefs"
        const val PREF_SHAPE = "shape"
        const val ZOOM_LEVEL = 16.0
    }
}