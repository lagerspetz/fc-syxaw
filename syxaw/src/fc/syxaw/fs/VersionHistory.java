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

// $Id: VersionHistory.java,v 1.11 2004/12/29 11:28:18 ctl Exp $
package fc.syxaw.fs;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/** Interface to version history for an object. */

public interface VersionHistory {

  /** Return the most recent assigned version of the object. The object may have
   * been modified since this version number was assigned. */
  // NOTE: Will NOT take into account modflags, i.e. returns last assigned version
  // although the object may have been modified since (=it doesn't have any version yet)
  public int getCurrentVersion();

  /** Retrieve metdata for a given version of this object.
   * @param mdVersion version of object, whose metdata is retrieved
   * @return metadata of the object, or <code>null</code> if no such version of
   * the object in this version history.
   */
  public FullMetadata getMetadata( int mdVersion );

  /** Retrieve data for a given version of this object.
   * @param version version of object which is retrieved
   * @return input stream to the data, or <code>null</code> if no such version of
   * the object in this version history.
   */

  public InputStream getData(int version );

  /** Get previous modification to data. Returns the highest version number
   * less than the given version, for which the data differs from the given version.
   * Informally, we go backwards in the version history until a version with
   * differing data is found.
   * @param version version to find previous data version for
   * @return previous data version, or {@link Constants#NO_VERSION} if there
   * is no previous version, or if <code>version</code> is not in this
   * version history.
   */
  public int getPreviousData( int version);

  /** Get previous modification to metadata. Returns the highest version number
  * less than the given version, for which the metadata differs from that of the
  * given version.
  * Informally, we go backwards in the version history until a version with
  * differing metadata is found.
  * @param mdversion version to find previous metadata version for
  * @return previous metadata version, or {@link Constants#NO_VERSION} if there
  * is no previous version, or if <code>version</code> is not in this
  * version history.
  */

  public int getPreviousMetadata( int mdversion);

  /** Get list of versions in this version history. Versions are sorted, with
   * the lowest version number at position 0. The version hostory does not
   * necessarily form an unbroken chain of version numbers, e.g. [1,2,99,222]
   * is a valid version history. */

  //DISABLE public int[] getVersions();

  //FIXME-P Access to full vefrsion history
  // List<proto.Version>
  public List getFullVersions(String branch);

  public boolean onBranch(); // True if this history is on a branch

  /** Compare two versions for equality. Note that {@link Constants#NO_VERSION}
   * is never equal to any version, including {@link Constants#NO_VERSION}. The
   * comparison may return false negatives (i.e. a return value of
   * <code>false</code> when the data/metadata is indeed identical).
   * @param v1 First version
   * @param v2 Second version
   * @param meta If <code>true</code>, compare metadata for equality, otherwise
   * compare data for equality.
   * @return <code>true</code> if the data/metdata of the two versions is identical.
   */
  
  public boolean versionsEqual(  int v1, int v2, boolean meta );
  
  /** An empty version history. The class implements a version history that
   * is always empty.
   */
  
  public static final VersionHistory EMPTY_HISTORY = new VersionHistory() {
        
    public int getCurrentVersion() {
      return Constants.NO_VERSION;
    }
    
    public FullMetadata getMetadata( int mdVersion ) {
      return null;
    }
    
    public InputStream getData(int version ) {
      return null;
    }
    
    public int getPreviousData( int version) {
      return Constants.NO_VERSION;
    }
    
    public int getPreviousMetadata( int mdversion) {
      return Constants.NO_VERSION;
    }
    
    public boolean versionsEqual(int v1, int v2, boolean meta) {
      return false;
    }
    
    public List getFullVersions(String branch) {
      return Collections.EMPTY_LIST;
    }
    
    public boolean onBranch() {
      return false;
    }
  };
     
}
// arch-tag: 112aa5677ef30e44f2e15f8b0df05ca2 *-
