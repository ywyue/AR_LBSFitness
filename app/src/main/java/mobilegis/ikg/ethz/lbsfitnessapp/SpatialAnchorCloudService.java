package mobilegis.ikg.ethz.lbsfitnessapp;

import android.app.Application;
import com.microsoft.CloudServices;

/**
 * This class is SpatialAnchorCloudService which is used to initialize CloudServices.
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */
public class SpatialAnchorCloudService extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Use application's context to initialize CloudServices!
        CloudServices.initialize(this);
    }
}
