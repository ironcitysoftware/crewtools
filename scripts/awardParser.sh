#!/bin/sh

CLASSPATH=bin
CLASSPATH=$CLASSPATH:lib/guava-21.0.jar
CLASSPATH=$CLASSPATH:lib/jsoup-1.10.2.jar
CLASSPATH=$CLASSPATH:lib/commons-logging-1.2.jar
CLASSPATH=$CLASSPATH:lib/commons-codec-1.9.jar
CLASSPATH=$CLASSPATH:lib/protobuf-2.6.1.jar
CLASSPATH=$CLASSPATH:lib/joda-time-2.2.jar
CLASSPATH=$CLASSPATH:lib/gson-2.8.0.jar
CLASSPATH=$CLASSPATH:lib/tika-core-1.16.jar
CLASSPATH=$CLASSPATH:lib/tika-parsers-1.16.jar
CLASSPATH=$CLASSPATH:lib/jempbox-1.8.13.jar
CLASSPATH=$CLASSPATH:lib/pdfbox-2.0.6.jar
CLASSPATH=$CLASSPATH:lib/commons-io-2.6.jar
CLASSPATH=$CLASSPATH:lib/fontbox-2.0.6.jar

java \
  -Djava.util.logging.config.file=scripts/logging.properties \
  -cp $CLASSPATH \
  crewtools.psa.AwardParser $@
