package mobilegis.ikg.ethz.lbsfitnessapp;

import android.content.Context;
import android.location.Location;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;



/**
 * This class is track record which contains track result
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */
public class TrackRecord implements Serializable {

    private Long startTime;
    private Long endTime;
    private transient Location startLocation;
    private transient Location checkPoint;
    private Double distance;
    private Long duration;
    private Double averageSpeed;
    private Double averageTemperature;
    private String reward;


    public TrackRecord(Long startTime, Location checkPoint) {
        this.startTime = startTime;
        this.checkPoint = checkPoint;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(Location startLocation) {
        this.startLocation = startLocation;
    }

    public Location getCheckPoint() {
        return checkPoint;
    }

    public void setCheckPoint(Location checkPoint) {
        this.checkPoint = checkPoint;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(List<Float> distanceArray) {
        double distance = 0;
        for (float d : distanceArray)
            distance += d;
        this.distance = distance * 0.001;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration() {
        this.duration = endTime - startTime;
    }

    public Double getAverageSpeed() {
        return averageSpeed;
    }

    public void setAverageSpeed() {

        this.setDuration();
        this.averageSpeed = distance / (duration * 0.001 / 3600);
    }

    public Double getAverageTemperature() {
        return averageTemperature;
    }

    public void setAverageTemperature(List<Float> temperatureArray) {
        double sumTem = 0;
        for (float t : temperatureArray)
            sumTem += t;
        this.averageTemperature = sumTem / temperatureArray.size();
    }

    public String getReward() {
        return reward;
    }

    public void setReward() {
        this.setAverageSpeed();

        if (averageSpeed >= 4 && averageSpeed < 6 && distance <= 1 && averageTemperature > 4 && averageTemperature < 20) {
            this.reward = "Peach";
        } else if (averageSpeed >= 4 && averageSpeed < 6 && distance > 1 && averageTemperature >= 20) {
            this.reward = "Watermelon";
        } else if (averageSpeed >= 6 && averageSpeed < 8 && distance > 0 && averageTemperature >= 20) {
            this.reward = "Ice Cream";
        } else if (averageSpeed >= 8 && distance > 1 && averageTemperature > 4 && averageTemperature < 20) {
            this.reward = "Banana";
        } else {
            this.reward = "Apple";
        }
    }

    public void saveResult(Context storedContext) {
        this.setReward();
        // Saving track result to a CSV file
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            try{
                Log.d("FileLog", "start writing track");

                File file = new File (storedContext.getExternalFilesDir(null), "output.csv");
                FileOutputStream outputStream = new FileOutputStream(file, true);
                PrintWriter writer = new PrintWriter(outputStream);

                // write head
                writer.print("starting timestamp (in milliseconds)" + ",");
                writer.print("longitude of starting point" + ",");
                writer.print("latitude of starting point" + ",");
                writer.print("longitude of checkpoint" + ",");
                writer.print("latitude of checkpoint" + ",");
                writer.print("distance" + ",");
                writer.print("duration" + ",");
                writer.print("average speed" + ",");
                writer.print("average temperature" + ",");
                writer.println("reward" + ",");

                // write values
                writer.print(startTime + ",");
                writer.print(startLocation.getLongitude() + ",");
                writer.print(startLocation.getLatitude() + ",");
                writer.print(checkPoint.getLongitude() + ",");
                writer.print(checkPoint.getLatitude() + ",");
                writer.print(distance + ",");
                writer.print(duration + ",");
                writer.print(averageSpeed + ",");
                writer.print(averageTemperature + ",");
                writer.println(reward);

                writer.close();
                outputStream.close();
                Log.d("FileLog", "File Saved :  " + file.getPath());
            }catch(IOException e){
                Log.e("FileLog", "File to write file");
            }
        }else{
            Log.e("FileLog", "SD card not mounted");
        }


    }
}
