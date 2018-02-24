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

package crewtools.flica.pojo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;

import crewtools.flica.bid.Transition;

public class OpentimeRequest {
  public static final String APPROVED = "Approved";
  public static final String DENIED = "Denied";
  public static final String PROCESSING = "Processing";
  public static final String PENDING = "Pending";  // guess
  
  //"",0,"","","",0,0,'',0) );
  private final String requestId;  // 01D385A4:CC126379
  private final String requestDateAndTime;  // 04 JAN 16:41
  private final int act;  // ? 4
  private final List<String> addTrips;  // ['L7101:20180120']
  private final List<String> dropTrips;  // ['L2062:20180119']
  private final List<String> pArySplit;  // ? [''] pAry is addTrips
  private final List<String> epArySplit;  // ? [''] epAry is dropTrips
  private final int tgt;  // ? 0
  private final String status;  // "Approved"
  private final String cmt;  // comment?
  private final String cm_cmt;  // comment?
  private final int priority;  // 1
  private final boolean isDeleted;
  private final String processedDateAndTime;  // 04 JAN 16:42 
  private final boolean cnf;  // false
  private final String txn;  // ""
  private final int split;  // 0
  private final String UNUSED_dependencies;  // ""
  private final String AOTS; // ""
  private final String matchcmt; // ""
  private final int tbreqid;  // 0
  private final int yyyymmdd;  // 0
  private final String bidservid;  // ""
  private final int tbrespid;  // 0
  
  private final Transition transition;
  
  private static final Splitter PARAMETER_SPLITTER = 
      Splitter.on(Pattern.compile("\\['|','|'\\]")).omitEmptyStrings().trimResults();
  
  public OpentimeRequest(Iterator<String> input) {
    requestId = input.next();
    requestDateAndTime = input.next();
    act = Integer.parseInt(input.next());
    addTrips = PARAMETER_SPLITTER.splitToList(input.next());
    dropTrips = PARAMETER_SPLITTER.splitToList(input.next());
    pArySplit = PARAMETER_SPLITTER.splitToList(input.next());
    epArySplit = PARAMETER_SPLITTER.splitToList(input.next());
    tgt = Integer.parseInt(input.next());
    status = input.next();
    cmt = input.next();
    cm_cmt = input.next();
    priority = Integer.parseInt(input.next());
    isDeleted = Boolean.parseBoolean(input.next());
    processedDateAndTime = input.next();
    cnf = Boolean.parseBoolean(input.next());
    txn = input.next();
    split = Integer.parseInt(input.next());
    UNUSED_dependencies = input.next();
    AOTS = input.next();
    matchcmt = input.next();
    tbreqid = Integer.parseInt(input.next());
    yyyymmdd = Integer.parseInt(input.next());
    bidservid = input.next();
    tbrespid = Integer.parseInt(input.next());
    
    transition = new Transition(convert(addTrips), convert(dropTrips));
  }
  
  public String getRequestId() {
    return requestId;
  }
  
  public Transition getTransition() {
    return transition;
  }
  
  private static final Splitter COLON_SPLITTER = Splitter.on(":");
  private static final DateTimeFormatter ALL_NUMERIC = 
      DateTimeFormat.forPattern("yyyyMMdd");
  
  private List<PairingKey> convert(List<String> rawKeys) {
    // raw key of form L7101:20180120
    List<PairingKey> result = new ArrayList<>();
    for (String key : rawKeys) {
      Iterator<String> parts = COLON_SPLITTER.split(key).iterator();
      String pairingName = parts.next();
      LocalDate date = ALL_NUMERIC.parseLocalDate(parts.next());
      result.add(new PairingKey(date, pairingName));
    }
    return result;
  }
  
  public String getStatus() {
    return status;
  }

  @Override
  public boolean equals(Object that) {
    if (that == null) { return false; }
    if (!(that instanceof OpentimeRequest)) { return false; }
    return ((OpentimeRequest) that).requestId.equals(requestId);
  }

  @Override
  public int hashCode() {
    return requestId.hashCode();
  }
  
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("requestId", requestId)
        .add("addTrips", addTrips)
        .add("dropTrips", dropTrips)
        .add("priority", priority)
        .add("status", status)
        .toString();
  }
}
