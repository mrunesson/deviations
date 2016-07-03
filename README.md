# Deviation 

Service that takes coordinates and return disruption in Stockholms
commute service close to the location.

You need your own API key for the nearbystops service from [Trafiklab](https://trafiklab.se)

## Run

### With docker

./docker-build.sh
export $NEARBYSTOPS="YOUR KEY"
export $DEVIATIONS="YOUR OTHER KEY"
./docker-run.sh

### With sbt

./sbt -Dservices.nearbystops.key="YOUR KEY" -Dservices.deviations.key="YOUR OTHER KEY" run

## Author & license

If you have any questions regarding this project contact:

Magnus Runesson <magru@linuxalert.org>.

For licensing info see LICENSE file in project's root directory.
