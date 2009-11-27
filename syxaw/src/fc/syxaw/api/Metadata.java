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

// $Id: Metadata.java,v 1.5 2004/11/19 09:31:08 ctl Exp $
package fc.syxaw.api;

import java.io.Serializable;

import fc.dessy.indexing.Keywords;

/** Metadata for files on a Syxaw file system.
 */

public interface Metadata extends Serializable {
    public Keywords getKeywords();	
    
//NM    public void setKeywords(Keywords keywords);
	
  /** Set readonly flag. */
//NM  public void setReadOnly(boolean aReadOnly);

  /** Get readonly flag */
  public boolean getReadOnly();

//NM  public void setJoins(String aFormat);

  public String getJoins();

  /** Set type of file. The type field is used by the file system
   * to obtain appropriate merging and delta encoding algorithms for the
   * file. Typically MIME content type names are used. Directories always
   * have the type <code>xml/syxaw-dirtree</code>. */

//NM  public void setType(String aType);

  /** Get type of file. */
  public String getType();

  /** Set time of file modification.
   * @param aModTime The new last-modified time, measured in milliseconds
   * since the epoch (00:00:00 GMT, January 1, 1970)
   * @see #setMetaModTime
   */
//NM  public void setModTime(long aModTime);

  /** Get time of file modification.
   * @return A long value representing the time the file was last modified,
   * measured in milliseconds since the epoch (00:00:00 GMT, January 1, 1970)
   */
  public long getModTime();

  /** Set time of file metadata modification. The metadata of a file
   * can be modified without modifying the file's contents. However, if the content of
   * the file is modified, it's metadata has been modified as well.
   * @param aMetaModTime The new metadata last-modified time, measured in milliseconds
   * since the epoch (00:00:00 GMT, January 1, 1970)
   */

//NM  public void setMetaModTime(long aMetaModTime);

  /** Get time of file metadata modification.
   * @return A long value representing the time the metadata of the file was last modified,
   * measured in milliseconds since the epoch (00:00:00 GMT, January 1, 1970)
   */
  public long getMetaModTime();
  
}
// arch-tag: 714c307d9d1653034c2e1c38abe0d58a *-
