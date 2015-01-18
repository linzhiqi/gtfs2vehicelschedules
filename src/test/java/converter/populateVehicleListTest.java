package converter;

import java.util.ArrayList;

import org.junit.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;

import datastructure.RouteWithTripList;
import datastructure.TripWithStopTimeList;

import junit.framework.TestCase;

public class populateVehicleListTest extends TestCase{

	@Test
	public void testOneVehicle() {
		// create start and end stops
		Stop stop0 = new Stop();
		stop0.setId(new AgencyAndId("hsl", "0"));
		stop0.setLon(1.0d);
		stop0.setLat(1.0d);
		Stop stop1 = new Stop();
		stop1.setId(new AgencyAndId("hsl", "1"));
		stop1.setLon(1.0d);
		stop1.setLat(1201.0d);
		Stop stop2 = new Stop();
		stop2.setId(new AgencyAndId("hsl", "2"));
		stop2.setLon(1.0d);
		stop2.setLat(2401.0d);
		Stop stop3 = new Stop();
		stop3.setId(new AgencyAndId("hsl", "3"));
		stop3.setLon(1.0d);
		stop3.setLat(3601.0d);
		Stop stop4 = new Stop();
		stop4.setId(new AgencyAndId("hsl", "4"));
		stop4.setLon(1.0d);
		stop4.setLat(4801.0d);
		
		
		//build trip0
		ArrayList<StopTime> stopTimeList0 = new ArrayList<StopTime>();
		StopTime stopTime00 = new StopTime();
		StopTime stopTime01 = new StopTime();
		StopTime stopTime02 = new StopTime();
		StopTime stopTime03 = new StopTime();
		StopTime stopTime04 = new StopTime();
		
		stopTime00.setStop(stop0);
		stopTime01.setStop(stop1);
		stopTime02.setStop(stop2);
		stopTime03.setStop(stop3);
		stopTime04.setStop(stop4);
		
		stopTimeList0.add(stopTime00);
		stopTimeList0.add(stopTime01);
		stopTimeList0.add(stopTime02);
		stopTimeList0.add(stopTime03);
		stopTimeList0.add(stopTime04);
		
		TripWithStopTimeList trip0 = new TripWithStopTimeList(new Trip(), 0, 400, stopTimeList0);
		
		// build trip1
		ArrayList<StopTime> stopTimeList1 = new ArrayList<StopTime>();
		StopTime stopTime10 = new StopTime();
		StopTime stopTime11 = new StopTime();
		StopTime stopTime12 = new StopTime();
		StopTime stopTime13 = new StopTime();
		StopTime stopTime14 = new StopTime();
		
		stopTime10.setStop(stop4);
		stopTime11.setStop(stop3);
		stopTime12.setStop(stop2);
		stopTime13.setStop(stop1);
		stopTime14.setStop(stop0);
		stopTimeList1.add(stopTime10);
		stopTimeList1.add(stopTime11);
		stopTimeList1.add(stopTime12);
		stopTimeList1.add(stopTime13);
		stopTimeList1.add(stopTime14);
		
		TripWithStopTimeList trip1 = new TripWithStopTimeList(new Trip(), 400, 800, stopTimeList1);
		
		// build trip2
		ArrayList<StopTime> stopTimeList2 = new ArrayList<StopTime>();
		StopTime stopTime20 = new StopTime();
		StopTime stopTime21 = new StopTime();
		StopTime stopTime22 = new StopTime();
		StopTime stopTime23 = new StopTime();
		StopTime stopTime24 = new StopTime();
		
		stopTime20.setStop(stop0);
		stopTime21.setStop(stop1);
		stopTime22.setStop(stop2);
		stopTime23.setStop(stop3);
		stopTime24.setStop(stop4);
		
		stopTimeList2.add(stopTime20);
		stopTimeList2.add(stopTime21);
		stopTimeList2.add(stopTime22);
		stopTimeList2.add(stopTime23);
		stopTimeList2.add(stopTime24);
		
		TripWithStopTimeList trip2 = new TripWithStopTimeList(new Trip(), 800, 1200, stopTimeList2);
		
		// populate trip list
		ArrayList<TripWithStopTimeList> tripList = new ArrayList<TripWithStopTimeList>();
		tripList.add(trip0);
		tripList.add(trip1);
		tripList.add(trip2);
		
		// build route
		Route route = new Route();
		route.setId(new AgencyAndId("hsl","0000"));
		route.setType(Converter.BUS_TYPE);
		RouteWithTripList route1 = new RouteWithTripList(route, tripList, null);
		
		// build route list
		ArrayList<RouteWithTripList> routesWithTripList = new ArrayList<RouteWithTripList>();
		routesWithTripList.add(route1);
		
		// run targeted method
		Converter.populateVehicleList(routesWithTripList, 20, 500);
		
		// one vehicle comsumes all three trips
		assertTrue(routesWithTripList.get(0).getVehicleList().size()==1);
	}
	
	@Test
	public void testTwoVehicle() {
		// create start and end stops
		Stop stop0 = new Stop();
		stop0.setId(new AgencyAndId("hsl", "0"));
		stop0.setLon(1.0d);
		stop0.setLat(1.0d);
		Stop stop1 = new Stop();
		stop1.setId(new AgencyAndId("hsl", "1"));
		stop1.setLon(1.0d);
		stop1.setLat(1201.0d);
		Stop stop2 = new Stop();
		stop2.setId(new AgencyAndId("hsl", "2"));
		stop2.setLon(1.0d);
		stop2.setLat(2401.0d);
		Stop stop3 = new Stop();
		stop3.setId(new AgencyAndId("hsl", "3"));
		stop3.setLon(1.0d);
		stop3.setLat(3601.0d);
		Stop stop4 = new Stop();
		stop4.setId(new AgencyAndId("hsl", "4"));
		stop4.setLon(1.0d);
		stop4.setLat(4801.0d);
		
		
		//build trip0
		ArrayList<StopTime> stopTimeList0 = new ArrayList<StopTime>();
		StopTime stopTime00 = new StopTime();
		StopTime stopTime01 = new StopTime();
		StopTime stopTime02 = new StopTime();
		StopTime stopTime03 = new StopTime();
		StopTime stopTime04 = new StopTime();
		
		stopTime00.setStop(stop0);
		stopTime01.setStop(stop1);
		stopTime02.setStop(stop2);
		stopTime03.setStop(stop3);
		stopTime04.setStop(stop4);
		
		stopTimeList0.add(stopTime00);
		stopTimeList0.add(stopTime01);
		stopTimeList0.add(stopTime02);
		stopTimeList0.add(stopTime03);
		stopTimeList0.add(stopTime04);
		
		TripWithStopTimeList trip0 = new TripWithStopTimeList(new Trip(), 0, 400, stopTimeList0);
		
		// build trip1
		ArrayList<StopTime> stopTimeList1 = new ArrayList<StopTime>();
		StopTime stopTime10 = new StopTime();
		StopTime stopTime11 = new StopTime();
		StopTime stopTime12 = new StopTime();
		StopTime stopTime13 = new StopTime();
		StopTime stopTime14 = new StopTime();
		
		stopTime10.setStop(stop0);
		stopTime11.setStop(stop1);
		stopTime12.setStop(stop2);
		stopTime13.setStop(stop3);
		stopTime14.setStop(stop4);
		stopTimeList1.add(stopTime10);
		stopTimeList1.add(stopTime11);
		stopTimeList1.add(stopTime12);
		stopTimeList1.add(stopTime13);
		stopTimeList1.add(stopTime14);
		
		TripWithStopTimeList trip1 = new TripWithStopTimeList(new Trip(), 500, 900, stopTimeList1);
		
		// build trip2
		ArrayList<StopTime> stopTimeList2 = new ArrayList<StopTime>();
		StopTime stopTime20 = new StopTime();
		StopTime stopTime21 = new StopTime();
		StopTime stopTime22 = new StopTime();
		StopTime stopTime23 = new StopTime();
		StopTime stopTime24 = new StopTime();
		
		stopTime20.setStop(stop4);
		stopTime21.setStop(stop3);
		stopTime22.setStop(stop2);
		stopTime23.setStop(stop1);
		stopTime24.setStop(stop0);
		
		stopTimeList2.add(stopTime20);
		stopTimeList2.add(stopTime21);
		stopTimeList2.add(stopTime22);
		stopTimeList2.add(stopTime23);
		stopTimeList2.add(stopTime24);
		
		TripWithStopTimeList trip2 = new TripWithStopTimeList(new Trip(), 600, 1000, stopTimeList2);
		
		// populate trip list
		ArrayList<TripWithStopTimeList> tripList = new ArrayList<TripWithStopTimeList>();
		tripList.add(trip0);
		tripList.add(trip1);
		tripList.add(trip2);
		
		// build route
		Route route = new Route();
		route.setId(new AgencyAndId("hsl","0000"));
		route.setType(Converter.BUS_TYPE);
		RouteWithTripList route1 = new RouteWithTripList(route, tripList, null);
		
		// build route list
		ArrayList<RouteWithTripList> routesWithTripList = new ArrayList<RouteWithTripList>();
		routesWithTripList.add(route1);
		
		// run targeted method
		Converter.populateVehicleList(routesWithTripList, 20, 500);
		
		// one vehicle comsumes all three trips
		assertTrue(routesWithTripList.get(0).getVehicleList().size()==2);
		assertTrue(routesWithTripList.get(0).getVehicleList().get(0).trips.size()==2);
		assertTrue(routesWithTripList.get(0).getVehicleList().get(0).trips.get(0).get(0).stop_id.equals("0"));
		assertTrue(routesWithTripList.get(0).getVehicleList().get(0).trips.get(1).get(0).stop_id.equals("4"));
	}
}
