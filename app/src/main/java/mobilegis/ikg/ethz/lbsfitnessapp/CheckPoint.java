package mobilegis.ikg.ethz.lbsfitnessapp;

import android.location.Location;

/**
 * This class is check points.
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */
public class CheckPoint {

    private String name;
    private double longitude;
    private double latitude;

    public CheckPoint(String name, double longitude, double latitude) {
        this.name = name;
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public Location getLocation() {
        Location checkPoint = new Location("");
        checkPoint.setLongitude(longitude);
        checkPoint.setLatitude(latitude);
        return checkPoint;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
}
