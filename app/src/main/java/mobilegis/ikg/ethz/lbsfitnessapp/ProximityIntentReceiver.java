package mobilegis.ikg.ethz.lbsfitnessapp;

import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.LocationManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

/**
 * This is an external BroadcastReceiver, which runs independently of our MainActivity. We here
 * show how to create notifications in the Android Bar (on top of the Android screen).
 *
 * This is of course useful if our app is not started, or running in the back, and shows how we
 * can access location outside of a running Activity.
 */
public class ProximityIntentReceiver extends BroadcastReceiver {

    Dialog popup;
    ImageView img;

    TextView popupTextTitle;
    TextView popupSubText;
    TextView popupMiddleLeftText;
    TextView popupMiddleRightText;
    Button btn;


    @Override
    public void onReceive(Context context, Intent intent) {
        String name = intent.getStringExtra("name");
        String award = intent.getStringExtra("award");
        Boolean entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false);
        popup = new Dialog(context);

        // Show dialog when user is entering the target location or starting location
        if (entering) {

            popup.setContentView(R.layout.popup);
            img= (ImageView) popup.findViewById(R.id.popup_img);
            btn = (Button) popup.findViewById(R.id.popup_btn);
            popupTextTitle = (TextView) popup.findViewById(R.id.popup_text1);
            popupMiddleLeftText = (TextView) popup.findViewById(R.id.popup_text2);
            popupMiddleRightText = (TextView) popup.findViewById(R.id.popup_text3);
            popupSubText = (TextView) popup.findViewById(R.id.popup_text4);

            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    popup.dismiss();
                }
            });
            btn.setText(R.string.okBtn);

            if (name.equals("start location")){

                popupMiddleRightText.setText(award);

                switch(award) {
                    case "Peach":
                        img.setImageResource(R.drawable.peach);
                        break;
                    case "Watermelon":
                        img.setImageResource(R.drawable.watermelon);
                        break;
                    case "Ice Cream":
                        img.setImageResource(R.drawable.ice_cream);
                        break;
                    case "Banana":
                        img.setImageResource(R.drawable.banana);
                        break;
                    default:
                        img.setImageResource(R.drawable.apple);
                        break;
                }

            } else {

                img.setImageResource(R.drawable.running_back);
                popupTextTitle.setText(R.string.return_title);
                popupMiddleLeftText.setText(R.string.return_midleft);
                popupMiddleRightText.setText(name);
                popupSubText.setText(R.string.return_sub);

            }
            popup.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.show();



            Log.d(getClass().getSimpleName(), "Entering Geofence " + name);
            sendNotification(context, "You are approaching " + name);

        } else {

            Log.d(getClass().getSimpleName(), "Exiting Geofence " + name);
            sendNotification(context, "You are leaving " + name);

        }
    }


    /**
     * Posts a notification in the notification bar when a transition is detected.
     * If the user clicks the notification, control goes to the MainActivity.
     *
     * See https://developer.android.com/training/location/geofencing.html for more details.
     */
    private void sendNotification(Context context, String notificationDetails) {
        // Create an explicit content Intent that starts the main Activity.
        Intent notificationIntent = new Intent(context, MainActivity.class);

        // Construct a task stack.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

        // Add the main Activity to the task stack as the parent.
        stackBuilder.addParentStack(MainActivity.class);

        // Push the content Intent onto the stack.
        stackBuilder.addNextIntent(notificationIntent);

        // Get a PendingIntent containing the entire back stack.
        PendingIntent notificationPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        // Get a notification builder that's compatible with platform versions >= 4
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

        // Define the notification settings.
        builder.setSmallIcon(R.drawable.ic_launcher)
                // In a real app, you may want to use a library like Volley
                // to decode the Bitmap.
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                        R.drawable.ic_launcher))
                .setColor(Color.RED)
                .setContentTitle(notificationDetails)
                .setContentText(context.getString(R.string.proximity_text))
                .setContentIntent(notificationPendingIntent);

        // Dismiss notification once the user touches it.
        builder.setAutoCancel(true);

        // Get an instance of the Notification manager
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Issue the notification
        mNotificationManager.notify(0, builder.build());
    }

}
