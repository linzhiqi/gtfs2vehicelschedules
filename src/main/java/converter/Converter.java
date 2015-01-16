package converter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import movement.schedule.RouteSchedule;
import movement.schedule.StopDataUnit;
import movement.schedule.VehicleSchedule;

import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.serialization.GtfsReader;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import core.Coord;
import core.DTNHost;
import datastructure.RouteWithTripList;
import datastructure.TripWithStopTimeList;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * 
 * @author linzhiqi
 * 
 */
public class Converter {

	public static final int TRAM_TYPE = 0;
	public static final int METRO_TYPE = 1;
	public static final int RAIL_TYPE = 2;
	public static final int BUS_TYPE = 3;

	/**
	 * max speed of vehicles, used in isNextStrip() method. unit: meters per
	 * second
	 */
	public static double SPEED_MAX = 20;
	/**
	 * max distance between two continuous trips of rail way transport, used in
	 * isNextStrip() method. unit: meter
	 */
	public static double DISTANCE_MAX = 500;

	public static String OUTPUT_PATH = "vehicle_schedules.json";

	public enum Weekday {
		Mon, Tue, Wed, Thu, Fri, Sat, Sun
	};

	public static void main(String[] args) throws IOException {
		String usageStr = "usage: <-i gtfs_path> [-o output_path] [-s max_speed] [-d max_distance]";
		String inputPath = null;
		OptionParser parser = new OptionParser("i:o:s:d:h");
		OptionSet options = parser.parse(args);
		if (!options.has("i")) {
			System.out.print(usageStr);
			System.exit(-1);
		}
		inputPath = (String) options.valueOf("i");
		if (options.has("o")) {
			OUTPUT_PATH = (String) options.valueOf("o");
		}
		if (options.has("s")) {
			SPEED_MAX = Double.parseDouble((String) options.valueOf("s"));
		}
		if (options.has("d")) {
			DISTANCE_MAX = Double.parseDouble((String) options.valueOf("d"));
		}
		if (options.has("h")) {
			System.out.print(usageStr);
			System.exit(0);
		}

		GtfsReader reader = new GtfsReader();
		// the inputPath can be the path of decompressed folder or of the ZIP
		// file
		reader.setInputLocation(new File(inputPath));

		/**
		 * the internal entity store, which has references to all the loaded
		 * entities
		 */
		GtfsDaoImpl store = new GtfsDaoImpl();
		reader.setEntityStore(store);

		reader.run();

		// build a HashSet usefulTrips having refers to all the useful trips
		// build a HashMap of int id <-> Route object
		Collection<Route> allRoutes = store.getAllRoutes();
		Collection<Trip> allTrips = store.getAllTrips();
		Collection<ServiceCalendar> calendars = store.getAllCalendars();
		Map<AgencyAndId, ServiceCalendar> calendarMap = getCalendarMap(calendars);
		HashMap<Route, Integer> route2IntIdMap = new HashMap<Route, Integer>();
		HashSet<Trip> usefulTrips = new HashSet<Trip>();

		obtainUsefulTrips(allRoutes, allTrips, calendarMap, route2IntIdMap,
				usefulTrips);

		// remove StopTime objects belongs to useless trips
		Collection<StopTime> stopTimes = store.getAllStopTimes();
		removeUselessStopTime(stopTimes, usefulTrips);

		// arrange Route, Trip and StopTime elements in a top to bottom
		// hierarchy manner
		HashMap<Route, HashMap<Trip, ArrayList<StopTime>>> top2BottomStructure = new HashMap<Route, HashMap<Trip, ArrayList<StopTime>>>();
		obtainTop2BottomStructure(top2BottomStructure, stopTimes);

		// make up trips and their stop times for each service week day
		int numOfTripsAfterMadeUp = splitTrips4MultipleWeekDay(
				top2BottomStructure, calendarMap);

		// sort stop times in each trip
		// sort the trips of each route
		// obtain the stop ids of each route
		ArrayList<RouteWithTripList> routesWithTripList = new ArrayList<RouteWithTripList>();
		sortTripsAndGetStopList(top2BottomStructure, routesWithTripList);

		// create vehicles to consume trips for each route, and set them into
		// the corresponding RouteWithTripList object
		int numberOfVehicles = populateVehicleList(routesWithTripList,
				Converter.SPEED_MAX, Converter.DISTANCE_MAX);

		System.out.println("numVehicle/numTrips=" + numberOfVehicles + "/"
				+ numOfTripsAfterMadeUp);

		// build RouteSchedule objects
		ArrayList<RouteSchedule> routeSchedules = new ArrayList<RouteSchedule>();
		populateRouteScheduleList(routesWithTripList, route2IntIdMap,
				routeSchedules);

		// convert to JSON file
		writeToJSONFile(routeSchedules, OUTPUT_PATH);
	}

	public static void writeToJSONFile(ArrayList<RouteSchedule> routeSchedules,
			String filePath) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(
					new File(filePath), routeSchedules);
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * construct RouteSchedule objects and populate the list
	 * 
	 * @param routesWithTripList
	 * @param route2IntIdMap
	 * @param routeSchedules
	 *            the structure to populate
	 */
	public static void populateRouteScheduleList(
			ArrayList<RouteWithTripList> routesWithTripList,
			HashMap<Route, Integer> route2IntIdMap,
			ArrayList<RouteSchedule> routeSchedules) {
		for (RouteWithTripList route : routesWithTripList) {
			RouteSchedule routeSchedule = new RouteSchedule();
			routeSchedule.layer_id = getLayerId(route.getRoute().getType());
			routeSchedule.route_id = route2IntIdMap.get(route.getRoute());
			routeSchedule.stops = route.getStopList();
			routeSchedule.vehicles = route.getVehicleList();
			routeSchedules.add(routeSchedule);
		}
	}

	/**
	 * for each route, step1: create an empty vehicle object; step2:consume the
	 * first trip; step3:consume the next trip that is OK to be its next trip,
	 * and repeat until the end of the trip list; step4: do step1 again unless
	 * the trip list is exhausted. method isNextStrip() is the method decide if
	 * the trip is OK to be the next trip.
	 * 
	 * @param routesWithTripList
	 * @param maxSpeed
	 * @param maxDistance
	 * @return the number of VehicleSchedule objects created
	 */
	public static int populateVehicleList(
			ArrayList<RouteWithTripList> routesWithTripList, double maxSpeed,
			double maxDistance) {

		int numberOfVehicles = 0;
		for (RouteWithTripList route : routesWithTripList) {
			List<VehicleSchedule> vehicleScheduleList = new ArrayList<VehicleSchedule>();
			List<TripWithStopTimeList> tripList = route.getTripList();
			int transportType = route.getRoute().getType();
			while (!tripList.isEmpty()) {
				VehicleSchedule vehicle = new VehicleSchedule();
				vehicle.trips = new ArrayList<ArrayList<StopDataUnit>>();
				vehicle.vehicle_id = numberOfVehicles;
				Iterator<TripWithStopTimeList> it1 = tripList.iterator();
				int lastEndTime = 0;
				Coord lastEndLocation = null;
				while (it1.hasNext()) {
					TripWithStopTimeList trip = it1.next();
					ArrayList<StopTime> stopTimeList = trip.getStopTimeList();

					if (vehicle.trips.isEmpty()) {
						// the first trip for the vehicle
						ArrayList<StopDataUnit> stopDataUniteList = stopTimeList2DataUniteList(stopTimeList);
						vehicle.trips.add(stopDataUniteList);
						lastEndTime = trip.getEndTime();
						Stop lastEndStop = stopTimeList.get(
								stopTimeList.size() - 1).getStop();
						lastEndLocation = new Coord(lastEndStop.getLon(),
								lastEndStop.getLat());
						it1.remove();
						continue;
					} else {
						// if is not the first trip, check if it is valid to be
						// the next trip
						int thisStartTime = trip.getStartTime();
						Stop thisStartStop = stopTimeList.get(0).getStop();
						Coord thisStartLocation = new Coord(
								thisStartStop.getLon(), thisStartStop.getLat());
						if (isNextStrip(lastEndTime, lastEndLocation,
								thisStartTime, thisStartLocation, maxSpeed,
								maxDistance, transportType)) {
							ArrayList<StopDataUnit> stopDataUniteList = stopTimeList2DataUniteList(stopTimeList);
							vehicle.trips.add(stopDataUniteList);
							lastEndTime = trip.getEndTime();
							Stop lastEndStop = stopTimeList.get(
									stopTimeList.size() - 1).getStop();
							lastEndLocation = new Coord(lastEndStop.getLat(),
									lastEndStop.getLon());
							it1.remove();
							continue;
						} else {
							continue;
						}
					}
				}
				vehicleScheduleList.add(vehicle);
				numberOfVehicles++;
			}
			route.setVehicleList(vehicleScheduleList);
		}
		return numberOfVehicles;
	}

	/**
	 * This method sort StopTime objects of each Trip object, and sort Trip
	 * objects of each Route object. Sorting is based on the sequence id of
	 * StopTime objects, and start time of Trip objects. It also populate the
	 * stop list of each route
	 * 
	 * @param top2BottomStructure
	 * @param routesWithTripList
	 */
	public static void sortTripsAndGetStopList(
			HashMap<Route, HashMap<Trip, ArrayList<StopTime>>> top2BottomStructure,
			ArrayList<RouteWithTripList> routesWithTripList) {
		for (HashMap<Trip, ArrayList<StopTime>> trips : top2BottomStructure
				.values()) {
			// create route element, and add it to the List
			Iterator<Entry<Trip, ArrayList<StopTime>>> it1 = trips.entrySet()
					.iterator();
			Entry<Trip, ArrayList<StopTime>> entry = it1.next();
			Route thisRoute = entry.getKey().getRoute();
			ArrayList<TripWithStopTimeList> tripsWithTime = new ArrayList<TripWithStopTimeList>();
			HashSet<String> stopList = new HashSet<String>();
			RouteWithTripList thisRoute2 = new RouteWithTripList(thisRoute,
					tripsWithTime, stopList);

			routesWithTripList.add(thisRoute2);

			for (ArrayList<StopTime> stopTimeList : trips.values()) {
				// sort the stop time elements of each trip of each route
				Collections.sort(stopTimeList);

				// build trip element and add to the trip list
				int startTime = stopTimeList.get(0).getArrivalTime();
				int endTime = stopTimeList.get(stopTimeList.size() - 1)
						.getDepartureTime();
				TripWithStopTimeList tripWithTime = new TripWithStopTimeList(
						stopTimeList.get(0).getTrip(), startTime, endTime,
						stopTimeList);
				tripsWithTime.add(tripWithTime);

				// populate stop list of the route
				for (StopTime st : stopTimeList) {
					String id = st.getStop().getId().getId();
					if (!stopList.contains(id)) {
						stopList.add(id);
					}
				}
			}
			// sort trips based on their start time
			Collections.sort(tripsWithTime);
		}
	}

	/**
	 * if a trip is for multiple(n) week days, it will create clone n-1 copies
	 * of the trip along with its StopTime objects. It also assign unique trip
	 * names for these copies, also update the trip field in StopTime objects
	 * 
	 * @param top2BottomStructure
	 *            where to get elements conveniently
	 * @param calendarMap
	 *            where the
	 * @return the number of Trip objects after split
	 */
	public static int splitTrips4MultipleWeekDay(
			HashMap<Route, HashMap<Trip, ArrayList<StopTime>>> top2BottomStructure,
			Map<AgencyAndId, ServiceCalendar> calendarMap) {
		int numOfTripsAfterMadeUp = 0;
		for (HashMap<Trip, ArrayList<StopTime>> trips : top2BottomStructure
				.values()) {

			HashMap<Trip, ArrayList<StopTime>> tripsToAdd = new HashMap<Trip, ArrayList<StopTime>>();
			Iterator<Trip> it1 = trips.keySet().iterator();
			Route route = it1.next().getRoute();
			for (ArrayList<StopTime> stopTimeList : trips.values()) {
				// make up trips and their stop times for each service week day
				Trip thisTrip = stopTimeList.get(0).getTrip();
				ServiceCalendar calendar = calendarMap.get(thisTrip
						.getServiceId());
				Boolean[] weekdayFlags = getWeekDayFlags(calendar);

				int numOfDays = numOfSet(weekdayFlags);
				assert (numOfDays >= 1 && numOfDays < 8) : "invalide calendar element:"
						+ calendar.getServiceId().getAgencyId();
				if (numOfDays == 1) {
					for (Weekday day : Weekday.values()) {
						if (weekdayFlags[day.ordinal()]) {
							int offset = 86400 * day.ordinal();
							offSetStopTimes(stopTimeList, offset);
						}
					}
				} else {
					boolean isFirstDay = true;
					ArrayList<ArrayList<StopTime>> prototypes = createTripListFromProto(
							stopTimeList, numOfDays - 1);
					int i = 0;
					for (Weekday day : Weekday.values()) {
						if (weekdayFlags[day.ordinal()]) {
							int offset = 86400 * day.ordinal();
							if (isFirstDay) {
								offSetStopTimes(stopTimeList, offset);
								isFirstDay = false;
							} else {
								offSetStopTimesAndTrip(prototypes.get(i),
										offset, day.ordinal());
							}
						}
						i++;
						if (i >= numOfDays) {
							break;
						}
					}
					// add new trips
					for (ArrayList<StopTime> list : prototypes) {
						Trip trip = list.get(0).getTrip();
						tripsToAdd.put(trip, list);
					}
				}

			}
			// add tripsToAdd to the list of trips of the route
			if (!tripsToAdd.isEmpty()) {
				for (ArrayList<StopTime> stopTimeList : tripsToAdd.values()) {
					Trip trip = stopTimeList.get(0).getTrip();
					trips.put(trip, stopTimeList);
				}

				System.out.println(tripsToAdd.size()
						+ " trips are made up for route-"
						+ route.getId().getId());
			}
			numOfTripsAfterMadeUp += trips.size();
		}
		System.out.println(numOfTripsAfterMadeUp + " trips after make up.");
		return numOfTripsAfterMadeUp;
	}

	/**
	 * traverse useful StopTime objects and map them to corresponding Trip
	 * objects and Route objects
	 * 
	 * @param top2BottomStructure
	 *            the structure to populate
	 * @param stopTimes
	 *            all useful StopTime objects
	 */
	public static void obtainTop2BottomStructure(
			HashMap<Route, HashMap<Trip, ArrayList<StopTime>>> top2BottomStructure,
			Collection<StopTime> stopTimes) {
		Iterator<StopTime> it2 = stopTimes.iterator();
		while (it2.hasNext()) {
			StopTime st = it2.next();
			Trip trip = st.getTrip();
			Route route = st.getTrip().getRoute();
			ArrayList<StopTime> stopTimeList = null;
			HashMap<Trip, ArrayList<StopTime>> tripStopTimeMap = null;

			if (!top2BottomStructure.containsKey(route)) {
				tripStopTimeMap = new HashMap<Trip, ArrayList<StopTime>>();
				top2BottomStructure.put(route, tripStopTimeMap);
			} else {
				tripStopTimeMap = top2BottomStructure.get(route);
			}

			if (!tripStopTimeMap.containsKey(trip)) {
				stopTimeList = new ArrayList<StopTime>();
				top2BottomStructure.get(route).put(trip, stopTimeList);
			} else {
				stopTimeList = tripStopTimeMap.get(trip);
			}

			stopTimeList.add(st);
		}
	}

	/**
	 * remove the StopTime objects of useless Trip objects
	 * 
	 * @param stopTimes
	 * @param usefulTrips
	 */
	public static void removeUselessStopTime(Collection<StopTime> stopTimes,
			HashSet<Trip> usefulTrips) {
		Iterator<StopTime> it = stopTimes.iterator();
		Trip bufTrip = null;
		boolean shouldRemove = false;
		int numOfOptimized = 0;
		while (it.hasNext()) {
			StopTime st = it.next();
			Trip thisTrip = st.getTrip();
			if (bufTrip == null || !bufTrip.equals(thisTrip)) {
				bufTrip = thisTrip;
				shouldRemove = !usefulTrips.contains(bufTrip);
			} else {
				numOfOptimized++;
			}
			if (shouldRemove) {
				it.remove();
			}
		}
		System.out.println("\nnum of optimized =" + numOfOptimized
				+ "\tnum of stoptime = " + stopTimes.size());
	}

	/**
	 * 
	 * There can be trips for the same route, for the same week day but for
	 * different calendar service duration. As ONE needs schedule of one week at
	 * most, we only need one version of schedules for each valid week day. This
	 * method builds a HashSet usefulTrips having refers to all the useful
	 * trips, and a HashMap of int id <-> Route object because ONE supports
	 * integer id for routes.
	 * 
	 * @param allRoutes
	 * @param allTrips
	 * @param calendarMap
	 * @param route2IntIdMap
	 * @param usefulTrips
	 */
	public static void obtainUsefulTrips(Collection<Route> allRoutes,
			Collection<Trip> allTrips,
			Map<AgencyAndId, ServiceCalendar> calendarMap,
			HashMap<Route, Integer> route2IntIdMap, HashSet<Trip> usefulTrips) {

		int numOfRoutes = allRoutes.size();
		int numOfTrips = allTrips.size();
		int routeIntId = 0;
		for (Route route : allRoutes) {
			// exclude routes with not interested transport types
			int routeType = route.getType();
			if (routeType != Converter.BUS_TYPE
					&& routeType != Converter.METRO_TYPE
					&& routeType != Converter.RAIL_TYPE
					&& routeType != Converter.TRAM_TYPE) {
				continue;
			}

			route2IntIdMap.put(route, routeIntId);
			routeIntId++;
			// exclude trips for the same week day but belongs to calendar
			// service of different duration
			HashMap<Weekday, String> records = new HashMap<Weekday, String>();
			Iterator<Trip> it = allTrips.iterator();
			while (it.hasNext()) {
				Trip trip = it.next();
				if (trip.getRoute().equals(route)) {
					ServiceCalendar calendar = calendarMap.get(trip
							.getServiceId());
					String record = calendar.getStartDate().getAsString()
							+ calendar.getEndDate().getAsString();
					Boolean[] flags = getWeekDayFlags(calendar);

					for (Weekday day : Weekday.values()) {
						if (flags[day.ordinal()]) {
							if (records.containsKey(day)) {
								if (records.get(day).equals(record)) {
									usefulTrips.add(trip);
									continue;
								}
							} else {
								records.put(day, record);
								usefulTrips.add(trip);
								continue;
							}
						}
					}

				}
			}

		}
		System.out.println("\nafter clearup" + "\troutes:" + routeIntId + "/"
				+ numOfRoutes + "\ttrips:" + usefulTrips.size() + "/"
				+ numOfTrips);
	}

	/**
	 * translate the type of route to the layer id used in ONE
	 * 
	 * @param type
	 * @return layer id used in ONE
	 */
	public static int getLayerId(int type) {
		if (type == Converter.METRO_TYPE) {
			return DTNHost.LAYER_UNDERGROUND;
		} else {
			return DTNHost.LAYER_DEFAULT;
		}
	}

	/**
	 * determine if two trips can be linked
	 * 
	 * @param lastEndTime
	 *            end time of the first trip
	 * @param lastEndLocation
	 *            location of the last stop of the first trip
	 * @param thisStartTime
	 *            end time of the second trip
	 * @param thisStartLocation
	 *            location of the starting stop of the second trip
	 * @param maxSpeed
	 *            the max speed of vehicles, rough estimation is sufficient
	 * @param maxDistance
	 *            the threshold of distance between two link-able trips of rail
	 *            transports
	 * @param transportType
	 * @return two trips can be linked or not
	 */
	public static boolean isNextStrip(int lastEndTime, Coord lastEndLocation,
			int thisStartTime, Coord thisStartLocation, double maxSpeed,
			double maxDistance, int transportType) {
		if (lastEndTime > thisStartTime) {
			return false;
		}

		double distance = lastEndLocation.distance(thisStartLocation);

		if (transportType == Converter.METRO_TYPE
				|| transportType == Converter.RAIL_TYPE
				|| transportType == Converter.TRAM_TYPE) {
			if (distance > maxDistance) {
				return false;
			}
		}

		if ((distance / maxSpeed) > (thisStartTime - lastEndTime)) {
			return false;
		} else {
			return true;
		}
	}

	public static ArrayList<StopDataUnit> stopTimeList2DataUniteList(
			ArrayList<StopTime> stopTimeList) {
		ArrayList<StopDataUnit> sduList = new ArrayList<StopDataUnit>();
		for (StopTime st : stopTimeList) {
			StopDataUnit sdu = stopTime2DataUnit(st);
			sduList.add(sdu);
		}
		return sduList;
	}

	public static StopDataUnit stopTime2DataUnit(StopTime st) {
		StopDataUnit sdu = new StopDataUnit();
		sdu.stop_id = st.getStop().getId().getId();
		sdu.arrT = st.getArrivalTime();
		sdu.depT = st.getDepartureTime();
		return sdu;
	}

	public static ArrayList<ArrayList<StopTime>> createTripListFromProto(
			ArrayList<StopTime> stopTimeList, int n) {
		ArrayList<ArrayList<StopTime>> ret = new ArrayList<ArrayList<StopTime>>();
		for (int i = 0; i < n; i++) {
			ret.add(deepCopystopTimeList(stopTimeList));
		}
		return ret;
	}

	public static int numOfSet(Boolean[] array) {
		int sum = 0;
		for (Boolean el : array) {
			if (el) {
				sum++;
			}
		}
		return sum;
	}

	public static ArrayList<StopTime> deepCopystopTimeList(
			ArrayList<StopTime> stopTimeList) {
		ArrayList<StopTime> ret = new ArrayList<StopTime>();
		for (StopTime st : stopTimeList) {
			ret.add(new StopTime(st));
		}
		return ret;
	}

	public static void offSetStopTimes(ArrayList<StopTime> stopTimeList,
			int offset) {
		for (StopTime st : stopTimeList) {
			int arrT = st.getArrivalTime();
			int depT = st.getDepartureTime();
			st.setArrivalTime(arrT + offset);
			st.setDepartureTime(depT + offset);
		}
	}

	public static void offSetStopTimesAndTrip(ArrayList<StopTime> stopTimeList,
			int offset, int weekday) {
		Trip origTrip = stopTimeList.get(0).getTrip();
		Trip newTrip = new Trip(origTrip);
		AgencyAndId origAAI = origTrip.getId();
		newTrip.setId(AgencyAndId.convertFromString(origAAI.getAgencyId()
				+ AgencyAndId.ID_SEPARATOR + origAAI.getId() + "weekday_"
				+ weekday));
		System.out.println("new trip:" + newTrip.toString() + " is created.");
		for (StopTime st : stopTimeList) {
			int arrT = st.getArrivalTime();
			int depT = st.getDepartureTime();
			st.setArrivalTime(arrT + offset);
			st.setDepartureTime(depT + offset);
			st.setTrip(newTrip);
		}
	}

	public static Boolean[] getWeekDayFlags(ServiceCalendar calendar) {
		Boolean[] weekdayFlags = new Boolean[7];
		if (calendar.getMonday() != 0) {
			weekdayFlags[0] = true;
		} else {
			weekdayFlags[0] = false;
		}

		if (calendar.getTuesday() != 0) {
			weekdayFlags[1] = true;
		} else {
			weekdayFlags[1] = false;
		}

		if (calendar.getWednesday() != 0) {
			weekdayFlags[2] = true;
		} else {
			weekdayFlags[2] = false;
		}

		if (calendar.getThursday() != 0) {
			weekdayFlags[3] = true;
		} else {
			weekdayFlags[3] = false;
		}

		if (calendar.getFriday() != 0) {
			weekdayFlags[4] = true;
		} else {
			weekdayFlags[4] = false;
		}

		if (calendar.getSaturday() != 0) {
			weekdayFlags[5] = true;
		} else {
			weekdayFlags[5] = false;
		}

		if (calendar.getSunday() != 0) {
			weekdayFlags[6] = true;
		} else {
			weekdayFlags[6] = false;
		}

		return weekdayFlags;
	}

	public static Map<AgencyAndId, ServiceCalendar> getCalendarMap(
			Collection<ServiceCalendar> calendars) {
		HashMap<AgencyAndId, ServiceCalendar> ret = new HashMap<AgencyAndId, ServiceCalendar>();
		for (ServiceCalendar calendar : calendars) {
			ret.put(calendar.getServiceId(), calendar);
		}
		return ret;
	}

}
