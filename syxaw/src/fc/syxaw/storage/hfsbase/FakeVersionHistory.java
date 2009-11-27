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

// $Id: FakeVersionHistory.java,v 1.1 2005/06/08 13:14:27 ctl Exp $
// Was previously in storage/xfs as
// Id: FakeVersionHistory.java,v 1.4 2005/06/08 12:46:12 ctl Exp

package fc.syxaw.storage.hfsbase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.proto.Version;
import fc.syxaw.util.Util;
import fc.util.log.Log;


/** "Faked" version history for objects not in any repository. This version
 * history generates a version history consisting of the last assigned
 * version to a given SyxawFile. It allows space-efficient versioned
 * synchronization of objects as long as they have not been concurrently
 * modified.
 * <p>The downside is that in those cases when merge would be needed, the
 * base version will be missing (since we do not keep it anywhere, and the
 * local file will have unknown changes w.r.t. base). This is why we call
 * the version history "faked".
 */

public class FakeVersionHistory implements VersionHistory {

  private int versions[];
  private fc.syxaw.fs.SyxawFile f;
  private boolean link;
  private boolean meta;

  /** Create a new fake version history. The history consists of a
   * single version, which is the current [link] version, if such
   * a version has been assigned.
   *
   * @param f SyxawFile to generate history for.
   * @param link <code>true</code> to generate for the link facet.
   */
  public FakeVersionHistory(fc.syxaw.fs.SyxawFile f, boolean link, boolean meta) {
    this.link = link;
    this.f = f;
    this.meta = meta;
    // Note: we deliberately ignore the modflags here, as we want
    // to lie about having retained the version we previously synced to
    // (we do this to get verrefs for remote data when there are only local
    // mods)
    int ver = getCurrentVersion();
    if ( ver != Constants.NO_VERSION)
      versions = new int[] {
          ver};
    else
      versions = new int[0];
  }

  /** Returns the current version. Returns the
   * version, which is the current [link] version, if such
   * a version has been assigned.
   *
   * @return current version.
   */
  public int getCurrentVersion() {
    try {
      if (meta) {
        int version = link ?
            f.getLinkMetaVersion() ://FIXME-versions:
              f.getFullMetadata().getMetaVersion();
            return version;  
      }else {
        int version = link ?
            f.getLinkDataVersion() ://FIXME-versions:
              f.getFullMetadata().getDataVersion();
            // Outright lie about having the older version -> remote
            // may return a verref to it instead of all data.
            // This is OK, as long as we only have local modifications
            // However, if there is both new local and remote versions
            // we won't be able to do anything but signal a conflict
            // (three-way merge would require the base version).
            return version; /*f.isMetadataModified(link) || f.isDataModified(link) ?
            Constants.NO_VERSION : version;*/
      }
    } catch (FileNotFoundException x) {
      return Constants.NO_VERSION;
    }
  }

  // FIXME-W BUG? check for modflags!
  public FullMetadata getMetadata(int mdVersion) {
    try {
      return f.getFullMetadata();
    } catch (FileNotFoundException x) {
      return null;
    }

  }

  public InputStream getData(int version) {
    try {
      if (Util.arrayLookup(versions, version, version + 1) == -1)
        return null;
      return link ? f.getLinkInputStream() : f.getInputStream();
    } catch (IOException x) {
      return null;
    }
  }

  public int getPreviousData(int version) {
    if (version == Constants.NO_VERSION)
      return Constants.NO_VERSION;
    if (version < Constants.FIRST_VERSION)
      return Constants.ZERO_VERSION;
    else
      return version - 1;
  }

  public int getPreviousMetadata(int mdversion) {
    if (mdversion == Constants.NO_VERSION)
      return Constants.NO_VERSION;
    if (mdversion < Constants.FIRST_VERSION)
      return Constants.ZERO_VERSION;
    else
      return mdversion - 1;
  }

  private int[] getVersions() {
    return versions;
  }

  public boolean versionsEqual(int v1, int v2, boolean meta) {
    return v1 != Constants.NO_VERSION && v1 == v2;
  }

  public List getFullVersions(String sbranch) {
    List vers = new LinkedList();
    for( int i=0;i<versions.length;i++) {
      String branch = null;
      try {
        branch =
                link ? f.getFullMetadata().getBranch() :
                Version.TRUNK;
      } catch (FileNotFoundException ex) {
        Log.log("Missing file",Log.FATALERROR,ex);
      }

      if( sbranch == null || Version.CURRENT_BRANCH.equals(sbranch) ||
         sbranch.equals(branch) )
       vers.add(new Version(versions[i], branch ) );
    }
    return vers;
  }

  public boolean onBranch() {
    String branch = null;
    try {
      branch =
              link ? f.getFullMetadata().getBranch() :
              Version.TRUNK;
    } catch (FileNotFoundException ex) {
      Log.log("Missing file",Log.FATALERROR,ex);
    }
    return !Version.TRUNK.equals(branch);
  }

}
// arch-tag: a8c5c4c14259f9d5f40d2990ded4a6e9 *-
