package mobilegis.ikg.ethz.lbsfitnessapp;

import android.location.Location;


/**
 * This class is geofence which centered on a point with a radius.
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */
public class GeoFence {
    private double latitude;
    private double longitude;
    private double radius;

    private String name;

    /**
     * Creates a new Geofence.
     *
     * @param name The name of this geofence.
     * @param latitude The latitude.
     * @param longitude The longitude.
     * @param radius The radius.
     */
    public GeoFence(String name, double latitude, double longitude, double radius) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }

    /**
     * Transforms this Geofence into an Android location.
     *
     * @return A location containing latitude and longitude of this Geofence.
     */
    public Location getLocation() {
        Location target = new Location("");
        target.setLatitude(latitude);
        target.setLongitude(longitude);
        return target;
    }

    /**
     * Determine whether the user is entering the geofence
     *
     * @param currentDistanceToCenter distance between current location and center
     * @param lastDistanceToCenter distance between last location and center
     * @return whether the user is entering the geofence
     */
    public Boolean isEntering(double currentDistanceToCenter, double lastDistanceToCenter) {

        if (currentDistanceToCenter < this.radius && lastDistanceToCenter > this.radius) {
            return true;
        }
        return false;
    }

    /**
     * Determine whether the user is leaving the geofence
     *
     * @param currentDistanceToCenter distance between current location and center
     * @param lastDistanceToCenter distance between last location and center
     * @return whether the user is leaving the geofence
     */
    public Boolean isLeaving(double currentDistanceToCenter, double lastDistanceToCenter) {
        if (currentDistanceToCenter > this.radius && lastDistanceToCenter < this.radius) {
            return true;
        }
        return false;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getRadius() {
        return radius;
    }

    public String getName() {
        return name;
    }
}
