/**
 * Copyright 2019 Iron City Software LLC
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

package crewtools.flica.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import crewtools.rpc.Proto.ReportRequest;

public class ReportServer {
  private final Logger logger = Logger.getLogger(ReportServer.class.getName());

  public static final int PORT = 8423;
  private static final int BACKLOG = 100;

  private final ServerSocket serverSocket;

  public static void main(String args[]) throws Exception {
    new ReportServer().run();
  }

  public ReportServer() throws UnknownHostException, IOException {
    this.serverSocket = new ServerSocket(PORT, BACKLOG, InetAddress.getLocalHost());
  }

  public void run() {
    logger.info("Listening on " + PORT);
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
    private final Socket socket;

    public WorkerRunnable(Socket socket) {
      this.socket = socket;
    }

    @Override
    public void run() {
      try (
        InputStream input = socket.getInputStream();
        OutputStream output = socket.getOutputStream();
      ) {
        ReportRequest request = ReportRequest.newBuilder().mergeFrom(input).build();
        switch (request.getRequestType()) {
          case OPEN_DUTY_PERIOD_DISCREPANCY_REPORT:
            new OpenDutyPeriodDiscrepancyReportAdapter(request, output).run();
            break;
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
