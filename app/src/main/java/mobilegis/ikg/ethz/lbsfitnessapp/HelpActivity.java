package mobilegis.ikg.ethz.lbsfitnessapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * This class is HelpActivity which is used to show user manual.
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */
public class HelpActivity extends AppCompatActivity {

    private static final String TAG = "HelpActivity";

    // References to GUI elements.
    private LinearLayout layoutBack;
    private TextView helpTitle;
    private TextView helpText;

    private String userID;
    private Boolean finishTrack;
    private TrackRecord trackRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        // Initialize references to GUI elements.
        layoutBack = (LinearLayout) findViewById(R.id.backFromHelp);
        helpTitle = (TextView) findViewById(R.id.helpTitle);
        helpText = (TextView) findViewById(R.id.helpText);

        helpTitle.setText(R.string.helpTitle);
        helpText.setText(R.string.helpText);

        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            userID = extras.getString("userID");
            finishTrack = extras.getBoolean("finishTrack");
            if (finishTrack) {
                trackRecord = (TrackRecord) extras.getSerializable("trackRecord");
            }

        }

        // Define what the back button will do on a click.
        layoutBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent back = new Intent(HelpActivity.this, MainActivity.class);
                back.putExtra("userID", userID);
                back.putExtra("finishTrack", finishTrack);
                if (finishTrack) {
                    back.putExtra("trackRecord", trackRecord);
                }
                back.setAction("fromHelp");
                startActivity(back);
            }
        });
    }
}