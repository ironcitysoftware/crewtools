package crewtools.dashboard;

public class FlightInfo {
  private final String flightNumber;
  private final String originAirport;
  private final String originGate;
  private final String destinationAirport;
  private final String destinationGate;
  private final String aircraftType;
  private final boolean isCanceled;
  private final TimeInfo timeInfo;

  FlightInfo(String flightNumber, String originAirport, String originGate,
      String destinationAirport,
      String destinationGate, String aircraftType,
      boolean isCanceled,
      TimeInfo timeInfo) {
    this.flightNumber = flightNumber;
    this.originAirport = originAirport;
    this.originGate = originGate;
    this.destinationAirport = destinationAirport;
    this.destinationGate = destinationGate;
    this.aircraftType = aircraftType;
    this.isCanceled = isCanceled;
    this.timeInfo = timeInfo;
  }

  public String getFlightNumber() {
    return flightNumber;
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

  public boolean isCanceled() {
    return isCanceled;
  }

  public TimeInfo getTimeInfo() {
    return timeInfo;
  }
}
