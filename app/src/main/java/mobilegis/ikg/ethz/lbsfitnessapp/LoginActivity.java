package mobilegis.ikg.ethz.lbsfitnessapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * This activity serves as an "entry point".
 *      Show welcome page;
 *      Let user login;
 *      Let guest user login.
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */
public class LoginActivity extends AppCompatActivity {

    // References to GUI elements.
    private Button btnSignIn;
    private Button btnSignGuest;
    private EditText etxtUserID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize references to GUI elements.
        btnSignIn = (Button) findViewById(R.id.signInBtn);
        btnSignGuest = (Button) findViewById(R.id.signGuestBtn);
        etxtUserID = (EditText) findViewById(R.id.inputUserID);

        btnSignIn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String userID = etxtUserID.getText().toString();
                triggerBtnSignInAction(userID);
            }
        });
        btnSignGuest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String userID = "guest";
                triggerBtnSignInAction(userID);
            }
        });

    }

    /**
     * This method sends userID to MainActivity using an intent.
     */
    private void triggerBtnSignInAction(String userID) {
        // We wrap everything in a try / catch block, in case someone forgets to enter
        // a number in any of the EditTexts.
        try {
            if (userID.isEmpty() || userID.equals("null")) {
                Toast.makeText(this, R.string.empty_user_id, Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("userID", userID);
                startActivity(intent);
            }

        } catch (Exception e) {
            Toast.makeText(this, R.string.intent_error, Toast.LENGTH_SHORT).show();
        }
    }
}