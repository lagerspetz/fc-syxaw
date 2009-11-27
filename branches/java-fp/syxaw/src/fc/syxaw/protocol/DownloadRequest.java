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

// $Id: DownloadRequest.java,v 1.13 2005/01/19 16:24:32 ctl Exp $
package fc.syxaw.protocol;

import fc.syxaw.transport.PropertySerializable;

//$EXTDOCDEP
/** The <code>DownloadRequest</code> structure.
 * See the Syxaw protocol data structure reference in the
 * <i>"Specification of Middleware Service Set II"</i> document.
 */

public class DownloadRequest implements java.io.Serializable,
    PropertySerializable {
  private String object;
  private int[] versionsAvailable;
  private boolean metadata;
  private boolean data;
  private boolean lockRequested;
  // Multi-object download
  private DownloadRequest[] objects;

  /** Create empty object. */
  public DownloadRequest() {
    this(null, null, false, false, false, null);
  }

  /** Create request
   *
   * @param aObjectId object to request
   * @param aVersionsAvailable versions locally available
   * @param aMetadata <code>true</code> to get metadata
   * @param aData <code>true</code> to get data
   * @param aRequestLock <code>true</code> to request a lock
   * @param abatch DownloadRequest[] batch of requests
   */
  public DownloadRequest(String aObjectId, int[] aVersionsAvailable,
                         boolean aMetadata, boolean aData, boolean aRequestLock,
                         DownloadRequest[] abatch) {
    object = aObjectId;
    versionsAvailable = aVersionsAvailable;
    metadata = aMetadata;
    data = aData;
    objects = abatch;
    lockRequested = aRequestLock;
  }

  /** Set name of object being downloaded. The value is expressed as the
   * default string representation of a {@link fc.syxaw.fs.UID} */

  public void setObject(String aObjectId) {
    object = aObjectId;
  }

  /** Get name of object being downloaded.
   * @see #setObject
   */

  public String getObject() {
    return object;
  }

  /** Set list of versions of the object available on the device
   * making the download request.
   */

  public void setVersionsAvailable( int[] aVersionsAvailable ) {
    versionsAvailable=aVersionsAvailable;
  }

  /** Get list of versions of the object available on the device
   * making the download request.
   */
  public int[] getVersionsAvailable() {
    return versionsAvailable;
  }

  /** Set "Request metadata for the object" flag. */
  public void setMetadata(boolean aMetadata) {
    metadata = aMetadata;
  }

  /** Get "Request metadata for the object" flag. */
  public boolean getMetadata() {
    return metadata;
  }

  /** Set "Request data for the object" flag. */
  public void setData(boolean aData) {
    data = aData;
  }

  /** Get "Request data for the object" flag. */
  public boolean getData() {
    return data;
  }

  /** Set batch request.
   *
   * @param aObjects array of requests in the batch
   */
  public void setObjects(DownloadRequest[] aObjects) {
    objects = aObjects;
  }

  /** Get batch request.
   *
   * @return array of requests in the batch
   */
  public DownloadRequest[] getObjects() {
    return objects;
  }

  /** Request lock flag.
   *
   * @param val <code>true</code> to request lock
   */
  public void setLockRequested(boolean val) {
    lockRequested = val;
  }

  /** Get request lock flag.
   *
   * @return <code>true</code> if lock requested.
   */
  public boolean getLockRequested() {
    return lockRequested;
  }

  // PropertySerializable interface

  private static final String[] PS_KEYS =
      new String[]{"data","lock","meta","obj","objs","known"};
  private static final Object[] PS_DEFAULTS =
      {Boolean.TRUE, Boolean.FALSE, Boolean.TRUE, "", new DownloadRequest[] {},
      new int[0] };

  public ClassInfo propInit() {
   return new PropertySerializable.ClassInfo("download",PS_KEYS,PS_DEFAULTS);
  }

  public Object[] propSerialize() {
    return new Object[]
        {new Boolean(data), new Boolean(lockRequested), new Boolean(metadata),
        object, objects, versionsAvailable};
  }

  public void propDeserialize(Object[] vals) {
    data = ((Boolean) vals[0]).booleanValue();
    lockRequested = ((Boolean) vals[1]).booleanValue();
    metadata = ((Boolean) vals[2]).booleanValue();
    object = (String) vals[3];
    objects = (DownloadRequest[])
        (vals[4] == PS_DEFAULTS[4] ? null : vals[4]);
      versionsAvailable = (int[]) (vals[5] == PS_DEFAULTS[5] ? null :
                                   vals[5]);
  }

}
// arch-tag: 9f30bd391a30acc1d6ca7d346bf57be2 *-
