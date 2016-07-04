# Deviation 

Service that takes coordinates(latitude and longitude) and active provides disruption 
in Stockholm's commute service close to the provided location.

You need your own API key for the 
[nearbystops](https://www.trafiklab.se/api/sl-narliggande-hallplatser) and 
[deviations(v2)](https://www.trafiklab.se/api/sl-storningsinformation-2) 
service from [Trafiklab](https://trafiklab.se)

## Build and Run

### With docker

```
./docker-build.sh
export $NEARBYSTOPS="YOUR KEY"
export $DEVIATIONS="YOUR OTHER KEY"
./docker-run.sh
```

### With sbt

```
./sbt -Dservices.nearbystops.key="YOUR KEY" -Dservices.deviations.key="YOUR OTHER KEY" run
```

### Access the API

The API is very simple you do a HTTP GET to `/deviation/lat,long` where lat and long are
replaced with your desired latitude and longitude. Example:
```
http://localhost:8080/deviation/59.32,18.07
```

If you use docker-machine, replace localhost with the IP for your docker-machine.

The respond is a JSON array of deviations according with the same field as the underlying
[deviation API](https://www.trafiklab.se/api/sl-storningsinformation-2/sl-storningsinformation-2).


## Author and license

If you have any questions regarding this project contact:
Magnus Runesson <magru@linuxalert.org>.

For licensing info see LICENSE file in project's root directory.
