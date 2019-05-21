package crewtools.flica.grid;

import crewtools.flica.Proto.Schedule;
import crewtools.rpc.Proto.GridObservation;

public interface Observer {
  public void observe(GridObservation observation);
  public void observe(Schedule schedule);
}
