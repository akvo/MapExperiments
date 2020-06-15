package org.akvo.mapexperiments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;

import java.util.ArrayList;
import java.util.List;

public class RegionsListDialogFragment extends DialogFragment {

    static final String TAG = "RegionsListDialogFragment";

    private RegionsAdapter adapter;
    private OfflineRegion[] offlineRegions;
    private RegionMapper regionMapper;
    private RegionsSelectionListener listener;

    public RegionsListDialogFragment() {
    }

    public static RegionsListDialogFragment newInstance() {
        return new RegionsListDialogFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Activity activity = getActivity();
        if (activity instanceof RegionsSelectionListener) {
            listener = (RegionsSelectionListener) activity;
        } else {
            throw new IllegalArgumentException("activity must implement RegionsSelectionListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new RegionsAdapter(getActivity());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        OfflineManager offlineManager = OfflineManager.getInstance(getActivity());
        regionMapper = new RegionMapper(getString(R.string.region_name));

        // Query the DB asynchronously
        offlineManager.listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
            @Override
            public void onList(final OfflineRegion[] offlineRegions) {
                setOfflineRegions(offlineRegions);
            }

            @SuppressLint("LongLogTag")
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error: " + error);
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    private void setOfflineRegions(OfflineRegion[] offlineRegions) {
        if (offlineRegions != null && offlineRegions.length > 0) {
            this.offlineRegions = offlineRegions;
            ArrayList<String> offlineRegionsNames = new ArrayList<>();
            for (OfflineRegion offlineRegion : offlineRegions) {
                offlineRegionsNames.add(regionMapper.getRegionName(offlineRegion));
            }
            adapter.setOfflineRegions(offlineRegionsNames);
        } else {
            this.offlineRegions = new OfflineRegion[0];
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.navigate_title))
                .setSingleChoiceItems(adapter, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        OfflineRegion region = offlineRegions[which];
                        listener.onRegionSelected(region);
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(getString(R.string.navigate_positive_button),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                listener.onAddNewSelected();
                            }
                        })
                .setNegativeButton(getString(R.string.navigate_negative_button_title),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                // EMPTY
                            }
                        });
        return dialog.create();
    }

    private static class RegionsAdapter extends ArrayAdapter<String> {

        private final ArrayList<String> offlineRegionsNames = new ArrayList<>();
        private final LayoutInflater inflater;

        RegionsAdapter(Context context) {
            super(context, 0);
            this.inflater = LayoutInflater.from(context);
        }

        void setOfflineRegions(List<String> items) {
            offlineRegionsNames.addAll(items);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return offlineRegionsNames.size();
        }

        @Override
        public String getItem(int i) {
            return offlineRegionsNames.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view;
            if (convertView == null) {
                view = inflater.inflate(R.layout.region_item, parent, false);
            } else {
                view = convertView;
            }
            ((TextView) view.findViewById(R.id.region_tv)).setText(getItem(position));
            return view;
        }
    }

    public interface RegionsSelectionListener {

        void onAddNewSelected();

        void onRegionSelected(OfflineRegion region);
    }
}
