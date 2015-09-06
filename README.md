### Intrudoction

This is a java program converting gtfs data to vehicle specific schedules

The project depends on the ONE project > 1.5.2v
It requires the departure and arrival time in stop_time.txt of the gtfs data.
It requires the stop_lat and stop_lon fields in stops.txt converted from geographic coordinates (longitude and latitude) to Cartesian (x,y). This can be done by using a browser based tool - gtfs_stops_crs_converter. 

### How to use

1. firstly, convert the lat-lon to Cartesian (x,y) in stops.txt of gtfs data by using this tool https://github.com/linzhiqi/gtfs_stops_crs_converter
2. run this java program by:
```bash
java -jar [name of the executable jar] -i [path of the gtfs folder]  -v [x_offset, y offset]   -b [x_min, y_min, x_max, y_max] -r [interested_routes_file_name]
```

-v is useful when you want to offset the location of stops.
-b specifies the geographic boundries. This is useful when you do not want to work on the whole area covered by this gtfs data.
-r specifies the ids of the routes that you are interested. This is useful when you only want to convert some certain routes. The format of the file's content is one id a line.

### The output files

The program produce 4 output file.

#### schedules.json

This is a JSON file with format shown below:

```json
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
```

#### stops.json and stops.wkt

This file contains the id and location info of the stops, one in json format, one in wkt format.

#### route_id_mapping.json

As you can see, in the schedules.json file, the route_id is a integer randomly assigned to routes by the program. but sometimes, we need to know the real textual id of a route which is not neccesarily a numeber. So this file contains the knowledge of the textual id for each route.
