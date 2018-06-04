package crewtools.dashboard;

public class FlightInfo {
  private final String originAirport;
  private final String originGate;
  private final String destinationAirport;
  private final String destinationGate;
  private final String aircraftType;
  private final TimeInfo timeInfo;

  FlightInfo(String originAirport, String originGate, String destinationAirport,
      String destinationGate, String aircraftType, TimeInfo timeInfo) {
    this.originAirport = originAirport;
    this.originGate = originGate;
    this.destinationAirport = destinationAirport;
    this.destinationGate = destinationGate;
    this.aircraftType = aircraftType;
    this.timeInfo = timeInfo;
  }

  public String getOriginAirport() {
    return originAirport;
  }

  public String getOriginGate() {
    return originGate;
  }

  public String getDestinationAirport() {
    return destinationAirport;
  }

  public String getDestinationGate() {
    return destinationGate;
  }

  public String getAircraftType() {
    return aircraftType;
  }

  public TimeInfo getTimeInfo() {
    return timeInfo;
  }
}
