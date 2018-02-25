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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.joda.time.YearMonth;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;

import com.google.common.io.Files;

import crewtools.flica.Proto;
import crewtools.flica.adapters.PairingAdapter;
import crewtools.flica.parser.AlertEmailParser;
import crewtools.flica.pojo.Trip;

public class FlicaMessageHandler implements MessageHandler {
  private final Logger logger = Logger.getLogger(FlicaMessageHandler.class.getName());
  private final MessageContext context;
  private final AlertEmailParser parser;  
  private final BlockingQueue<Trip> queue;
  private final PairingAdapter pairingAdapter;
  private final RuntimeStats stats;

  private static final String TEXT_HTML_MIME_TYPE = "text/html";

  public FlicaMessageHandler(MessageContext context, YearMonth yearMonth, BlockingQueue<Trip> queue,
      RuntimeStats stats) {
    this.context = context;
    this.parser = new AlertEmailParser(yearMonth);
    this.queue = queue;
    this.pairingAdapter = new PairingAdapter();
    this.stats = stats;
  }
  
  @Override
  public void data(InputStream inputStream) throws RejectException, TooMuchDataException, IOException {
    String text = null;
    try {
      text = getTextHtmlBodyPart(inputStream);
      if (text == null) {
        logger.warning("Message was empty?");
        return;
      }
      for (Proto.Trip tripProto : parser.parse(text)) {
        Trip trip = pairingAdapter.adaptTrip(tripProto);
        logger.info("Adding " + trip.getPairingKey() + " from email");
        stats.incrementEmailTrip();
        queue.put(trip);
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error parsing or queueing", e);
      Files.write(text, new File("/opt/autobidder/deadletter/" +
          text.hashCode() + ".txt"), StandardCharsets.UTF_8);
    }
  }
  
  private String getTextHtmlBodyPart(InputStream inputStream) throws MessagingException, IOException {
    Properties properties = new Properties();
    // properties.put("mail.debug", "true");
    MimeMessage message = new MimeMessage(
        Session.getDefaultInstance(properties), inputStream);
    return findTextHtmlRecursively(message.getContent(), 1, false);
  }
  
  private String findTextHtmlRecursively(Object object, int level, boolean isTextHtml) throws MessagingException, IOException {
    if (object instanceof Part) {
      boolean isPartTextHtml = false;
      if (((Part) object).isMimeType(TEXT_HTML_MIME_TYPE)) {
        isPartTextHtml = true;
        logger.finest("found text/html");
      }
      if (object instanceof BodyPart) {
        logger.finest("Level " + level + " is body part");
        return findTextHtmlRecursively(
            ((BodyPart) object).getContent(), 
            level + 1,
            isPartTextHtml);
      }
      if (object instanceof Message) {
        logger.finest("Level " + level + " is message");
        return findTextHtmlRecursively(
            ((Message) object).getContent(), 
            level + 1,
            isPartTextHtml);
      } else {
        logger.severe("Unrecognized part: " + object);
        return null;
      }
    } else if (object instanceof InputStream) {
      logger.severe("Unable to parse InputStream");
      return null;
    } else if (object instanceof String) {
      logger.finest("Found string, is text/html? " + isTextHtml);
      if (isTextHtml) {
        return (String) object;
      } else {
        return null;
      }
    } else if (object instanceof MimeMultipart) {
      // Why doesn't Multipart implement Part?
      for (int i = 0; i < ((Multipart) object).getCount(); ++i) {
        String result = 
            findTextHtmlRecursively(
                ((Multipart) object).getBodyPart(i),
                level + 1,
                false);
        if (result != null) {
          // short circuit
          return result;
        }
      }
      return null;
    } else {
      logger.severe("Unrecognized object: " + object);
      return null;
    }
  }
  
  @Override
  public void done() {
    // shazam
  }

  @Override
  public void from(String arg0) throws RejectException {
  }

  @Override
  public void recipient(String arg0) throws RejectException {
  }
}
