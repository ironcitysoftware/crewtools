package crewtools.dashboard;

public class TimeInfo {
  private final String companyShowOffset;
  private final String companyShowZulu;

  public TimeInfo(String companyShowOffset, String companyShowZulu) {
    this.companyShowOffset = companyShowOffset;
    this.companyShowZulu = companyShowZulu;
  }

  public String getCompanyShowOffset() {
    return companyShowOffset;
  }

  public String getCompanyShowZulu() {
    return companyShowZulu;
  }
}
