package org.akvo.mapexperiments;

import android.util.Log;

import com.mapbox.mapboxsdk.offline.OfflineRegion;

import org.json.JSONObject;

class RegionMapper {
    private static final String TAG = "RegionMapper";
    private String defaultRegionName;

    RegionMapper(String defaultRegionName) {
        this.defaultRegionName = defaultRegionName;
    }

    String getRegionName(OfflineRegion offlineRegion) {
        // Get the region name from the offline region metadata
        String regionName;
        try {
            byte[] metadata = offlineRegion.getMetadata();
            String json = new String(metadata, OfflineManagerActivity.JSON_CHARSET);
            JSONObject jsonObject = new JSONObject(json);
            regionName = jsonObject.getString(OfflineManagerActivity.JSON_FIELD_REGION_NAME);
        } catch (Exception exception) {
            Log.e(TAG,
                    "Failed to decode metadata: " + exception.getMessage());
            regionName = String.format(defaultRegionName, offlineRegion.getID());
        }
        return regionName;
    }
}