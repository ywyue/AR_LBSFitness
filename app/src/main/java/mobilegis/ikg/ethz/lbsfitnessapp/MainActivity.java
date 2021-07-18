package mobilegis.ikg.ethz.lbsfitnessapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * This activity serves as an main control page.
 *      Show checkpoint list;
 *      Go to track review page;
 *      Social sharing when exiting app.
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */
public class MainActivity extends AppCompatActivity {

    // References to GUI elements.
    private Button btnStart;
    private Button btnTrackReview;
    private Button btnARView;
    private Button btnExit;
    private Button btnHelp;

    private Spinner spinner;

    // Define some variables.
    private List<CheckPoint> checkPoints;
    private CheckPoint selectedPoint;
    private String userID;
    private TrackRecord trackRecord;
    private Boolean finishTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize references to GUI elements.
        btnStart = (Button) findViewById(R.id.startButton);
        btnTrackReview = (Button) findViewById(R.id.trackReviewButton);
        btnARView = (Button) findViewById(R.id.ARViewButton);

        btnExit = (Button) findViewById(R.id.exitButton);
        btnHelp = (Button) findViewById(R.id.helpButton);

        spinner = (Spinner) findViewById(R.id.spinner);

        checkPoints = new ArrayList<>();

        // Load checkpoints from the file system.
        loadCheckPointsData();
        // Check the permission to write file to the file system.
        checkWritePermissions();

        finishTrack = false;

        // Get information about the Intent which started this Activity.
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            userID = extras.getString("userID");
            finishTrack = extras.getBoolean("finishTrack");
            if (finishTrack){
                trackRecord = (TrackRecord) extras.getSerializable("trackRecord");
            }

        }

        // Create adapter to show the drop-down
        ArrayAdapter<CheckPoint> adapter = new ArrayAdapter<CheckPoint>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, checkPoints);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        // Set action when user select a checkpoint
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPoint = checkPoints.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // Define what the start button will do on a click.
        btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                triggerBtnStartAction();
            }
        });

        // Define what the track review button will do on a click.
        btnTrackReview.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                triggerBtnTrackReviewAction();
            }
        });

        // Define what the ar view button will do on a click.
        btnARView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                triggerBtnARViewAction();
            }
        });

        // Define what the exit button will do on a click.
        btnExit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                triggerBtnExitAction();
            }
        });

        // Define what the help button will do on a click.
        btnHelp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                triggerBtnHelpAction();
            }
        });
    }

    /**
     * This method sends the information of user selected checkpoint (name, longitude, latitude) and
     * userID to TrackActivity using an intent.
     */
    private void triggerBtnStartAction() {
        try {
            // Create a new intent and put name, longitude, latitude of user selected checkpoint
            Intent intent = new Intent(MainActivity.this, TrackActivity.class);
            intent.putExtra("userID", userID);
            intent.putExtra("name", selectedPoint.getName());
            intent.putExtra("latitude", selectedPoint.getLatitude());
            intent.putExtra("longitude", selectedPoint.getLongitude());

            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.intent_error, Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * This method reads a list of possible checkpoints from the file system. Each checkpoint
     * contains a name and the latitude and longitude of the point.
     */
    private boolean loadCheckPointsData() {
        try {
            InputStream inputStream = getResources().openRawResource(R.raw.checkpoints);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, Charset.forName("UTF-8")));
            String line = "";
            int row = 0;
            while ((line = reader.readLine()) != null) {
                // skip the head row
                if (row == 0) {
                    ++row;
                    continue;
                }
                // Split the line into different parts (using the ";" as a separator).
                String[] parse = line.split(";");

                Log.d("MainActivity", "load checkpoints: parsed " + parse[0] + "," +
                        parse[1] + "," + parse[2]);
                CheckPoint point = new CheckPoint(parse[0], Double.valueOf(parse[1]),
                        Double.valueOf(parse[2]));
                checkPoints.add(point);
                ++row;
            }
            Log.d("MainActivity", "load checkpoints: finished loading " +
                    (row - 1) + " points");
            return true;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return false;
    }

    /**
     * This method checks the permission to write track result to the file system. If the permission
     * has not been not granted yet, then ask user to give permission.
     */
    public void checkWritePermissions() {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else {
            Log.d("MainActivity", "checkWritePermission: permission granted");
        }
    }

    /**
     * This method sends userID to ReviewActivity using an intent.
     */
    private void triggerBtnTrackReviewAction() {
        try {
            Intent reviewIntent = new Intent(MainActivity.this, ReviewActivity.class);
            reviewIntent.putExtra("userID", userID);
            reviewIntent.putExtra("finishTrack", finishTrack);
            if (finishTrack){
                reviewIntent.putExtra("trackRecord", trackRecord);
            }

            startActivity(reviewIntent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.intent_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * This method sends userID to ARViewActivity using an intent.
     */
    private void triggerBtnARViewAction() {
        try {
            Intent reviewIntent = new Intent(MainActivity.this, ARViewActivity.class);
            reviewIntent.putExtra("userID", userID);
            reviewIntent.putExtra("finishTrack", finishTrack);
            if (finishTrack){
                reviewIntent.putExtra("trackRecord", trackRecord);
            }

            startActivity(reviewIntent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.intent_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * This method directs the user to the welcome page and ask user to share track results.
     */
    private void triggerBtnExitAction() {
        try {
            if (trackRecord!=null){
                socialSharing(userID,trackRecord);
            }
            else {
            Intent reviewIntent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(reviewIntent);
            }

        } catch (Exception e) {
            Toast.makeText(this, R.string.intent_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * This method directs the user to the welcome page and ask user to share track results.
     */
    private void triggerBtnHelpAction() {
        try {
            Intent reviewIntent = new Intent(MainActivity.this, HelpActivity.class);
            reviewIntent.putExtra("userID", userID);
            reviewIntent.putExtra("finishTrack", finishTrack);
            if (finishTrack){
                reviewIntent.putExtra("trackRecord", trackRecord);
            }

            startActivity(reviewIntent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.intent_error, Toast.LENGTH_SHORT).show();
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
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
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