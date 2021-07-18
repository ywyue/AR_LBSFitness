package mobilegis.ikg.ethz.lbsfitnessapp;

import android.os.Handler;
import android.os.Looper;

/**
 * This class is MainThreadContext
 * Note: this class is adopted from azure-spatial-anchors-sample provided by Microsoft.
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */
class MainThreadContext {
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Looper mainLooper = Looper.getMainLooper();

    public static void runOnUiThread(Runnable runnable){
        if (mainLooper.isCurrentThread()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
