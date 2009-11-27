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

// $Id: FullMetadata.java,v 1.3 2003/09/17 21:25:41 ctl Exp $
// old file: Metadata.java,v 1.6 2003/06/26 15:13:54 ctl Exp
package fc.syxaw.fs;

import fc.syxaw.api.Metadata;

/** Full metadata for an object. Includes object metadata maintained internally
 * by the file system, in addition to the object metadata that is
 * defined in the synchronization protocol.
 */


public interface FullMetadata extends Metadata {

  /** Set object GUID. */
//NSpublic void setGuid(GUID aGuid);

  /** Get object GUID. */
  public GUID getGuid();

  /** Set the name that this object is linked to */
  //NSpublic void setLink(String aLink);

  /** Get the name that this object is linked to */
  public String getLink();

  /** Get SHA-1 hash of object data. */
  public byte[] getHash();

  /** Set SHA-1 hash of object data. */
 //NSpublic void setHash(byte[] aHash);

  /** Set the data version of the object linked to. A value of
   * {@link Constants#NO_VERSION} means that the object has never been
   * synchronized with its linked object. */
  //NSpublic void setLinkDataVersion(int aVersion);

  /** Get the data version of the object linked to.
   * @see #setLinkDataVersion */
  public int getLinkDataVersion();
  
  /** Set the metadata version of the object linked to. A value of
   * {@link Constants#NO_VERSION} means that the object has never been
   * synchronized with its linked object. */
  //NSpublic void setLinkMetaVersion(int aVersion);

  /** Get the metadata version of the object linked to.
   * @see #setLinkMetaVersion */
  public int getLinkMetaVersion();

  /** Set the data version of object. A value of
   * {@link Constants#NO_VERSION} means that the object has not yet been
   * assigned a version. */

  //NSpublic void setDataVersion(int aVersion);

  /** Get the data version of object.
   * @see #setDataVersion */
  public int getDataVersion();
  
  /** Set the metadata version of object. A value of
   * {@link Constants#NO_VERSION} means that the object has not yet been
   * assigned a version. */

  //NSpublic void setMetaVersion(int aVersion);

  /** Get the metadata version of object.
   * @see #setMetaVersion */
  public int getMetaVersion();

  /** Set object length. */
  //NSpublic void setLength(long aLength);

  /** Get object length. */
  public long getLength();

  //NSpublic void setBranch(String branch);

  public String getBranch();
  
  /** Return a <code>Metadata</code> object for this object. */
  /*  public Metadata asMetadata();
  
  /** Assign this object to a <code>Metdata</code> object. */
  //  public void assignTo( Metadata target );*/
  
  /** Assign a <code>Metdata</code> object to this object. The fields not
   * present in {@link fc.syxaw.api.Metadata} are not affected by
   * the assignment.
   */
  
  //NM  public void assignFrom( Metadata src );
  
}
 // arch-tag: 49f74cfa5f3b5464e3b6ad017f7471f2 *-
