package mobilegis.ikg.ethz.lbsfitnessapp;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * This activity is used to review track.
 * Show user_id list;
 * Show tracks of selected user_id;
 * Show detailed information when clicking track.
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */
public class ReviewActivity extends AppCompatActivity {

    private static final String TAG = "ReviewActivity";

    // References to GUI elements.
    private MapView mMapView;
    private Callout mCallout;
    private LinearLayout layoutBack;
    private Spinner userIDSpinner;
    private Button btnSearch;

    // Define some variables.
    private ServiceFeatureTable mServiceFeatureTable;

    ArrayAdapter<String> userIDAdapter;
    ArrayList<String> userIDs;

    private String userID;
    private String selectedUserID;
    private Boolean finishTrack;
    private TrackRecord trackRecord;


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        // Initialize references to GUI elements.
        layoutBack = (LinearLayout) findViewById(R.id.backFromReview);
        btnSearch = (Button) findViewById(R.id.searchButton);

        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            userID = extras.getString("userID");
            finishTrack = extras.getBoolean("finishTrack");
            if (finishTrack) {
                trackRecord = (TrackRecord) extras.getSerializable("trackRecord");
            }

        }

        userIDs = new ArrayList<>();
        userIDs.add("User ID:");

        // Set your API key, Read the api key from string.xml
        // authentication with an API key or named user is required to access basemaps and other
        // location services
        ArcGISRuntimeEnvironment.setApiKey(getString(R.string.arcGISAPIKey));

        // Inflate MapView from layout
        mMapView = findViewById(R.id.mapView);

        mServiceFeatureTable = new ServiceFeatureTable(getString(R.string.url_track));
        mServiceFeatureTable.loadAsync();
        mServiceFeatureTable.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                if (mServiceFeatureTable.getLoadStatus() == LoadStatus.LOADED) {
                    Log.d(TAG, getString(R.string.succeed_load));
                } else {
                    Toast.makeText(ReviewActivity.this, getString(R.string.fail_load), Toast.LENGTH_LONG).show();
                }
            }
        });


        // Create a map with the a topographic basemap
        ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, 47.408992, 8.507847, 15);
        mMapView.setMap(map);

        mCallout = mMapView.getCallout();

        setUserIDSpinner();

        // Define what the back button will do on a click.
        layoutBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent back = new Intent(ReviewActivity.this, MainActivity.class);
                back.putExtra("userID", userID);
                back.putExtra("finishTrack", finishTrack);
                if (finishTrack){
                    back.putExtra("trackRecord", trackRecord);
                }
                back.setAction("fromReview");
                startActivity(back);
            }
        });
        // Define what the search button will do on a click.
        btnSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                triggerBtnSearchAction();
            }
        });

        // Set an on touch listener to listen for click events
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Remove any existing callouts
                if (mCallout.isShowing()) {
                    mCallout.dismiss();
                }
                // Get the point that was clicked and convert it to a point in map coordinates
                final Point clickPoint = mMapView.screenToLocation(new android.graphics.Point(Math.round(e.getX()), Math.round(e.getY())));
                // Create a selection tolerance
                double tolerance = 10;
                double mapTolerance = tolerance * mMapView.getUnitsPerDensityIndependentPixel();
                // Use tolerance to create an envelope to query
                Envelope envelope = new Envelope(clickPoint.getX() - mapTolerance, clickPoint.getY() - mapTolerance,
                        clickPoint.getX() + mapTolerance, clickPoint.getY() + mapTolerance, map.getSpatialReference());
                QueryParameters query = new QueryParameters();
                query.setGeometry(envelope);
                if (selectedUserID != null) {
                    query.setWhereClause("user_id =" + selectedUserID);
                }
                // Request all available attribute fields
                final ListenableFuture<FeatureQueryResult> future = mServiceFeatureTable.queryFeaturesAsync(query,
                        ServiceFeatureTable.QueryFeatureFields.LOAD_ALL);
                // Add done loading listener to fire when the selection returns
                future.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Get the result
                            FeatureQueryResult result = future.get();
                            // Create an Iterator
                            Iterator<Feature> iterator = result.iterator();
                            // Create a TextView to display field values
                            TextView calloutContent = new TextView(getApplicationContext());
                            calloutContent.setTextColor(Color.BLACK);
                            calloutContent.setSingleLine(false);
                            calloutContent.setVerticalScrollBarEnabled(true);
                            calloutContent.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);

                            calloutContent.setMovementMethod(new ScrollingMovementMethod());
                            calloutContent.setLines(5);
                            // Cycle through selections
                            Feature feature;
                            while (iterator.hasNext()) {
                                feature = iterator.next();
                                // Create a Map of all available attributes as name value pairs
                                Map<String, Object> attr = feature.getAttributes();

                                Set<String> keys = attr.keySet();
                                for (String key : keys) {
                                    Object value = attr.get(key);
                                    if (value instanceof GregorianCalendar) {
                                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
                                        value = simpleDateFormat.format(((GregorianCalendar) value).getTime());
                                    }
                                    // Append name value pairs to TextView
                                    if (key.equals("track_id") || key.equals("user_id") || key.equals("CreationDate")) {
                                        calloutContent.append(key + ": " + value + "\n");
                                    }
                                    if (key.equals("distance")) {
                                        calloutContent.append(key + ": " + String.format("%.3f", (Double) value) + "km\n");
                                    }
                                    if (key.equals("duration")) {
                                        calloutContent.append(key + ": " + value + "s\n");
                                    }
                                }
                                // Center the mapview on selected feature
                                Envelope envelope = feature.getGeometry().getExtent();
                                mMapView.setViewpointGeometryAsync(envelope, 200);
                                // Show CallOut
                                mCallout.setLocation(clickPoint);
                                mCallout.setContent(calloutContent);
                                mCallout.show();
                            }
                        } catch (Exception e) {
                            Log.e(getResources().getString(R.string.app_name), R.string.fail_select + e.getMessage());
                        }
                    }
                });
                return super.onSingleTapConfirmed(e);
            }
        });

    }

    /**
     * This method gets all userIDs by feature query and put them into a spinner.
     */
    public void setUserIDSpinner() {
        QueryParameters query = new QueryParameters();
        // Guest user with user_id = -1 is not included
        query.setWhereClause("user_id >= 0");
        final ListenableFuture<FeatureQueryResult> future = mServiceFeatureTable.queryFeaturesAsync(query);
        // Add done loading listener to fire when the selection returns
        future.addDoneListener(new Runnable() {
            @Override
            public void run() {
                try {
                    // Get the result
                    FeatureQueryResult result = future.get();
                    // Check there are some results
                    Iterator<Feature> resultIterator = result.iterator();

                    while (resultIterator.hasNext()) {
                        // Get the extent of the first feature in the result to zoom to
                        Feature feature = resultIterator.next();
                        Map<String, Object> attributes = feature.getAttributes();
                        String userID = attributes.get("user_id").toString();
                        if (userIDs.contains(userID)) continue;
                        userIDs.add(userID);
                    }

                    userIDs.toArray(new String[0]);
                    userIDSpinner = (Spinner) findViewById(R.id.spinnerReview);
                    userIDAdapter = new ArrayAdapter<String>(ReviewActivity.this,
                            android.R.layout.simple_list_item_1, android.R.id.text1, userIDs);
                    userIDAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    userIDSpinner.setAdapter(userIDAdapter);

                    userIDSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position != 0) {
                                selectedUserID = userIDs.get(position);
                            }
                        }

                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    });
                } catch (Exception e) {
                    String error = getString(R.string.fail_search) + e.getMessage();
                    Toast.makeText(ReviewActivity.this, error, Toast.LENGTH_LONG).show();
                    Log.e(TAG, error);
                }
            }
        });

    }

    /**
     * This method queries the track records of each individual user.
     */
    private void triggerBtnSearchAction() {
        // Remove any existing tracks
        mMapView.getGraphicsOverlays().clear();
        // Remove any existing callouts
        if (mCallout.isShowing()) {
            mCallout.dismiss();
        }
        // Start query
        QueryParameters query = new QueryParameters();
        query.setWhereClause("user_id =" + selectedUserID);
        final ListenableFuture<FeatureQueryResult> future = mServiceFeatureTable.queryFeaturesAsync(query);
        future.addDoneListener(new Runnable() {
            @Override
            public void run() {
                try {
                    // Get the result
                    FeatureQueryResult result = future.get();
                    // Check there are some results
                    Iterator<Feature> resultIterator = result.iterator();
                    // Create a TextView to display field values
                    while (resultIterator.hasNext()) {
                        Feature feature = resultIterator.next();
                        // Get geometry
                        Geometry track = feature.getGeometry();
                        if (track == null) continue;
                        // Set track as graphic on map
                        final GraphicsOverlay overlay = new GraphicsOverlay();
                        mMapView.getGraphicsOverlays().add(overlay);
                        SimpleLineSymbol routeSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.DASH, Color.BLUE, 3);
                        Graphic routeGraphic = new Graphic(track, routeSymbol);
                        overlay.getGraphics().add(routeGraphic);
                        Envelope envelope = feature.getGeometry().getExtent();
                        mMapView.setViewpointGeometryAsync(envelope, 50);
                    }

                } catch (Exception e) {
                    String error = getString(R.string.fail_search) + e.getMessage();
                    Toast.makeText(ReviewActivity.this, error, Toast.LENGTH_LONG).show();
                    Log.e(TAG, error);
                }
            }
        });

    }


}