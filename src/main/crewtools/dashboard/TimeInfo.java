package crewtools.dashboard;

public class TimeInfo {
  private final String companyShowOffset;
  private final String companyShowZulu;
  private final String departureOffset;
  private final String departureZulu;

  public TimeInfo(
      String companyShowOffset,
      String companyShowZulu,
      String departureOffset,
      String departureZulu) {
    this.companyShowOffset = companyShowOffset;
    this.companyShowZulu = companyShowZulu;
    this.departureOffset = departureOffset;
    this.departureZulu = departureZulu;
  }

  /** Relative show time from now, as scheduled by the company. */
  public String getCompanyShowOffset() {
    return companyShowOffset;
  }

  /** Absolute show time from now, as scheduled by the company. */
  public String getCompanyShowZulu() {
    return companyShowZulu;
  }

  public String getDepartureOffset() {
    return departureOffset;
  }

  public String getDepartureZulu() {
    return departureZulu;
  }
}
