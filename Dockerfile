FROM java:openjdk-8
MAINTAINER Magnus Runesson<magru@linuxalert.org>

ENV DST=/opt/deviations/

RUN mkdir -p ${DST}/src ${DST}/project
WORKDIR ${DST}
COPY sbt *.sbt ./
COPY project/build.properties project/plugins.sbt ./project/
EXPOSE 8080
RUN ./sbt update
COPY src ./src/

RUN ./sbt compile

CMD ${DST}/sbt run -Dservices.nearbystops.key=$NEARBYSTOPS -Dservices.deviations.key=$DEVIATIONS
