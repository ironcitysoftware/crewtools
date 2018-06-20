java -Djava.util.logging.config.file=scripts/logging.properties \
   -cp lib/guava-21.0.jar:lib/httpcore-4.4.6.jar:lib/protobuf-2.6.1.jar:lib/okhttp-3.10.0.jar:lib/okio-1.14.1.jar:lib/gson-2.8.0.jar \
   crewtools.aa.FlightStatusService $@

