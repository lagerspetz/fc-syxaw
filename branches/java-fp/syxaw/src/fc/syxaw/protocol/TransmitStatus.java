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

// $Id: TransmitStatus.java,v 1.15 2005/01/20 10:59:05 ctl Exp $

package fc.syxaw.protocol;

import java.io.Serializable;

import fc.syxaw.api.StatusCodes;
import fc.syxaw.fs.Constants;
import fc.syxaw.transport.PropertySerializable;

//$EXTDOCDEP

/** The <code>TransmitStatus</code> structure.
 * See the Syxaw protocol data structure reference in the
 * <i>"Specification of Middleware Service Set II"</i> document.
 */

public class TransmitStatus implements Serializable, StatusCodes,
    PropertySerializable {

  private String object=null;
  private int status;
  private int version;
  private int[] versionsAvailable;
  private String lock=null;

  /** Create new object.
   *
   */
  public TransmitStatus() {
    this( NO_STATUS, Constants.NO_VERSION );
  }

  /** Create new object.
   *
   * @param aStatus initial status
   * @param aVersion version
   */
  public TransmitStatus(int aStatus, int aVersion ) {
    this(aStatus,aVersion,null);
  }

  /** Create new object.
   *
   * @param aStatus initial status
   * @param aVersion version
   * @param aObject object UID
   */
  public TransmitStatus(int aStatus, int aVersion, String aObject ) {
    status = aStatus;
    version = aVersion;
    object = aObject;
  }

  /** Set status code. Allowable values are defined in the
   * {@link fc.syxaw.api.StatusCodes} interface. */
  public void setStatus(int aStatus) {
    status = aStatus;
  }

  /** Get status code.
   * @see #setStatus */
  public int getStatus() {
    return status;
  }

  /** Set version number. The version number is either the version of the
   * object downloaded, or the version assigned (if accepted) to the object
   * that was uploaded. */
  public void setVersion(int aVersion) {
    version = aVersion;
  }

  /** Get version number.
   * @see #setVersion */
  public int getVersion() {
    return version;
  }

  /** Set array of versions available. The array lists which versions of the
   * transferred object exist on the device sending the transmit status.
   * May not include all versions, and may be left empty. Used when establishing
   * a base version for delta encoding.
   */

  public void setVersionsAvailable(int[] aVersionsAvailable) {
    versionsAvailable = aVersionsAvailable;
  }

  /** Get array of versions available.
   * @see #setVersionsAvailable
   */
  public int[] getVersionsAvailable() {
    return versionsAvailable;
  }

  /** Set object the status pertains to.
   *
   * @param aObject String
   */
  public void setObject(String aObject) {
    object = aObject;
  }

  /** Get object the status pertains to.
   *
   * @return object UID, or <code>null</code> if none
   */

  public String getObject() {
    return object;
  }

  /** Set lock.
   *
   * @param aLock lock token
   */
  public void setLock(String aLock) {
    lock = aLock;
  }

  /** Get lock.
   *
   * @return lock token
   */
  public String getLock() {
    return lock;
  }

  // PropertySerializable interface

  private static final String[] PS_KEYS =
      new String[]{"lock","obj","stat","ver","versAvail"};
  private static final Object[] PS_DEFAULTS =
      {"","",new Integer(TransmitStatus.OK),new Integer(Constants.NO_VERSION),
       new int[] {}};

  public PropertySerializable.ClassInfo propInit() {
   return new PropertySerializable.ClassInfo("stat",PS_KEYS,PS_DEFAULTS);
  }

  public Object[] propSerialize() {
    return new Object[] {lock,object,new Integer(status), new Integer(version),
     versionsAvailable};
  }

  public void propDeserialize(Object[] vals) {
    lock = (String) (vals[0]==PS_DEFAULTS[0] ? null : vals[0]);
    object = (String) vals[1];
    status = ((Integer) vals[2]).intValue();
    version = ((Integer) vals[3]).intValue();
    versionsAvailable = (int []) (vals[4]==PS_DEFAULTS[4] ? null : vals[4]);
  }

  public void assignTo(TransmitStatus target) {
    target.setLock(getLock());
    target.setObject(getObject());
    target.setStatus(getStatus());
    target.setVersion(getVersion());
    target.setVersionsAvailable(getVersionsAvailable());
  }

}
// arch-tag: 0dd4e5d1b2c303a85ddce9ac3033e1c5 *-
