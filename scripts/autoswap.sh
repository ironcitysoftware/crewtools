#!/bin/sh

CLASSPATH=bin
CLASSPATH=$CLASSPATH:lib/guava-21.0.jar
CLASSPATH=$CLASSPATH:lib/gson-2.8.0.jar
CLASSPATH=$CLASSPATH:lib/jsoup-1.10.2.jar
CLASSPATH=$CLASSPATH:lib/okhttp-3.10.0.jar
CLASSPATH=$CLASSPATH:lib/okio-1.14.1.jar
CLASSPATH=$CLASSPATH:lib/protobuf-2.6.1.jar
CLASSPATH=$CLASSPATH:lib/joda-time-2.2.jar
CLASSPATH=$CLASSPATH:lib/slf4j-api-1.7.25.jar
CLASSPATH=$CLASSPATH:lib/slf4j-jdk14-1.7.25.jar

java \
  -Djava.util.logging.config.file=scripts/logging.properties \
  -cp $CLASSPATH \
  crewtools.flica.grid.AutoSwap DCA CLT CAPTAIN 2019-05 $@
