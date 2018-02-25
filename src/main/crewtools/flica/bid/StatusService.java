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
import java.nio.charset.StandardCharsets;

public class StatusService extends Thread {
  private final ServerSocket serverSocket;
  private final int PORT = 8422;
  private final int BACKLOG = 100;
  private final RuntimeStats stats;

  public StatusService(RuntimeStats stats) throws UnknownHostException, IOException {
    this.stats = stats;
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

        output.write(stats.toString().getBytes(StandardCharsets.UTF_8));

        output.close();
        input.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
