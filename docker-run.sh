#!/bin/sh
[ -z ${DEVIATIONS} ] && echo Set DEVIATIONS environment variable to your deviation API key && exit 1
[ -z ${NEARBYSTOPS} ] && echo Set NEARBYSTOPS environment variable to your near by stops API key && exit 1
docker run -e DEVIATIONS -e NEARBYSTOPS -P deviations
