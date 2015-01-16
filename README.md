java program converting gtfs data to vehicle specific schedules

gtfs data as input can be compressed ZIP file or decompressed folder
the output is a JSON file with format shown below:
{“routes”:[
    {“route_id”:int, 
     “layer_id”:int,
     “stops”:[int, ...],
     “vehicles”:[
         {“vehicle_id”:int,
          “trips”:[
              [{“stop_id”: int, “arrT”:double, “depT”:double},
                ...
              ],
              ...
          ]},
     ...]},
 ...]}

This project depends on the ONE project > 1.5.2v
It requires the departure and arrival time in stop_time.txt of the gtfs data.
It requires the stop_lat and stop_lon fields in stops.txt converted from geographic coordinates (longitude and latitude) to Cartesian (x,y). This can be done by using a browser based tool - gtfs_stops_crs_converter. 
