/**
 * Copyright 2018 Iron City Software LLC
 *
 * This file is part of CrewTools.
 *
 * CrewTools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CrewTools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CrewTools.  If not, see <http://www.gnu.org/licenses/>.
 */

package crewtools.flica.bid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.AutobidderRequest;
import crewtools.rpc.Proto.AutobidderResponse;
import crewtools.rpc.Proto.ScoreExplanation;

public class StatusService extends Thread {
  private final ServerSocket serverSocket;
  private final int PORT = 8422;
  private final int BACKLOG = 100;
  private final RuntimeStats stats;
  private final TripDatabase trips;

  public StatusService(RuntimeStats stats, TripDatabase trips)
      throws UnknownHostException, IOException {
    this.stats = stats;
    this.trips = trips;
    this.serverSocket = new ServerSocket(PORT, BACKLOG, InetAddress.getLocalHost());
    this.setDaemon(true);
    this.setName("StatusService");
  }

  @Override
  public void run() {
    while (true) {
      Socket socket = null;
      try {
        socket = serverSocket.accept();
      } catch (IOException e) {
        throw new RuntimeException("Error accepting client connection", e);
      }
      new Thread(new WorkerRunnable(socket)).start();
    }
  }

  public class WorkerRunnable implements Runnable {
    private Socket socket;

    public WorkerRunnable(Socket socket) {
      this.socket = socket;
    }

    @Override
    public void run() {
      try {
        InputStream input  = socket.getInputStream();
        OutputStream output = socket.getOutputStream();

        AutobidderRequest request = AutobidderRequest.newBuilder().mergeFrom(input)
            .build();
        getResponse(request).writeTo(output);

        output.close();
        input.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    private AutobidderResponse getResponse(AutobidderRequest request) {
      AutobidderResponse.Builder response = AutobidderResponse.newBuilder();
      if (request.getHealth()) {
        response.setHealthy(true);
      } else if (request.getStatus()) {
        stats.populate(response.getStatusBuilder());
      } else if (request.getCompareTripCount() == 2) {
        try {
          populateExplanation(request.getCompareTrip(0),
              response.addScoreExplanationBuilder());
          populateExplanation(request.getCompareTrip(1),
              response.addScoreExplanationBuilder());
        } catch (Exception e) {
          response.setError(e.getMessage());
        }
      }
      return response.build();
    }

    private void populateExplanation(String keyString, ScoreExplanation.Builder builder)
        throws Exception {
      PairingKey key = PairingKey.parse(keyString);
      Trip trip = trips.getTrip(key);
      TripScore score = new TripScore(trip);
      for (String line : score.getScoreExplanation()) {
        builder.addLine(line);
      }
    }
  }
}
