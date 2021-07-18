package mobilegis.ikg.ethz.lbsfitnessapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeException;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureEditResult;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointCollection;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.loadable.LoadStatus;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * This activity contains many logical parts of the program, including:
 * Tracking whether the user arrives at the checkpoint and starting point;
 * Display the status (direction, distance, current speed, temperature), and write the track
 * result, etc;
 * Upload the track to ArcGIS server;
 * Some parts of onSensorChanged() are referenced from open source projects on the internet.
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */

public class TrackActivity extends AppCompatActivity implements LocationListener, SensorEventListener {


    // Define an Intent string.
    private static final String PROX_ALERT_INTENT =
            "mobgis.ikg.ethz.lbsfitnessapp.PROXIMITY_ALERT";
    private static final String TAG = "TrackActivity";

    // References to GUI elements.
    private TextView directionTxtView;
    private TextView distanceTxtView;
    private TextView speedTxtView;
    private TextView temperatureTxtView;
    private LinearLayout layoutBack;
    private ImageView compass, arrow;
    private Button bthUploadTrack;

    private String userID;

    // The name and location of the checkpoint selected by user.
    private String targetName;
    private Location targetLocation;

    // The name and location of the starting location.
    private String startName;
    private Location startLocation;

    // The track that stores longitude and latitude.
    private ArrayList<Double> track;

    // The geofence centered on checkpoint and start location.
    private GeoFence targetLocationGeoFence;
    private GeoFence startLocationGeoFence;

    private TrackRecord trackRecord;

    // Last location used for calculating distance changes.
    private Location lastLocation;

    // Set up some variables
    private Map<String, Double> lastDistToCenter;

    private List<Float> temperatureArray;
    private List<Float> distanceArray;

    private LocationManager locationManager;
    private SensorManager sensorManager;
    private ProximityIntentReceiver proximityIntentReceiver;

    private float direction;
    private Sensor temSensor;
    private Sensor accSensor;
    private Sensor magSensor;
    private Boolean isTemSensorAvailable;
    private Boolean isAccSensorAvailable;
    private Boolean isMagSensorAvailable;

    // Check if the user is on the return journey.
    private Boolean goingBack;

    // Check if the user have finished this track.
    private Boolean finishTrack;

    // Initialize some parameters used to calculate compass rotation.
    private float[] gravity = new float[3];
    private float[] rotation = new float[16];
    private float[] inclination = new float[16];
    private float[] magnetic = new float[3];
    private float[] orientation = new float[3];
    private float north_azimuth = 0f;
    private float target_azimuth = 0f;
    private float currentAzimuth = 0f;
    private float currentTargetAzimuth = 0f;

    // FeatureTable for round trip track and checkpoint
    ServiceFeatureTable trackFeatureTable;
    ServiceFeatureTable checkPointFeatureTable;

    private Integer trackID;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track);

        // Initialize references to GUI elements.
        layoutBack = (LinearLayout) findViewById(R.id.back);
        distanceTxtView = (TextView) findViewById(R.id.distanceValue);
        directionTxtView = (TextView) findViewById(R.id.directionValue);
        speedTxtView = (TextView) findViewById(R.id.currentSpeedValue);
        temperatureTxtView = (TextView) findViewById(R.id.temperatureValue);
        compass = (ImageView) findViewById(R.id.compass);
        arrow = (ImageView) findViewById(R.id.arrow);
        bthUploadTrack = (Button) findViewById(R.id.trackUploadButton);

        // Create a Map where we store the distances to the individual geofence centers.
        lastDistToCenter = new HashMap<>();

        temperatureArray = new ArrayList<>();
        distanceArray = new ArrayList<>();

        // Get information about the Intent which started this Activity.
        Intent intent = getIntent();
        Bundle extras = getIntent().getExtras();

        // Initialize track and trackID
        track = new ArrayList<Double>();
        trackID = 1;

        trackFeatureTable = new ServiceFeatureTable(getString(R.string.url_track));
        checkPointFeatureTable = new ServiceFeatureTable(getString(R.string.url_checkPoint));

        // Turn the content into a location.
        targetLocation = new Location("");
        if (extras != null) {
            userID = intent.getStringExtra("userID");
            targetName = intent.getStringExtra("name");
            targetLocation.setLongitude(extras.getDouble("longitude"));
            targetLocation.setLatitude(extras.getDouble("latitude"));
        }

        startName = "start location";

        // Initialize track record.
        trackRecord = new TrackRecord(Calendar.getInstance().getTimeInMillis(), targetLocation);

        // Create a geofence centered on the target location .
        targetLocationGeoFence = new GeoFence(targetName, targetLocation.getLatitude(),
                targetLocation.getLongitude(), 50.00);

        // At first, the user is on the journey to the target location.
        goingBack = false;
        finishTrack = false;

        // Define what the back will do on a click.
        layoutBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
//                if (finishTrack) {
//                    socialSharing(userID, trackRecord);
//                } else {

                    Intent back = new Intent(TrackActivity.this, MainActivity.class);
                    back.putExtra("userID", userID);
                    back.putExtra("finishTrack", finishTrack);
                    if (finishTrack) {
                        back.putExtra("trackRecord", trackRecord);
                    }
                    startActivity(back);


            }
        });

        // Define what the upload track button will do on a click.
        bthUploadTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadTrack();
            }
        });

        // loadAsync the track feature table and then get the maximum track id of the user.
        trackFeatureTable.loadAsync();
        trackFeatureTable.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                if (trackFeatureTable.getLoadStatus() == LoadStatus.LOADED) {
                    getTrackID();
                } else {
                    Toast.makeText(TrackActivity.this, getString(R.string.fail_load),
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        // loadAsync the check point feature table.
        checkPointFeatureTable.loadAsync();
        checkPointFeatureTable.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                if (checkPointFeatureTable.getLoadStatus() == LoadStatus.LOADED) {
                    Log.d(TAG, getString(R.string.succeed_load));
                } else {
                    Toast.makeText(TrackActivity.this, getString(R.string.fail_load),
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        lastDistToCenter.put(targetName, Double.MAX_VALUE);
        lastDistToCenter.put(startName, Double.MAX_VALUE);

        // Check the location permission.
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
//        checkLocationPermissions();
        proximityIntentReceiver = new ProximityIntentReceiver();

        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        // Check sensor's availability.
        if (sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null) {
            temSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
            isTemSensorAvailable = true;
        } else {
            Log.d(TAG, getString(R.string.temperature_error));
            isTemSensorAvailable = false;
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            isAccSensorAvailable = true;
        } else {
            Log.d(TAG, getString(R.string.accelerometer_error));
            isAccSensorAvailable = false;
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
            magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            isMagSensorAvailable = true;
        } else {
            Log.d(TAG, getString(R.string.magnetic_error));
            isMagSensorAvailable = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register sensor listener.
        checkLocationPermissions();
        if (isTemSensorAvailable) {
            sensorManager.registerListener(this, temSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        if (isAccSensorAvailable) {
            sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        if (isMagSensorAvailable) {
            sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        // Register proximity intent receiver.
        registerReceiver(proximityIntentReceiver, new IntentFilter(PROX_ALERT_INTENT));

    }


    /**
     * We use this function to check for location permissions, and pop up a box asking for them,
     * in case a user hasn't given them yet.
     */
    private void checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        } else {

            // If the permission is given, set the update rate of location manager.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000,
                    5, this);
            // Use the last known location from the start of the activity as the user's starting location.
            startLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            lastLocation = startLocation;
            // create geofence centered on the starting location.
            startLocationGeoFence = new GeoFence(startName, startLocation.getLatitude(), startLocation.getLongitude(), 50.00);

            trackRecord.setStartLocation(startLocation);

//            track.add(startLocation.getLongitude());
//            track.add(startLocation.getLatitude());
        }
    }

    @Override
    public boolean shouldShowRequestPermissionRationale(@NonNull String permission) {
        return super.shouldShowRequestPermissionRationale(permission);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // Permission is granted
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(this,
                                    Manifest.permission.ACCESS_COARSE_LOCATION) ==
                                    PackageManager.PERMISSION_GRANTED) {

                        // If the permission is given, set the update rate of location manager.
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                1000, 5, this);
                        // Use the last known location from the start of the activity as the user's starting location.
                        startLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        lastLocation = startLocation;
                        // create geofence centered on the starting location.
                        startLocationGeoFence = new GeoFence(startName, startLocation.getLatitude(),
                                startLocation.getLongitude(), 50.00);
                        trackRecord.setStartLocation(startLocation);

//                        track.add(startLocation.getLongitude());
//                        track.add(startLocation.getLatitude());

                    }

                } else {
                    Toast.makeText(this, getString(R.string.no_location_enabled),
                            Toast.LENGTH_LONG).show();
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }

    }


    @Override
    public void onLocationChanged(Location location) {

        // Add every location into track
        track.add(location.getLongitude());
        track.add(location.getLatitude());

        // Put relative distance when location change, and sum up these distance to get the total
        // distance in the end.
        distanceArray.add(lastLocation.distanceTo(location));
        lastLocation = location;

        double distance;
        double speed = location.getSpeed();

        speedTxtView.setText(String.format("%.2f m/s", speed));

        // If the user is on the journey to the target location.
        if (!goingBack) {
            // The direction of current location to target location.
            direction = location.bearingTo(targetLocation);
            // The distance of current location to target location.
            distance = targetLocation.distanceTo(location);

            if (targetLocationGeoFence.isEntering(distance, lastDistToCenter.get(targetName))) {

                sendProximityIntent(targetName, true, null);
                goingBack = true;
            }

            lastDistToCenter.put(targetName, distance);

        } else {  // If the user is on the journey to the starting location.
            // The direction of current location to stating location.
            direction = location.bearingTo(startLocation);
            // The distance of current location to stating location.
            distance = startLocation.distanceTo(location);

            if (startLocationGeoFence.isEntering(distance, lastDistToCenter.get(startName))) {

                trackRecord.setDistance(distanceArray);
                trackRecord.setAverageTemperature(temperatureArray);
                trackRecord.setEndTime(Calendar.getInstance().getTimeInMillis());
                trackRecord.saveResult(this);

                sendProximityIntent(startName, true, trackRecord.getReward());
                finishTrack = true;

            }

            lastDistToCenter.put(startName, distance);
        }

        // Update the status info.
        directionTxtView.setText(String.format("%.2f °", direction));
        distanceTxtView.setText(String.format("%.2f m", distance));
    }

    /**
     * We use this method to send an intent stating that we are in proximity of a certain object,
     * denoted by its "name". The boolean passed along tells us if we are entering of leaving
     * the proximity.
     *
     * @param name     The name of the proximity area.
     * @param entering True if we're entering, false otherwise.
     */
    private void sendProximityIntent(String name, boolean entering, String award) {
        Intent i = new Intent(PROX_ALERT_INTENT);
        i.putExtra("name", name);
        if (award != null) {
            i.putExtra("award", award);
        }
        i.putExtra(LocationManager.KEY_PROXIMITY_ENTERING, entering);

        this.sendBroadcast(i);
    }

    /**
     * We use this method to update the compass dynamically.
     *
     * @param fromDegree The degree where the rotate animation begins from.
     * @param toDegree   The degree where the rotate animation goes to.
     * @param image      The image to be rotated.
     */
    public void rotateCompass(float fromDegree, float toDegree, ImageView image) {
        RotateAnimation rotateAnimation = new RotateAnimation(fromDegree, toDegree, Animation.RELATIVE_TO_SELF,
                0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

        rotateAnimation.setDuration(200);
        rotateAnimation.setFillAfter(true);
        rotateAnimation.setRepeatCount(0);
        image.startAnimation(rotateAnimation);
    }


    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    /**
     * This method is used to update temperature and calculate the direction from the phone's orientation
     * to the destination. Some parts of the code are referenced from open source projects on the internet.
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        final float alpha = 0.8f;

        if (sensorEvent.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            temperatureArray.add(sensorEvent.values[0]);
            temperatureTxtView.setText(sensorEvent.values[0] + " °C");
        }

        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity[0] = alpha * gravity[0] + (1 - alpha) * sensorEvent.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * sensorEvent.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * sensorEvent.values[2];
        }

        if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetic[0] = alpha * magnetic[0] + (1 - alpha) * sensorEvent.values[0];
            magnetic[1] = alpha * magnetic[1] + (1 - alpha) * sensorEvent.values[1];
            magnetic[2] = alpha * magnetic[2] + (1 - alpha) * sensorEvent.values[2];
        }

        SensorManager.getRotationMatrix(rotation, inclination, gravity, magnetic);
        SensorManager.getOrientation(rotation, orientation);

        // Get the azimuth relative to north.
        north_azimuth = (float) Math.toDegrees(orientation[0]);
        north_azimuth = (360 + north_azimuth) % 360;
        target_azimuth = north_azimuth - (360 + direction) % 360;

        // Rotate the compass.
        rotateCompass(-currentAzimuth, -north_azimuth, compass);

        // Rotate the arrow pointing to the destination.
        rotateCompass(-currentTargetAzimuth, -target_azimuth, arrow);

        // Set currentAzimuth to current angle.
        currentAzimuth = north_azimuth;
        currentTargetAzimuth = target_azimuth;


    }

    /**
     * This method is used to get the maximum trackID of the user, and set the new trackID = maximum
     * trackID + 1. If the user is new or there is no existing track, then trackID = 1; If the user
     * is guest, then trackID = -1.
     */
    public void getTrackID() {
        if (userID.equals("guest")) {
            trackID = -1;
            return;
        }
        // Query existing trackID according to userID
        final ArrayList<Integer> existID = new ArrayList<Integer>();
        QueryParameters query = new QueryParameters();
        query.setWhereClause("user_id = " + userID);
        final ListenableFuture<FeatureQueryResult> future = trackFeatureTable.queryFeaturesAsync(query);

        // Add done loading listener to fire when the query returns
        future.addDoneListener(new Runnable() {
            @Override
            public void run() {
                try {
                    // Get the result
                    FeatureQueryResult result = future.get();
                    Iterator<Feature> resultIterator = result.iterator();
                    // Check if there are some results
                    if (resultIterator.hasNext()) {
                        while (resultIterator.hasNext()) {
                            Feature feature = resultIterator.next();
                            // create a Map of all available attributes as name value pairs
                            Map<String, Object> attr = feature.getAttributes();
                            Integer currentID = (Integer) attr.get("track_id");
                            existID.add(currentID);
                        }
                        if (!existID.isEmpty()) {
                            trackID = Collections.max(existID) + 1;
                        }
                    }
                    // If empty result
                    else {
                        trackID = 1;
                    }
                } catch (Exception e) {
                    String error = getString(R.string.fail_search) + e.getMessage();
                    Toast.makeText(TrackActivity.this, error, Toast.LENGTH_LONG).show();
                    Log.e(TAG, error);
                }
            }
        });
    }


    /**
     * This method is used to upload the track to the ArcGIS server. Only when the user finish the
     * track, he can upload it.
     */
    public void uploadTrack() {
        // Check if the user have finished the track
        if (finishTrack) {

            Map<String, Object> attributes = new HashMap<>();
            // Use -1 as userID for guest user
            if (userID.equals("guest")) {
                userID = String.valueOf(-1);
            }

            // Set attributes for the track
            attributes.put("start_timestamp", Long.toString(trackRecord.getStartTime()));
            attributes.put("user_id", Integer.valueOf(userID));
            attributes.put("track_id", trackID);
            attributes.put("reward", trackRecord.getReward());
            attributes.put("distance", trackRecord.getDistance());
            attributes.put("duration", (double) trackRecord.getDuration() / 1000);
            attributes.put("average_speed", trackRecord.getAverageSpeed());
            attributes.put("average_temp", trackRecord.getAverageTemperature());

            ArrayList<Point> points = new ArrayList<Point>();

            // Traverse the track array list
            for (int i = 0; i < track.size() / 2; i++) {
                points.add(new Point(track.get(2 * i), track.get(2 * i + 1)));
            }

            // Create a pointSet from points
            PointCollection pointSet = new PointCollection(points);
            // Create a polyline from pointSet
            Polyline lines = new Polyline(pointSet, SpatialReferences.getWgs84());

            // Add new features to the service feature table
            addFeature(attributes, lines, trackFeatureTable);
            Toast.makeText(this, R.string.succeed_upload, Toast.LENGTH_LONG).show();

            track = new ArrayList<Double>();


        } else {
            Toast.makeText(TrackActivity.this, getString(R.string.empty_track), Toast.LENGTH_LONG).show();
            return;
        }

        // Upload check point to the ArcGIS server
        uploadCheckPoint();
    }

    /**
     * This method is used to upload the check point to the ArcGIS server. Only when the user finish the
     * track, he can upload it.
     */
    public void uploadCheckPoint() {

        if (targetLocation != null) {
            double lon = targetLocation.getLongitude();
            double lat = targetLocation.getLatitude();

            String arrivalTimeStamp = Long.toString(trackRecord.getEndTime());
            String checkPointName = targetName;

            Point checkPoint = new Point(lon, lat, SpatialReferences.getWgs84());
            Map<String, Object> attributes = new HashMap<>();

            // Use -1 as userID for guest user
            if (userID.equals("guest")) {
                userID = String.valueOf(-1);
            }

            // Set attributes for the track
            attributes.put("arrival_timestamp", arrivalTimeStamp);
            attributes.put("user_id", Integer.valueOf(userID));
            attributes.put("track_id", trackID);
            attributes.put("checkpoint_name", checkPointName);

            // Add new features to the service feature table
            addFeature(attributes, checkPoint, checkPointFeatureTable);
            Toast.makeText(this, R.string.succeed_upload, Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Adds a new Feature to a ServiceFeatureTable and applies the changes to the server.
     *
     * @param attributes   Attributes of table
     * @param geom         Geometry feature
     * @param featureTable Service feature table to add feature
     */
    private void addFeature(Map<String, Object> attributes, Geometry geom, final ServiceFeatureTable featureTable) {

        // Create a new feature from the attributes and existing geometry, and then add the feature.
        Feature feature = featureTable.createFeature(attributes, geom);
        // check if feature can be added to feature table
        if (featureTable.canAdd()) {
            // add the new feature to the feature table and to server
            featureTable.addFeatureAsync(feature).addDoneListener(() -> applyEdits(featureTable));
        } else {
            runOnUiThread(() -> Toast.makeText(this, R.string.fail_upload, Toast.LENGTH_LONG).show());
        }
    }

    /**
     * Sends any edits on the ServiceFeatureTable to the server.
     *
     * @param featureTable service feature table
     */
    private void applyEdits(ServiceFeatureTable featureTable) {

        // Apply the changes to the server
        final ListenableFuture<List<FeatureEditResult>> editResult = featureTable.applyEditsAsync();
        editResult.addDoneListener(() -> {
            try {
                List<FeatureEditResult> editResults = editResult.get();
                // Check if the server edit was successful
                if (editResults != null && !editResults.isEmpty()) {
                    if (!editResults.get(0).hasCompletedWithErrors()) {
                        Log.d(TAG, getString(R.string.feature_added));
                    } else {
                        throw editResults.get(0).getError();
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                Toast.makeText(this, R.string.error_applying_edits, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isTemSensorAvailable) {
            sensorManager.unregisterListener(this);
        }
        unregisterReceiver(proximityIntentReceiver);
        locationManager.removeUpdates(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister all listeners.
        locationManager.removeUpdates(this);
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onBackPressed() {
        if (finishTrack) {
            socialSharing(userID, trackRecord);
        } else {
            TrackActivity.this.finish();
        }

    }

    /**
     * This method asks user to select the sharing app of choice (Facebook, Twitter, SMS, Mail, etc.).
     * A small text about the user id, distance, duration, and the type of reward will be shared.
     *
     * @param userID      user's ID
     * @param trackRecord the trackRecord of the finished track
     */
    public void socialSharing(String userID, TrackRecord trackRecord) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(TrackActivity.this);
        alertDialog.setTitle("Social Sharing");
        alertDialog.setMessage(R.string.social_sharing);
        alertDialog.setPositiveButton("yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Game Result");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "User ID: " + userID + ", Distance: " +
                        String.format("%.2f", trackRecord.getDistance()) + "km, Duration: " +
                        Long.toString(trackRecord.getDuration()) + "ms, Reward: " +
                        trackRecord.getReward());
                startActivity(Intent.createChooser(shareIntent, "Share via "));

                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });

        alertDialog.setNegativeButton("no", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });

        alertDialog.show();
    }

}