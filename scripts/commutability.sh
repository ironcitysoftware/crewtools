#!/bin/sh

CLASSPATH=bin
CLASSPATH=$CLASSPATH:lib/commons-io-2.6.jar
CLASSPATH=$CLASSPATH:lib/commons-logging-1.2.jar
CLASSPATH=$CLASSPATH:lib/guava-21.0.jar
CLASSPATH=$CLASSPATH:lib/joda-time-2.2.jar
CLASSPATH=$CLASSPATH:lib/jsoup-1.10.2.jar
CLASSPATH=$CLASSPATH:lib/protobuf-2.6.1.jar

java \
  -Djava.util.logging.config.file=scripts/logging.properties \
  -cp $CLASSPATH \
  crewtools.flica.stats.Commutability $@
