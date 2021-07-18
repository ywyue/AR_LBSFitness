package mobilegis.ikg.ethz.lbsfitnessapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.QueryParameters;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.rendering.ModelRenderable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;

import android.util.Log;

// For Spatial Anchor
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Scene;
import com.microsoft.azure.spatialanchors.AnchorLocatedEvent;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchorSession;
import com.microsoft.azure.spatialanchors.CloudSpatialException;
import com.microsoft.azure.spatialanchors.LocateAnchorsCompletedEvent;
import com.microsoft.azure.spatialanchors.NearAnchorCriteria;
import com.microsoft.azure.spatialanchors.SessionLogLevel;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.microsoft.azure.spatialanchors.AnchorLocateCriteria;
import com.microsoft.azure.spatialanchors.LocateAnchorStatus;

import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.microsoft.azure.spatialanchors.SessionUpdatedEvent;


/**
 * This activity contains many logical parts of the AR view, including:
 * 1. Decide between the placing mode or locating mode by checking if rewards have already
 * been placed earlier.
 * 2. If in placing mode, then allow users to place rewards in the environment and upload the anchor
 * to cloud, and save anchor ID locally.
 * 3. If in locating mode, then allow users to scan the environment to re-establish the previous session.
 * 4. Communicate the whole process of uploading spatial anchor for better user experience.
 * 5. Query reward counts from ArcGIS server.
 * Note: Some parts of the code are referenced from azure-spatial-anchors-sample provided by Microsoft.
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */

public class ARViewActivity extends AppCompatActivity {

    private static final String TAG = "ARViewActivity";

    private LinearLayout layoutBack;
    private Spinner rewardSpinner;
    ArrayAdapter<String> rewardAdapter;
    Dialog popup;
    TextView popupText;
    TextView popupSubText;
    Button popupBtn;


    // boolean for checking if Google Play Services for AR if necessary.
    private boolean mUserRequestedInstall = true;

    // Camera Permission
    private static final int CAMERA_PERMISSION_CODE = 0;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;

    // Variables for tap and place
    private ArFragment arFragment;

    // Variables for loading 3d model and its title
    private Renderable model;
    private ViewRenderable viewRenderable;

    // Variables for spatial anchor
    private ArSceneView sceneView;

    private String userID;
    private Boolean finishTrack;
    private TrackRecord trackRecord;

    private String selectedReward;
    private String modelTitle;
    private String selectedSpinner;

    private HashMap<String, Integer> rewardCounts;

    // FeatureTable for round trip track
    ServiceFeatureTable trackFeatureTable;

    private String anchorID;
    private HashMap<String, String> anchorList;
    private HashMap<String, String> localAnchorList;


    private HashMap<String, LocalAnchor> localAnchors;


    private final ConcurrentHashMap<String, AnchorVisual> anchorVisuals = new ConcurrentHashMap<>();

    private AzureSpatialAnchorsManager cloudAnchorManager;
    private ARStep currentARStep;

    // Set AR mode as placing if no local anchors exist
    private String ARMode = "placing";

    private boolean enoughDataForSaving;
    private final Object progressLock = new Object();
    private int saveCount = 0;


    // UI Elements
    private Button actionButton;
    private TextView scanProgressText;
    private TextView statusText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_view);

        // Initialize references to GUI elements.
        layoutBack = (LinearLayout) findViewById(R.id.backFromARView);

        // Get information about the Intent which started this Activity.
        Intent intent = getIntent();
        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            userID = extras.getString("userID");
            finishTrack = extras.getBoolean("finishTrack");
            if (finishTrack) {
                trackRecord = (TrackRecord) extras.getSerializable("trackRecord");
            }
        }

        anchorList = new HashMap<>();
        localAnchorList = new HashMap<>();

        localAnchors = new HashMap<>();

        rewardCounts = new HashMap<String, Integer>();

        trackFeatureTable = new ServiceFeatureTable(getString(R.string.url_track));

        // Define what the back will do on a click.
        layoutBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent back = new Intent(ARViewActivity.this, MainActivity.class);
                back.putExtra("userID", userID);
                back.putExtra("finishTrack", finishTrack);
                if (finishTrack) {
                    back.putExtra("trackRecord", trackRecord);
                }
                back.setAction("fromARView");
                startActivity(back);

            }
        });

        // Get the rewards that the rewards that the user has earned before
        getAllRewards();


        readAnchorsFromLocal(this);

        popup = new Dialog(this);
        popup.setContentView(R.layout.popup_ar);
        popupText = (TextView) popup.findViewById(R.id.popup_text);
        popupSubText = (TextView) popup.findViewById(R.id.popup_subtext);
        popupBtn = (Button) popup.findViewById(R.id.popup_btn);

        popupBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                popup.dismiss();
            }
        });
        popupBtn.setText(R.string.okBtn);

        if (ARMode.equals("placing")) {
            popupText.setText(R.string.have_not_placed_before);
            popupSubText.setText(R.string.have_not_placed_instruction);
        } else {
            popupText.setText(R.string.have_placed_before);
            popupSubText.setText(R.string.have_placed_instruction);
        }

        popup.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));



        // Enable AR-related functionality on ARCore supported devices only.
        checkARCoreSupported();

        if (ARMode.equals("locating")) {
            currentARStep = ARStep.LookForAnchor;
        } else {
            currentARStep = ARStep.Start;
        }


        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);
        arFragment.setOnTapArPlaneListener(this::onTapArPlaneListener);

        sceneView = arFragment.getArSceneView();

        Scene scene = sceneView.getScene();
        scene.addOnUpdateListener(frameTime -> {
            if (cloudAnchorManager != null) {
                // Pass frames to Spatial Anchors for processing.
                cloudAnchorManager.update(sceneView.getArFrame());
            }
        });

        statusText = findViewById(R.id.statusText);
        scanProgressText = findViewById(R.id.scanProgressText);
        actionButton = findViewById(R.id.actionButton);
        actionButton.setOnClickListener((View v) -> ARStepAction());

    }


    @Override
    protected void onResume() {
        super.onResume();


        // ARCore requires camera permission to operate.
        if (!(ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
                == PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                    this, new String[] {CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
            //return;
        }

        // Ensure that Google Play Services for AR and ARCore device profile data are
        // installed and up to date.
        try {
            switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                case INSTALL_REQUESTED:
                    // When this method returns `INSTALL_REQUESTED`:
                    // 1. ARCore pauses this activity.
                    // 2. ARCore prompts the user to install or update Google Play
                    //    Services for AR (market://details?id=com.google.ar.core).
                    // 3. ARCore downloads the latest device profile data.
                    // 4. ARCore resumes this activity. The next invocation of
                    //    requestInstall() will either return `INSTALLED` or throw an
                    //    exception if the installation or update did not succeed.
                    mUserRequestedInstall = false;
                    return;
            }
        }
        catch (UnavailableUserDeclinedInstallationException | UnavailableDeviceNotCompatibleException e) {
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this, "Exception creating session: " + e, Toast.LENGTH_LONG)
                    .show();
            return;
        }

//        if (!SceneformHelper.hasCameraPermission(this)) {
//            return;
//        }

        if (sceneView != null && sceneView.getSession() == null) {
            if (!SceneformHelper.trySetupSessionForSceneView(this, sceneView)) {

                finish();
                return;
            }
        }

        if ((AzureSpatialAnchorsManager.SpatialAnchorsAccountId == null || AzureSpatialAnchorsManager.SpatialAnchorsAccountId.equals("Set me"))
                || (AzureSpatialAnchorsManager.SpatialAnchorsAccountKey == null || AzureSpatialAnchorsManager.SpatialAnchorsAccountKey.equals("Set me"))
                || (AzureSpatialAnchorsManager.SpatialAnchorsAccountDomain == null || AzureSpatialAnchorsManager.SpatialAnchorsAccountDomain.equals("Set me"))) {
            Toast.makeText(this, "\"Set SpatialAnchorsAccountId, SpatialAnchorsAccountKey, and SpatialAnchorsAccountDomain in AzureSpatialAnchorsManager.java\"", Toast.LENGTH_LONG)
                    .show();

            finish();
        }

        popup.show();

        if (currentARStep == ARStep.Start) {
            startAR();
        } else if (currentARStep == ARStep.LookForAnchor) {

            // We need to restart the session to find anchors we created.
            startNewSession();

            AnchorLocateCriteria criteria = new AnchorLocateCriteria();
            criteria.setIdentifiers(new String[]{anchorID});

            // Cannot run more than one watcher concurrently
            stopWatcher();

            cloudAnchorManager.startLocating(criteria);

            runOnUiThread(() -> {
                actionButton.setVisibility(View.INVISIBLE);
                rewardSpinner.setVisibility(View.INVISIBLE);
                statusText.setText(R.string.look_for_anchor);
            });
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (results[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.toast_camera_permission), Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    void checkARCoreSupported() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // Continue to query availability at 5Hz while compatibility is checked in the background.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkARCoreSupported();
                }
            }, 200);
        }
    }


    /**
     * A function to load the 3D models and create title next to the model
     * For local 3D models, only .glb or .gltf (2.0) can be loaded
     */
    public void loadModels(String selectedReward) {
        WeakReference<ARViewActivity> weakActivity = new WeakReference<>(this);
        String modelPath;
        if (selectedReward.equals("Ice Cream")) {
            modelPath = "models/Ice_Cream.gltf";
        } else if (selectedReward.equals("Watermelon")) {
            modelPath = "models/watermelon.gltf";
        } else {
            modelPath = "models/" + selectedReward + ".gltf";
        }

        // Load 3d model
        ModelRenderable.builder()
                .setSource(this, Uri.parse(modelPath))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(model -> {
                    ARViewActivity activity = weakActivity.get();
                    if (activity != null) {
                        if (ARMode.equals("locating")) {
                            localAnchors.get(selectedReward).setModel(model);
                        } else {
                            activity.model = model;
                        }
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });

        // Create title next the model
        ViewRenderable.builder()
                .setView(this, R.layout.view_model_card)
                .build()
                .thenAccept(viewRenderable -> {
                    ARViewActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.viewRenderable = viewRenderable;
                        TextView titleText = viewRenderable.getView().findViewById(R.id.titleText);

                        if (ARMode.equals("locating")) {
                            localAnchors.get(selectedReward).setModelTitleView(viewRenderable);
                            titleText.setText(localAnchors.get(selectedReward).getModelTitle());
                        } else {
                            titleText.setText(modelTitle);
                        }

                    }

                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });
    }


    /**
     * A function to query all rewards and its counts, and then set the spinner
     */
    public void getAllRewards() {

        String[] reward_names = {"Peach", "Watermelon", "Ice Cream", "Banana", "Apple"};
        for (String reward : reward_names) {
            int count = queryRewardCount(userID, reward);
            if (count != 0) {
                rewardCounts.put(reward, count);
            }

        }

        ArrayList<String> rewards;
        rewards = new ArrayList<>();
        rewards.add("Select a reward: ");

        for (String i : rewardCounts.keySet()) {
            String reward = i + ": " + rewardCounts.get(i);
            rewards.add(reward);
        }

        rewardSpinner = (Spinner) findViewById(R.id.spinnerReward);
        rewardAdapter = new ArrayAdapter<String>(ARViewActivity.this,
                android.R.layout.simple_list_item_1, android.R.id.text1, rewards);
        rewardAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rewardSpinner.setAdapter(rewardAdapter);

        rewardSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSpinner = rewards.get(position);
                if (position != 0) {
                    modelTitle = rewards.get(position);
                    selectedReward = modelTitle.split(":")[0];
                    loadModels(selectedReward);

                }
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

    }

    /**
     * We use this method to query counts of a reward from ArcGIS server.
     *
     * @param userID    User'ID.
     * @param reward    The name of the reward.
     */
    public int queryRewardCount(String userID, String reward) {
        if (userID.equals("guest")) {
            Toast.makeText(this, "Guest has no rewards! Please login as a normal user.",
                    Toast.LENGTH_LONG).show();
            return 0;
        }
        int count = 0;
        QueryParameters query = new QueryParameters();
        query.setWhereClause("user_id = " + userID + "and reward = '" + reward + "'");
        final ListenableFuture<Long> countAsync = trackFeatureTable.queryFeatureCountAsync(query);
        try {
            long result = countAsync.get();
            count = (int) result;
        } catch (Exception e) {
            String error = getString(R.string.fail_search) + e.getMessage();
            Toast.makeText(ARViewActivity.this, error, Toast.LENGTH_LONG).show();
            Log.e(TAG, error);
        }

        return count;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroySession();
    }

    /**
     * We use this function during the the whole process of uploading spatial anchor. When the action
     * button is clicked, this function will be triggered differently based on current step.
     */
    private void ARStepAction() {
        switch (currentARStep) {
            case SaveCloudAnchor:
                AnchorVisual visual = anchorVisuals.get("");
                if (visual == null) {
                    return;
                }

                if (!enoughDataForSaving) {
                    return;
                }


                setupLocalCloudAnchor(visual);

                cloudAnchorManager.createAnchorAsync(visual.getCloudAnchor())
                        .thenAccept(this::anchorSaveSuccess)
                        .exceptionally(thrown -> {
                            thrown.printStackTrace();
                            String exceptionMessage = thrown.toString();
                            Throwable t = thrown.getCause();
                            if (t instanceof CloudSpatialException) {
                                exceptionMessage = (((CloudSpatialException) t).getErrorCode().toString());
                            }

                            anchorSaveFailed(exceptionMessage);
                            return null;
                        });

                synchronized (progressLock) {
                    runOnUiThread(() -> {
                        scanProgressText.setVisibility(View.GONE);
                        scanProgressText.setText("");
                        actionButton.setVisibility(View.INVISIBLE);
                        statusText.setText(R.string.save_anchor);
                    });
                    currentARStep = ARStep.SavingCloudAnchor;
                }

                break;

            case LookForAnchor:
                // We need to restart the session to find anchors we created.
                startNewSession();

                AnchorLocateCriteria criteria = new AnchorLocateCriteria();
                criteria.setIdentifiers(new String[]{anchorID});

                // Cannot run more than one watcher concurrently
                stopWatcher();

                cloudAnchorManager.startLocating(criteria);

                runOnUiThread(() -> {
                    actionButton.setVisibility(View.INVISIBLE);
                    rewardSpinner.setVisibility(View.INVISIBLE);
                    statusText.setText(R.string.look_for_anchor);
                });

                break;

            case LookForNearbyAnchors:
                if (anchorVisuals.isEmpty() || !anchorVisuals.containsKey(anchorID)) {
                    runOnUiThread(() -> statusText.setText(R.string.fail_locate_nearby));

                    break;
                }

                AnchorLocateCriteria nearbyLocateCriteria = new AnchorLocateCriteria();
                NearAnchorCriteria nearAnchorCriteria = new NearAnchorCriteria();
                nearAnchorCriteria.setDistanceInMeters(10);
                nearAnchorCriteria.setSourceAnchor(anchorVisuals.get(anchorID).getCloudAnchor());
                nearbyLocateCriteria.setNearAnchor(nearAnchorCriteria);
                // Cannot run more than one watcher concurrently
                stopWatcher();
                cloudAnchorManager.startLocating(nearbyLocateCriteria);
                runOnUiThread(() -> {
                    actionButton.setVisibility(View.INVISIBLE);
                    statusText.setText(R.string.locating_nearby);
                });

                break;

            case End:
                for (AnchorVisual toDeleteVisual : anchorVisuals.values()) {
                    cloudAnchorManager.deleteAnchorAsync(toDeleteVisual.getCloudAnchor());
                }

                destroySession();

                deleteLocalAnchorsFile(this);

                runOnUiThread(() -> {
                    actionButton.setText(R.string.restart);
                    statusText.setText("");
//                    backButton.setVisibility(View.VISIBLE);
                });

                currentARStep = ARStep.Restart;

                break;

            case Restart:
                startAR();
                break;
        }
    }

    /**
     * This function is used to continue to next step after uploading each anchor.
     */
    private void anchorSaveSuccess(CloudSpatialAnchor result) {
        saveCount++;

        anchorID = result.getIdentifier();
        Log.d("ASADemo:", "created anchor: " + anchorID);
        anchorList.put(anchorID, modelTitle);
        saveAnchorToLocal(this,anchorID,modelTitle);

        AnchorVisual visual = anchorVisuals.get("");
        anchorVisuals.put(anchorID, visual);
        anchorVisuals.remove("");

        if (saveCount == rewardCounts.size()) {
            runOnUiThread(() -> {
                statusText.setText("you have placed all the rewards");
            });

        } else {
            // Need to create more anchors
            runOnUiThread(() -> {
                statusText.setText(R.string.create_next_anchor);
                actionButton.setVisibility(View.INVISIBLE);
            });

            currentARStep = ARStep.CreateLocalAnchor;
        }
    }

    /**
     * This function is used to show error message if we failed to upload the anchor.
     */
    private void anchorSaveFailed(String message) {
        runOnUiThread(() -> statusText.setText(message));
        AnchorVisual visual = anchorVisuals.get("");
    }

    /**
     * This function is used to clear all placed rewards.
     */
    private void clearVisuals() {
        for (AnchorVisual visual : anchorVisuals.values()) {
            visual.destroy();
        }

        anchorVisuals.clear();
    }

    /**
     * This function is used to render the model and it's title after user tapping on the screen.
     *
     * @param hitResult
     */
    private Anchor createAnchor(HitResult hitResult) {
        AnchorVisual visual = new AnchorVisual(arFragment, hitResult.createAnchor());

        visual.render(arFragment, model, viewRenderable, selectedReward);
        anchorVisuals.put("", visual);

        runOnUiThread(() -> {
            scanProgressText.setVisibility(View.VISIBLE);
            if (enoughDataForSaving) {
                statusText.setText(R.string.ready_save);
                actionButton.setText(R.string.save_reward);
                actionButton.setVisibility(View.VISIBLE);
            } else {
                statusText.setText(R.string.move_around);
            }
        });

        currentARStep = ARStep.SaveCloudAnchor;

        return visual.getLocalAnchor();
    }

    /**
     * This function is used to remove cloud anchor manager and all placed rewards.
     */
    private void destroySession() {
        if (cloudAnchorManager != null) {
            cloudAnchorManager.stop();
            cloudAnchorManager = null;
        }

        clearVisuals();
    }

    /**
     * This function will be triggered if an anchor is found.
     *
     * @param event
     */
    private void onAnchorLocated(AnchorLocatedEvent event) {
        LocateAnchorStatus status = event.getStatus();

        runOnUiThread(() -> {
            switch (status) {
                case AlreadyTracked:
                    Log.d("FileLog", "NOLocated!!!2!!!!!!!!!!!");
                    break;

                case Located:
                    Log.d("FileLog", "Located!!!!!!!!!!!!!!!!");
                    renderLocatedAnchor(event.getAnchor());
                    break;

                case NotLocatedAnchorDoesNotExist:
                    Log.d("FileLog", "1111111!!!!!!!!!!!!");
                    statusText.setText(R.string.fail_locate_anchor);
                    break;
            }
        });
    }

    /**
     * This function will be triggered if an anchor is located completed.
     *
     * @param event
     */
    private void onLocateAnchorsCompleted(LocateAnchorsCompletedEvent event) {

        if (currentARStep == ARStep.LookForAnchor) {
            runOnUiThread(() -> {
                statusText.setText(R.string.succeed_locate_first_anchor);
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText(R.string.look_for_neary_anchor);
            });
            currentARStep = ARStep.LookForNearbyAnchors;
        } else {
            stopWatcher();
            runOnUiThread(() -> {
                statusText.setText(R.string.succeed_locate_all_anchor);
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText(R.string.clear_rewards);
            });
            currentARStep = ARStep.End;
        }
    }

    /**
     * This function is used to monitor scan progress.
     *
     * @param args
     */
    private void onSessionUpdated(SessionUpdatedEvent args) {
        float progress = args.getStatus().getRecommendedForCreateProgress();
        enoughDataForSaving = progress >= 1.0;
        synchronized (progressLock) {
            if (currentARStep == ARStep.SaveCloudAnchor) {
                DecimalFormat decimalFormat = new DecimalFormat("00");
                runOnUiThread(() -> {
                    String progressMessage = "Scan progress is " + decimalFormat.format(Math.min(1.0f, progress) * 100) + "%";
                    scanProgressText.setText(progressMessage);
                });

                if (enoughDataForSaving && actionButton.getVisibility() != View.VISIBLE) {
                    // Enable the save button
                    runOnUiThread(() -> {
                        statusText.setText(R.string.ready_save);
                        actionButton.setText(R.string.save_reward);
                        actionButton.setVisibility(View.VISIBLE);
                    });
                    currentARStep = ARStep.SaveCloudAnchor;
                }
            }
        }
    }


    /**
     * This function is used to handling the tap on screen. Each model can only be placed once.
     *
     * @param hitResult
     * @param plane
     * @param motionEvent
     */
    private void onTapArPlaneListener(HitResult hitResult, Plane plane, MotionEvent motionEvent) {

        if (currentARStep == ARStep.CreateLocalAnchor) {
            if (selectedSpinner.equals("Select a reward: ")) {
                Toast.makeText(ARViewActivity.this, "Select a reward!", Toast.LENGTH_LONG).show();
            } else if (anchorList.containsValue(modelTitle)) {
                Toast.makeText(ARViewActivity.this, "Already placed this reward," +
                        " select another!", Toast.LENGTH_LONG).show();
            } else {
                createAnchor(hitResult);
            }

        }
    }

    /**
     * This function is used to render the located anchor. It will render corresponding model on the
     * anchor according to it's ID.
     *
     * @param anchor
     */
    private void renderLocatedAnchor(CloudSpatialAnchor anchor) {
        AnchorVisual foundVisual = new AnchorVisual(arFragment, anchor.getLocalAnchor());
        foundVisual.setCloudAnchor(anchor);
        foundVisual.getAnchorNode().setParent(arFragment.getArSceneView().getScene());
        String cloudAnchorIdentifier = foundVisual.getCloudAnchor().getIdentifier();

        modelTitle = localAnchorList.get(cloudAnchorIdentifier);
        selectedReward = modelTitle.split(":")[0];

        foundVisual.render(arFragment, localAnchors.get(selectedReward).getModel(),
                localAnchors.get(selectedReward).getModelTitleView(), selectedReward);

        anchorVisuals.put(cloudAnchorIdentifier, foundVisual);
    }

    /**
     * This function is used set up local cloud anchor.
     *
     * @param visual
     */
    private void setupLocalCloudAnchor(AnchorVisual visual) {
        CloudSpatialAnchor cloudAnchor = new CloudSpatialAnchor();
        cloudAnchor.setLocalAnchor(visual.getLocalAnchor());
        visual.setCloudAnchor(cloudAnchor);
    }

    /**
     * This function is used start from scratch.
     */
    private void startAR() {
        saveCount = 0;
        ARMode = "placing";
        startNewSession();
        runOnUiThread(() -> {
            scanProgressText.setVisibility(View.GONE);
            rewardSpinner.setVisibility(View.VISIBLE);
            statusText.setText(R.string.create_anchor);
            actionButton.setVisibility(View.INVISIBLE);
        });
        currentARStep = ARStep.CreateLocalAnchor;
    }

    /**
     * This function is used start a new AR session.
     */
    private void startNewSession() {
        destroySession();

        cloudAnchorManager = new AzureSpatialAnchorsManager(sceneView.getSession());
        cloudAnchorManager.addAnchorLocatedListener(this::onAnchorLocated);
        cloudAnchorManager.addLocateAnchorsCompletedListener(this::onLocateAnchorsCompleted);
        cloudAnchorManager.addSessionUpdatedListener(this::onSessionUpdated);
        cloudAnchorManager.start();
    }

    /**
     * This function is used stop the watcher of the cloud anchor manager.
     */
    private void stopWatcher() {
        if (cloudAnchorManager != null) {
            cloudAnchorManager.stopLocating();
        }
    }

    enum ARStep {
        Start,                          ///< the start of the process
        CreateLocalAnchor,      ///< the session will create a local anchor
        SaveCloudAnchor,        ///< the session will save the cloud anchor
        SavingCloudAnchor,      ///< the session is in the process of saving the cloud anchor
        LookForAnchor,          ///< the session will run the query
        LookForNearbyAnchors,   ///< the session will run a query for nearby anchors
        End,                            ///< the end of the process
        Restart,                        ///< waiting to restart
    }

    /**
     * This function is used to save each anchor ID to local csv file.
     * @param storedContext
     * @param anchorID
     * @param modelTitle
     */
    public void saveAnchorToLocal(Context storedContext, String anchorID, String modelTitle) {
        // Saving anchor IDs and model's title to a CSV file.
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            try {
                Log.d("FileLog", "start writing track");

                File file = new File(storedContext.getExternalFilesDir(null), "anchorList.csv");
                FileOutputStream outputStream = new FileOutputStream(file, true);
                PrintWriter writer = new PrintWriter(outputStream);

                // write values
//                for (String key : anchorList.keySet()) {
//                    writer.print(key + ",");
//                    writer.println(anchorList.get(key));
//                }
                writer.print(anchorID + ",");
                writer.println(modelTitle);

                writer.close();
                outputStream.close();
                Log.d("FileLog", "File Saved :  " + file.getPath());
            } catch (IOException e) {
                Log.e("FileLog", "File to write file");
            }
        } else {
            Log.e("FileLog", "SD card not mounted");
        }
    }


    /**
     * This function is used to determine between placing and locating mode, and create local anchors.
     * @param storedContext
     */
    public void readAnchorsFromLocal(Context storedContext) {
        // Reading anchor IDs and model's title from a CSV file.
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File file = new File(storedContext.getExternalFilesDir(null), "anchorList.csv");

            if (file.exists()) {
                ARMode = "locating";
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(file));

                    String line = "";
//                    int row = 0;
                    while ((line = reader.readLine()) != null) {
                        // Skip the head row
//                        if (row == 0) {
//                            ++row;
//                            continue;
//                        }
                        // Split the line into different parts (using the "," as a separator).
                        String[] parse = line.split(",");

                        Log.d("FileLog", "load anchors: parsed " + parse[0] + "," +
                                parse[1]);
                        localAnchorList.put(parse[0], parse[1]);
                        anchorID = parse[0];
                        modelTitle = parse[1];

                        LocalAnchor localAnchor = new LocalAnchor();
                        localAnchor.setAnchorID(parse[0]);
                        localAnchor.setModelTitle(modelTitle);
                        localAnchor.setModelName(modelTitle.split(":")[0]);

                        loadModels(localAnchor.getModelName());
                        localAnchors.put(localAnchor.getModelName(), localAnchor);
//                        ++row;
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                Log.d("FileLog", "Anchor loaded from:  " + file.getPath());
            } else {
                Log.d("FileLog", "Anchor not found:  " + file.getPath());
            }

        } else {
            Log.e("FileLog", "SD card not mounted");
        }
    }

    /**
     * This function is used to delete the local anchor IDs file if users would like to re-locating.
     * @param storedContext
     */
    public void deleteLocalAnchorsFile(Context storedContext) {

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {

            File file = new File(storedContext.getExternalFilesDir(null), "anchorList.csv");
            if (file.exists()) {
                file.delete();
                Log.d("FileLog", "File deleted :  " + file.getPath());
            }

        } else {
            Log.e("FileLog", "SD card not mounted");
        }
    }
}