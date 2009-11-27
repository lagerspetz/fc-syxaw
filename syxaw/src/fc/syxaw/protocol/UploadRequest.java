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

// $Id: UploadRequest.java,v 1.13 2005/01/19 16:24:36 ctl Exp $
package fc.syxaw.protocol;

import fc.syxaw.transport.PropertySerializable;

//$EXTDOCDEP
/** The <code>UploadRequest</code> structure.
 * See the Syxaw protocol data structure reference in the
 * <i>"Specification of Middleware Service Set II"</i> document.
 */

public class UploadRequest implements java.io.Serializable, PropertySerializable {

  private String object; // null (absent) object -> batch upload
  private boolean metadata;
  private boolean data;
  private String lock=null;

  /** Create new object.
   *
   */
  public UploadRequest() {
    this(null,false,false,null);
  }

  /** Create new object.
   *
   * @param aObjectId object UID to upload
   * @param aMetadata <code>true</code> if sending metdata
   * @param aData <code>true</code> if sending data
   * @param aLock lock token, or <code>null</code> if none
   */
  public UploadRequest(String aObjectId, boolean aMetadata, boolean aData,
                       String aLock) {
    object = aObjectId;
    metadata = aMetadata;
    data = aData;
    lock = aLock;
  }

  /** Set name of object being downloaded. The value is expressed as the
   * default string
   * representation of a {@link fc.syxaw.fs.UID} */
  public void setObject(String aObjectId) {
    object = aObjectId;
  }

  /** Get name of object being downloaded.
   * @see #setObject
   */
  public String getObject() {
    return object;
  }

  /** Set "uploading metadata for the object" flag. */
  public void setMetadata(boolean aMetadata) {
    metadata = aMetadata;
  }

  /** Get "uploading metadata for the object" flag. */
  public boolean getMetadata() {
    return metadata;
  }

  /** Set "uploading data for the object" flag. */
  public void setData(boolean aData) {
    data = aData;
  }

  /** Get "uploading data for the object" flag. */
  public boolean getData() {
    return data;
  }

  /** Set lock.
   *
   * @param aLock lock token.
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
      new String[]{"data","lock","meta","obj"};

  // FIXME-W: empty lock and obj fields are used with diffrent meanings
  // for "" and null. The \u0000 defaults is a result of this
  // This is potentially dangerous, and should be fixed!
  private static final Object[] PS_DEFAULTS =
      {Boolean.TRUE, "\u0000", Boolean.TRUE, "\u0000"};

  public PropertySerializable.ClassInfo propInit() {
   return new PropertySerializable.ClassInfo("uploadRq",PS_KEYS,PS_DEFAULTS);
  }

  public Object[] propSerialize() {
    return new Object[] {new Boolean(data),lock,new Boolean(metadata),object};
  }

  public void propDeserialize(Object[] vals) {
    data = ((Boolean) vals[0]).booleanValue();
    lock = (String) (vals[1]==PS_DEFAULTS[1] ? null : vals[1]);
    metadata = ((Boolean) vals[2]).booleanValue();
    object = (String) (vals[3]==PS_DEFAULTS[3] ? null : vals[3]);
  }

}
// arch-tag: 1ebc0850472fd553728b138a8395ac92 *-
