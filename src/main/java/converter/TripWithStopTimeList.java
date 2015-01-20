package converter;

import java.util.ArrayList;

import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;

/**
 * 
 * @author linzhiqi
 * 
 */
public class TripWithStopTimeList implements Comparable<TripWithStopTimeList> {
	@Override
	public String toString() {
		return "TripWithStopTimeList [trip=" + trip + ", startTime="
				+ startTime + ", endTime=" + endTime + "]";
	}

	private Trip trip;
	private int startTime;
	private int endTime;
	private ArrayList<StopTime> stopTimeList;
	
	public TripWithStopTimeList() {
		
	}

	public TripWithStopTimeList(Trip trip, int startTime, int endTime,
			ArrayList<StopTime> stopTimeList) {
		this.trip = trip;
		this.startTime = startTime;
		this.endTime = endTime;
		this.setStopTimeList(stopTimeList);
	}

	public int compareTo(TripWithStopTimeList o) {
		return startTime - o.getStartTime();
	}

	public int getStartTime() {
		return startTime;
	}

	public int getEndTime() {
		return endTime;
	}

	public Trip getTrip() {
		return trip;
	}

	public void setTrip(Trip trip) {
		this.trip = trip;
	}
		
	public void setStartTime(int startTime) {
		this.startTime = startTime;
	}

	public void setEndTime(int endTime) {
		this.endTime = endTime;
	}

	public ArrayList<StopTime> getStopTimeList() {
		return stopTimeList;
	}

	public void setStopTimeList(ArrayList<StopTime> stopTimeList) {
		this.stopTimeList = stopTimeList;
	}
}
