package converter;

import java.util.HashSet;
import java.util.List;

import movement.schedule.VehicleSchedule;

import org.onebusaway.gtfs.model.Route;


/**
 * 
 * @author linzhiqi
 * 
 */
public class RouteWithTripList {
	private Route route;
	private List<TripWithStopTimeList> tripList;
	private HashSet<String> stopList;
	private List<VehicleSchedule> vehicleList;

	public RouteWithTripList(Route route, List<TripWithStopTimeList> tripList,
			HashSet<String> stopList) {
		this.route = route;
		this.tripList = tripList;
		this.stopList = stopList;
	}

	public Route getRoute() {
		return route;
	}

	public void setRoute(Route route) {
		this.route = route;
	}

	public List<TripWithStopTimeList> getTripList() {
		return tripList;
	}

	public void setTripList(List<TripWithStopTimeList> tripList) {
		this.tripList = tripList;
	}

	public HashSet<String> getStopList() {
		return stopList;
	}

	public void setStopList(HashSet<String> stopList) {
		this.stopList = stopList;
	}

	public List<VehicleSchedule> getVehicleList() {
		return vehicleList;
	}

	public void setVehicleList(List<VehicleSchedule> vehicleList) {
		this.vehicleList = vehicleList;
	}

}
