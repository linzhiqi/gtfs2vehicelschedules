package util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.onebusaway.gtfs.model.Route;


public class IOUtil {

	
	public static void writeToWKTPoint(HashMap<String, Coord> stopMap,
			String filePath) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(new File(filePath)));
			
			for (Coord c : stopMap.values()) {
				writer.append("POINT ("+c.getX()+" "+c.getY()+")\n");
			}
			writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void writeToJSONFile(Object object, String filePath) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(
					new File(filePath), object);
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static HashMap<String, Coord> loadStopMap(String path) {
		HashMap<String, Coord> stopMap = null;
		ObjectMapper mapper = new ObjectMapper();
		
		try {
			stopMap = mapper.readValue(new FileReader(new File(path)), 
					    new TypeReference<HashMap<String, Coord>>(){});
		} catch (JsonParseException e) {
			System.err.println("file " + path + " failed to be parsed.");
			e.printStackTrace();
			System.exit(-1);
		} catch (JsonMappingException e) {
			System.err.println("file " + path + " failed to be parsed.");
			e.printStackTrace();
			System.exit(-1);
		} catch (FileNotFoundException e) {
			System.err.println("file " + path + " is not found.");
			System.exit(-1);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		return stopMap;
	}
	
	public static void writeLinesToWKTFile(List<List<Coord>> lines, String fileName) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(new File(fileName)));

			for (List<Coord> line : lines) {
				StringBuilder sb = new StringBuilder();
				String lineStr = null;
				for (int i = 0; i < line.size(); i++) {
					Coord c = line.get(i);
					sb.append(c.getX() + " " + c.getY());
					if (i != (line.size() - 1)) {
						sb.append(", ");
					}
				}
				lineStr = sb.toString();
				writer.append("LINESTRING (" + lineStr + ")\n");
			}
			writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static void outputRouteIdMapping(
			HashMap<Route, Integer> route2IntIdMap,
			String routeIdMappingFileName) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(new File(routeIdMappingFileName)));
			Set<Entry<Route, Integer>> set = route2IntIdMap.entrySet();
			Iterator<Entry<Route, Integer>> it = set.iterator();
			writer.append("[real-route-id] -> [numeric id]\n");
			while(it.hasNext()){
				Entry<Route, Integer> entry = it.next();		
				writer.append(entry.getKey().getId().getId() + " -> " + entry.getValue() + "\n");
			}			
			writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
