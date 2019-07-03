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

package crewtools.util;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class Notifier {
  private final Logger logger = Logger.getLogger(Notifier.class.getName());

  private final InternetAddress from;
  private final InternetAddress to;

  public Notifier(String from, String to) throws AddressException {
    this.from = new InternetAddress(from);
    this.to = new InternetAddress(to);
  }

  public void notify(String subject, String message) {
    Properties props = new Properties();
    props.put("mail.smtp.host", "localhost");
    Session session = Session.getDefaultInstance(props, null);

    // Construct the message
    Message msg = new MimeMessage(session);
    try {
      msg.setFrom(from);
      msg.setRecipient(Message.RecipientType.TO, to);
      msg.setSubject(subject);
      msg.setText(message);
      Transport.send(msg);
    } catch (MessagingException e) {
      logger.log(Level.WARNING, "Unable to notify", e);
    }
  }
}
