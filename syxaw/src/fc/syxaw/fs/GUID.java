/*
 * Copyright 2005--2008 Helsinki Institute for Information Technology
 *
 * This file is a part of Fuego middleware.  Fuego middleware is free
 * software; you can redistribute it and/or modify it under the terms
 * of the MIT license, included as the file MIT-LICENSE in the Fuego
 * middleware source distribution.  If you did not receive the MIT
 * license with the distribution, write to the Fuego Core project at
 * fuego-core-users@googlegroups.com.
 */

// $Id: GUID.java,v 1.22 2004/11/27 19:32:19 ctl Exp $
package fc.syxaw.fs;

import java.io.Serializable;

import fc.syxaw.util.Util;
import fc.util.log.Log;

/** Syxaw object globally unique id (GUID). A Syxaw GUID consists of a
 * location id (LID) and a locally unique id (UID). The concatenation of the
 * LID and the UID delimitered by '<code>/</code>' form the GUID.
 * <p>The division into LID and UID is made for object to device lookup purposes:
 * GUIDs are required to be assigned so that objects sharing the same LID reside
 * on the same device. Furthermore, when allocating GUIDs on a particular
 * device, on should attempt to use as few LIDs as possible. This way, the
 * number of object-to-device lookups is minimized.
 * <p>The GUID objects are immutable.
 */

public class GUID implements Serializable, Cloneable {

  public static final char SEPARATOR = '/';
  private static final String currentLocation = Config.THIS_LOCATION_ID;

  private UID uid;
  private String location;

  /** Create a new GUID.
   *
   * @param location id
   * @param uid UID of object
   */
  public GUID(String location, UID uid) {
    this.location = location;
    this.uid = uid;
  }

  // Create from DID/LID?Query format as stated in spec 4.6.1.
  /** Create a GUID. Equivalent to
   * {@link #GUID(String,String) new GUID(getCurrentLocation(),guid)}.
   * Example input guids:
   * <code>syxaw.hiit.fi/</code> (DBO on <code>syxaw.hiit.fi</code>),
   * <code>syxaw.hiit.fi/affedea0009</code>, <code>A_gge9mxfyyz</code>
   * a GUID for which LID=this host.
   * @param guid GUID string
   */
  public GUID(String guid) {
    this(getCurrentLocation(),guid);
  }

  /** Create a GUID. The guid is created from the LID/UID format, i.e.
   * location id followed by UID. The constructor also accepts a
   * <code>guid</code>
   * without a slash, in which case the LID will be set to
   * <code>defaultLocation</code> and
   * the UID to <code>guid</code>.
   * @param defaultLocation LID to use if <code>guid</code> contains none
   * @param guid GUID string
   */
  public GUID(String defaultLocation,String guid) {
    if( guid == null )
      throw new IllegalArgumentException();
    int slashpos = guid.indexOf(SEPARATOR);
    // 20070524 FIX: If a query is set here, and it contains a forwardslash...
    int qspos = guid.indexOf(SyxawFile.querySeparator);
    if(slashpos == -1 || (qspos > -1 && slashpos > qspos)) {
      location = defaultLocation;
      uid = QueriedUID.createFromBase64Q(guid);
    } else {
      location = guid.substring(0,slashpos);
      uid = QueriedUID.createFromBase64Q(guid.substring(slashpos+1));
    }
  }

  /** Return string representation of GUID. */
  public String toString() {
    return location + SEPARATOR + uid.toBase64();
  }

  public boolean equals(Object o) {
    return o == this || (
        (o instanceof GUID) &&
        Util.equals(((GUID) o).location,location) &&
        Util.equals(((GUID) o).uid,uid));
  }

  public int hashCode() {
    return toString().hashCode();
  }

  /** Get UID part of GUID.
   *
   * @return uid part of GUID
   */
  public UID getUId() {
    return uid;
  }

  public QueriedUID getQueriedUId() {
    if( !(uid instanceof QueriedUID) ) {
      uid = QueriedUID.createFromBytes(uid.getBytes());
    }
    return (QueriedUID) uid;
  }

  /** Get location of GUID.
   *
   * @return location id (LID) of GUID
   */
  public String getLocation() {
    return location;
  }

  /** Get location id of the current location.
   * @return current location, as set by
   * {@link Config#THIS_LOCATION_ID}
   */

  public static String getCurrentLocation() {
    return currentLocation;
  }

  /*
  public Object clone() {
    return new GUID(location,uid);
  }*/
}
// arch-tag: ae21c552c39d8da1daf24b4bfe9ef91e *-
