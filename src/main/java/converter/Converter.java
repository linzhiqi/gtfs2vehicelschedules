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
import java.util.Set;

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

import core.DTNHost;

import util.Coord;
import util.IOUtil;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * 
 * @author linzhiqi
 * 
 */
public class Converter {

	// routes other than these 4 types will be excluded
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

	public static final String SCHEDULE_FILE_NAME = "schedules.json";
	public static final String STOP_FILE_NAME = "stops.json";
	private static final String WKT_STOP_FILE_NAME = "stops.wkt";

	public enum Weekday {
		Mon, Tue, Wed, Thu, Fri, Sat, Sun
	};

	public static void main(String[] args) throws IOException {
		// parsing input options
		String usageStr = "usage: <-i gtfs_path> <-b xmin,ymin,xmax,ymax> " +
				"[-s max_speed] [-d max_distance] [-v x_offset,y_offset] ";
		String inputPath = null;
		double[] boundaries = null;
		double x_offset = 0.0;
		double y_offset = 0.0;
		OptionParser parser = new OptionParser("i:b:s:d:v:h");
		OptionSet options = parser.parse(args);
		
		if (!options.has("i")) {
			System.out.print(usageStr);
			System.exit(-1);
		} else {
			inputPath = (String) options.valueOf("i");
		}
		
		if (!options.has("b")) {
			System.out.print(usageStr);
			System.exit(-1);
		} else {
			String boundStr = (String) options.valueOf("b");
			String[] bounds = boundStr.split(",");
			if (bounds.length != 4) {
				System.out.print(usageStr);
				System.exit(-1);
			} else {
				boundaries = new double[4];
				for (int i = 0; i < 4; i++) {
					boundaries[i] = Double.valueOf(bounds[i]);
				}
			}
		}
		
		if (options.has("v")) {
			String vector = (String) options.valueOf("v");
			String[] element = vector.split(",");
			if (element.length!=2) {
				System.out.print(usageStr);
				System.exit(-1);
			} else {
				x_offset = Double.valueOf(element[0]);
				y_offset = Double.valueOf(element[1]);
			}	
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

		// read gtfs
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

		// arrange Route, Trip and StopTime elements in a top to bottom manner
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

		// build stopId -> stopLoc HashMap
		Collection<Stop> stops = store.getAllStops();
		HashMap<String, Coord> stopMap = new HashMap<String, Coord>();
		buildStopMap(stops, stopMap);
		
		// deal with schedules out of the given location boundaries
		constrainOutOfBound(boundaries, stopMap, routeSchedules);
		
		// exclude stops out of boundaries
		excludeOutBoundStop(boundaries, stopMap);
		
		// convert vehicle schedules to JSON file
		IOUtil.writeToJSONFile(routeSchedules, SCHEDULE_FILE_NAME);
		
		if(options.has("v")){
			// offset all the stops
			offsetCoordsInCollection(stopMap.values(), x_offset, y_offset);
		}
		
		// convert stop list to JSON file
		IOUtil.writeToJSONFile(stopMap, STOP_FILE_NAME);
		// extract coordinates from stop list to WKT file
		IOUtil.writeToWKTPoint(stopMap, WKT_STOP_FILE_NAME);
	}

	public static void offsetCoordsInCollection(Collection<Coord> values, double x_offset, double y_offset) {
		Iterator<Coord> it = values.iterator();
		while (it.hasNext()) {
			Coord c = it.next();
			c.translate(x_offset, y_offset);
		}
	}

	public static void excludeOutBoundStop(double[] boundaries,
			HashMap<String, Coord> stopMap) {
		Set<String> stopIds = stopMap.keySet();
		Iterator<String> it = stopIds.iterator();
		int num = stopIds.size();	
		while (it.hasNext()) {
			String id = it.next();
			Coord c = stopMap.get(id);
			if (!isCoordInBound(c.getX(), c.getY(), boundaries)){
				it.remove();
			}
		}
		
		System.out.println("within_boundary_stop_num/original_number: "+stopIds.size()+" / "+num);
	}

	/**
	 * build the stopMap so given a stop id, the stop location can be returned.
	 * 
	 * @param stops
	 *            all stops's info
	 * @param stopMap
	 *            the map to build
	 */
	public static void buildStopMap(Collection<Stop> stops,
			HashMap<String, Coord> stopMap) {
		Iterator<Stop> it = stops.iterator();
		while (it.hasNext()) {
			Stop stop = it.next();
			Coord loc = new Coord(stop.getLon(), stop.getLat());
			stopMap.put(stop.getId().getId(), loc);
		}
	}

	/**
	 * if in the trip, there is a sequence of stops within boundaries, and the
	 * number of them >= the half of the total number of stops of this trip, we
	 * shorten this trip to this stop sequence, with any other stops removed.
	 * 
	 * @param boundaries
	 *            a double array of length 4: [xmin][ymin][xmax][ymax]
	 * @param stopMap
	 *            the map provides stop coordinates when stop id is given
	 * @param routeSchedules
	 */
	public static void constrainOutOfBound(double[] boundaries,
			HashMap<String, Coord> stopMap,
			ArrayList<RouteSchedule> routeSchedules) {
		
		int stopsDeleted = 0, tripsDeleted = 0, vehicleDeleted = 0, routeDeleted = 0;
		
		Iterator<RouteSchedule> routeIt = routeSchedules.iterator();
		while (routeIt.hasNext()) {
			RouteSchedule route = routeIt.next();

			// if all stops of the route are within boundaries, no need to dig
			// in
			if (areStopsInBound(route.stops, stopMap, boundaries)) {
				continue;
			}

			Iterator<VehicleSchedule> vehicleIt = route.vehicles.iterator();
			while (vehicleIt.hasNext()) {
				VehicleSchedule vehicle = vehicleIt.next();

				Iterator<ArrayList<StopDataUnit>> tripIt = vehicle.trips
						.iterator();
				while (tripIt.hasNext()) {
					ArrayList<StopDataUnit> trip = tripIt.next();

					Iterator<StopDataUnit> stopIt = trip.iterator();
					int stopIndex = 0;
					int numOfStop = trip.size();

					// record the in bound stop sequence, and update the stop
					// list of the route
					int numOfInBoundStop = 0;
					int firstInBoundIndex = -1;
					boolean isFirstInBoundStop = true;
					boolean answerIsFound = false;
					while (stopIt.hasNext()) {
						StopDataUnit stop = stopIt.next();
						if (isStopInBound(stop.stop_id, stopMap, boundaries)) {
							if (answerIsFound) {
								continue;
							}
							if (isFirstInBoundStop) {
								firstInBoundIndex = stopIndex;
								isFirstInBoundStop = false;
							}
							numOfInBoundStop++;
						} else {
							// always update the stop list of the route
							if (route.stops.contains(stop.stop_id)) {
								route.stops.remove(stop.stop_id);
							}
							// when answer is found, do nothing extra, just
							// traverse later stops and update stop list of the
							// route.
							if (answerIsFound) {
								continue;
							}
							// this is when it determine that the trip can be
							// shorten rather than be deleted
							if (numOfInBoundStop >= numOfStop / 2) {
								answerIsFound = true;
							} else {
								numOfInBoundStop = 0;
								firstInBoundIndex = -1;
								isFirstInBoundStop = true;
							}

						}
						stopIndex++;
					}

					// for the case that the trip is ended at stops within
					// boundaries
					if (numOfInBoundStop >= numOfStop / 2) {
						answerIsFound = true;
					}

					// remove all the other stops
					stopIt = trip.iterator();
					int i = 0;
					while (stopIt.hasNext()) {
						stopIt.next();
						if (!answerIsFound) {
							stopIt.remove();
							stopsDeleted ++;
							continue;
						}
						if (i < firstInBoundIndex || i >= i + numOfInBoundStop) {
							stopIt.remove();
							stopsDeleted ++;
						} else {
							// is within boundaries, do nothing
						}
						i++;
					}

					// remove this trip from the vehicle if it's empty now
					if (trip.isEmpty()) {
						tripIt.remove();
						tripsDeleted ++;
					}
				}

				// remove this vehicle from the route if it's empty now
				if (vehicle.trips.isEmpty()) {
					vehicleIt.remove();
					vehicleDeleted ++;
				}
			}
			// remove this route if it has no vehicle
			if (route.vehicles.isEmpty()) {
				routeIt.remove();
				routeDeleted ++;
			}
		}
		System.out.println("\nstopsDeleted="+stopsDeleted+"\ttripsDeleted="+tripsDeleted+"\tvehicleDeleted="+vehicleDeleted+"\trouteDeleted"+routeDeleted);
	}

	public static boolean areStopsInBound(Set<String> stopsOfRoute,
			HashMap<String, Coord> stopMap, double[] boundaries) {
		Iterator<String> it = stopsOfRoute.iterator();
		while (it.hasNext()) {
			String id = it.next();
			if (!isStopInBound(id, stopMap, boundaries)) {
				return false;
			}
		}
		return false;
	}

	public static boolean isStopInBound(String stopId,
			HashMap<String, Coord> stopMap, double[] boundaries) {
		Coord c = stopMap.get(stopId);
		double x = c.getX();
		double y = c.getY();
		return isCoordInBound(x, y, boundaries);
	}
	
	public static boolean isCoordInBound(double x, double y, double[] boundaries) {
		double xmin = boundaries[0];
		double ymin = boundaries[1];
		double xmax = boundaries[2];
		double ymax = boundaries[3];
		//System.out.println("x="+x+" y="+y+" xmin"+xmin+" ymin"+ymin +" xmax"+xmax+" ymax"+ymax);
		if (x > xmin && x < xmax && y > ymin && y < ymax) {
			return true;
		} else {
			return false;
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
				int lastStartTime = 0;
				Coord lastEndLocation = null;
				while (it1.hasNext()) {
					TripWithStopTimeList trip = it1.next();
					ArrayList<StopTime> stopTimeList = trip.getStopTimeList();

					if (vehicle.trips.isEmpty()) {
						// the first trip for the vehicle
						ArrayList<StopDataUnit> stopDataUniteList = stopTimeList2DataUniteList(stopTimeList);
						vehicle.trips.add(stopDataUniteList);
						lastEndTime = trip.getEndTime();
						lastStartTime = trip.getStartTime();
						Stop lastEndStop = stopTimeList.get(
								stopTimeList.size() - 1).getStop();
						lastEndLocation = new Coord(lastEndStop.getLon(),
								lastEndStop.getLat());
						it1.remove();

					} else {
						// if is not the first trip, check if it is valid to be
						// the next trip
						int thisStartTime = trip.getStartTime();
						if(thisStartTime<lastStartTime) {
							assert false:"trip sorting error!";
						}
						Stop thisStartStop = stopTimeList.get(0).getStop();
						Coord thisStartLocation = new Coord(
								thisStartStop.getLon(), thisStartStop.getLat());
						if (isNextStrip(lastEndTime, lastEndLocation,
								thisStartTime, thisStartLocation, maxSpeed,
								maxDistance, transportType)) {
							ArrayList<StopDataUnit> stopDataUniteList = stopTimeList2DataUniteList(stopTimeList);
							vehicle.trips.add(stopDataUniteList);
							lastEndTime = trip.getEndTime();
							lastStartTime = trip.getStartTime();
							Stop lastEndStop = stopTimeList.get(
									stopTimeList.size() - 1).getStop();
							lastEndLocation = new Coord(lastEndStop.getLon(),
									lastEndStop.getLat());
							it1.remove();

						} else {
	
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
				int startTime = stopTimeList.get(0).getDepartureTime();
				int endTime = stopTimeList.get(stopTimeList.size() - 1)
						.getArrivalTime();
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
