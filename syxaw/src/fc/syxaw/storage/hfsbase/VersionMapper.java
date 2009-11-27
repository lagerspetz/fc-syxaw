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

package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.UID;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.hierfs.SyxawFile;
import fc.syxaw.proto.Version;
import fc.syxaw.util.Util;
import fc.util.IOUtil;
import fc.util.log.Log;

public class VersionMapper {
  
  StringDb mapDb = null; // FIXME: ObjectDb<SomeClass,Integer> would be better
  
  public VersionMapper(File dbFile) {
    mapDb = new StringDb(dbFile);
  }

  public VersionHistory getLinkVersionHistory(SyxawFile f, 
      VersionHistory localHistory) {
    UID u = f.getUid();
    if( mapDb.lookup(getTopkey(u)) == null )
      return VersionHistory.EMPTY_HISTORY;
    return new MappedHistory(u,localHistory);
  }
  
  public void map(SyxawFile f, int lver, int ver) {
    try {
      UID u = f.getUid();
      String tk = getTopkey(u);
      int entry = 0;
      if( mapDb.lookup(tk) != null ) {
        entry = Integer.parseInt( mapDb.lookup(getTopkey(u)) )+1;
      } 
      Log.debug("Mapping for "+f.getUid()+": (link->local) "+lver+"->"+ver);
      mapDb.insert(getEntryKey(u, entry),  String.valueOf( lver ) );
      //Log.debug("Writing to db: "+getMappedKey(u, lver)+" : "+ver);
      mapDb.insert(getMappedKey(u, lver), String.valueOf( ver ) );
      mapDb.update(tk, String.valueOf(entry));
    } catch (NumberFormatException e) {
      Log.fatal("Cannot map version",e);
    }   
  }
  
  public void clean(SyxawFile f) {
    try {
      UID u = f.getUid();
      String tk = getTopkey(u);
      if( mapDb.lookup(tk) == null )
        return;
      int maxEntry = Integer.parseInt( mapDb.lookup(getTopkey(u)) );
      for(int e=maxEntry;e>=0;e--) {
        int lver = Integer.parseInt( mapDb.lookup(getEntryKey(u, e)) );
        mapDb.delete(getMappedKey(u, lver));
        mapDb.delete(getEntryKey(u, e));
      }
      mapDb.delete(tk);
    } catch (NumberFormatException e) {
      Log.fatal("Cannot clean version map",e);
    }
  }
  
  // uid -> last entry
  private String getTopkey(UID u) {
    return u.toBase64();
  }

  // linkver -> localver
  public String getMappedKey(UID u, int lver) {
    return u.toBase64()+".l."+lver;
  }

  // entry -> linkver
  public String getEntryKey(UID u, int no) {
    return u.toBase64()+".e."+no;
  }

  public class MappedHistory implements VersionHistory {

    UID u;
    VersionHistory mapped;

    public MappedHistory(UID u, VersionHistory mapped) {
      this.u = u;
      this.mapped = mapped;
    }

    public int getCurrentVersion() {
      try {
        int maxEntry = Integer.parseInt( mapDb.lookup(getTopkey(u)) );
        String currentL = mapDb.lookup(getEntryKey(u, maxEntry));
        return Integer.parseInt(currentL);
      } catch (NumberFormatException e) {
        Log.fatal(e);
      }
      //A! assert false;
      return Constants.NO_VERSION;
    }

    public FullMetadata getMetadata(int mdVersion) {
      return mapped.getMetadata(getMappedVersion(mdVersion));
    }

    public InputStream getData(int version) {
      return mapped.getData(getMappedVersion(version));
    }

    public int getPreviousData(int versionL) {
      return getPrevious(versionL,false);
    }

    public int getPreviousMetadata(int mdversionL) {
      return getPrevious(mdversionL,true);
    }

    public List getFullVersions(String branch) {
      //A! assert branch == null || Version.CURRENT_BRANCH.equals(branch) ||
      //A!  Version.TRUNK.equals(branch) : "Other branch "+branch+" not coded.";
      LinkedList /*!5 <Version> */ verlist = new LinkedList /*!5 <Version> */();
      Set mappedVers = new HashSet();
      mappedVers.addAll(mapped.getFullVersions(branch));
      Log.debug("Set of local vers for "+u,mappedVers);
      int maxKey = Integer.parseInt( mapDb.lookup(getTopkey(u)) );
      for( int v = maxKey;v>=0;v--) {
        int linkVer = Integer.parseInt( mapDb.lookup( getEntryKey(u, v) ) );
        int ver = getMappedVersion(linkVer);
        //Log.debug("Trying linkver "+linkVer+" which maps to "+ver);
        if( !mappedVers.contains(new Version(ver,Version.TRUNK)))
          continue; // Not in current mapped history, although we can map it
        verlist.addFirst(new Version(linkVer,Version.TRUNK));
      }
      Log.debug("List of linkvers",verlist);
      return verlist;
    }

    public boolean onBranch() {
      return mapped.onBranch();
    }

    public boolean versionsEqual(int v1L, int v2L, boolean meta) {
      return mapped.versionsEqual(getMappedVersion(v1L),
          getMappedVersion(v2L), meta);
    }

    protected int getPrevious(int versionL, boolean meta) {
      int pvL = versionL-1;
      int version = getMappedVersion(versionL);
      // Scan back until inequality
      for(;pvL>Constants.FIRST_VERSION-2 &&
        mapped.versionsEqual( getMappedVersion( pvL ), version, meta);pvL--)
        ;
      return pvL < Constants.FIRST_VERSION ? Constants.FIRST_VERSION : pvL;
    }

    protected int getMappedVersion(int lver) {
      String ver = mapDb.lookup(getMappedKey(u,lver));
      //Log.debug("Reading from db: "+getMappedKey(u,lver),ver);
      try {
        if( ver == null )
          return Constants.NO_VERSION;
        return Integer.parseInt(ver);
      } catch (NumberFormatException e) {
        Log.fatal(e);
      }
      //A! assert false;
      return Constants.NO_VERSION;
    }
  }

  /** Versioned directory tree maintenance routines. */
  
  public static class Maintenance {

    public static void clean(File root) {
      try {
        if( !root.exists() )
          return;
        if( !root.isDirectory() )
          Log.log("Versionmapper location (now " + root
              + ") must be a directory", Log.FATALERROR);
        IOUtil.delTree(root);
      } catch( IOException x) {
        Log.log("Cleaning failed while deleting tree "+root,Log.FATALERROR);
      }
    }

    /** Initialize a new versioned directory tree.
     *
     * @param root directory the tree will be stored in
     */
    public static void init(File root) {
      if( !root.exists() && !root.mkdir() )
        Log.log("Can't create "+root,Log.FATALERROR);
    }
  }


}

// arch-tag: 26ee1916-7c55-4933-9e84-4f5ada5aa4b9

