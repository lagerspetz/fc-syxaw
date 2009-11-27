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

// $Id: VersionDB.java,v 1.35 2004/12/02 11:01:05 ctl Exp $
package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.FullMetadataImpl;
import fc.syxaw.fs.GUID;
import fc.syxaw.fs.QueriedUID;
import fc.syxaw.fs.UID;
import fc.syxaw.proto.NotInPrototypeException;
import fc.syxaw.proto.Version;
import fc.syxaw.util.Cache;
import fc.syxaw.util.LruCache;
import fc.syxaw.util.Util;
import fc.util.StringUtil;
import fc.util.log.Log;

/** Database storing old versions of files and their metadata. The class
 * implements as simple
 * database that stores versions of data and metadata.
 *  The database is stored in the directory given by
 * {@link fc.syxaw.storage.hfsbase.Config#VERSION_FOLDER}.
 * <p>Implementation note: the database entries for the versions of
 * an object with GUID <i>g</i> consists of entries
 * (<i>g</i>,version,data,metadata). Each such entry is stored
 * on the file system as two files: <i>g'</i><code>,d</code><i>v</i>,
 * which stores the data, and
 * <i>g'</i><code>,m</code><i>v</i>, which stores the metadata.
 * <i>g'</i> is the
 * file name determined by
 * <code>g.getUId().toBase64()+"@"+g.getLocation()</code>
 * and <i>v</i> is the version number.
 * <p>To optimize storage, the data file for a given version <i>v</i> may be missing.
 * In this case we assume <i>g'</i><code>,d</code><i>v</i> is equal to the
 * existing file <i>g'</i><code>,d</code><i>v'</i>,
 * where <i>v'</i> is the highest version &lt;<i>v</i>.
 * <p>The contents of a sample version database is listed below:<pre>
 alchAABXRJUmkqG7@localhost,d1000
 alchAABXRJUmkqG7@localhost,d1001
 alchAABXRJUmkqG7@localhost,d1003
 alchAABXRJUmkqG7@localhost,m1000
 alchAABXRJUmkqG7@localhost,m1001
 alchAABXRJUmkqG7@localhost,m1002
 alchAABXRJUmkqG7@localhost,m1003
</pre>
 * In this case, the database stores versions 1000&emdash;1003 for the GUID
 * <code>localhost/alchAABXRJUmkqG7</code>. The data for version 1002 is equal to the
 * data for version 1001, and hence there is no file
 * <code>alchAABXRJUmkqG7@localhost,d1002</code>
 * <p>Needless to say, this is a very slow implementation. But it's
 * easy to hack with!
 */


public class VersionDB {

  // Impl note: 1) new data ver -> new metaver
  //            2) for a given data ver x, the metaver is x

  private static final String META_SUFFIX = ",m";
  private static final String DATA_SUFFIX = ",d";

  protected static final int LISTCACHE_SIZE = 64;

  Cache listCache = new LruCache(Cache.TEMP_MED,LISTCACHE_SIZE);

  File root;

  public VersionDB(File aRoot) {
    if( !aRoot.exists() && !aRoot.isDirectory())
      Log.log("No version DB folder/is file " +
              aRoot, Log.FATALERROR);
    root = aRoot;
  }

  /** Commit object to version database, and automatically assign version number.
   * @see #commit(GUID,FullMetadata,InputStream,int)
   */

  public synchronized FullMetadata commit( GUID id, FullMetadata md, InputStream is ) {
    return doCommit(id,md,is,Constants.NO_VERSION);
  }

  /** Commit object to version database.
   *
   * @param id GUID of object
   * @param md Metadata to commit. Upon return, the <code>version</code> and
   * <code>hash</code> fields have been filled in, with the assigned version
   * number and hash of the data (hash=<code>null</code> if no data).
   * @param is InputStream to data, or <code>null</code> if no data to commit.
   * @param newVersion version number to to assign to the object. Must be
   * larger than any version number for the object that exists in the database.
   * {@link fc.syxaw.fs.Constants#NO_VERSION} means that the version
   * number should be automatically assigned.
   */

  public synchronized FullMetadata commit( GUID id, FullMetadata md, InputStream is,
                                   int newVersion ) {
    return doCommit(id,md,is,newVersion);
  }

  // new md is stored in md passed as arg
  // if is = null, only store md; In this case hash in md must be valid!
  protected FullMetadata doCommit( GUID id, FullMetadata mdRo, InputStream is,
                            int requestedVersion) {
    FullMetadataImpl md = FullMetadataImpl.createFrom(mdRo);
    String key = guidToFile(id);
    VersionList l = getVersionList(key);
    int newVersion =  l.allocVersion(requestedVersion, is != null,
                      md.getDataVersion() + 1 > Constants.FIRST_VERSION ?
                      md.getDataVersion() +1 : Constants.FIRST_VERSION);

    File mdFile = new File( root, key + META_SUFFIX + newVersion );
    byte[] hash = null;
    try {
      if (is != null) {
        File dFile = new File(root, key + DATA_SUFFIX + newVersion);
        FileOutputStream fos = new FileOutputStream(dFile);
        hash = fc.syxaw.util.Util.copyAndDigestStream(is, fos );
        fos.close();
      } else {
        hash = md.getHash();
      }
      md.setDataVersion(newVersion);
      md.setHash(hash);
      Util.writeObjectAsXML(mdFile, md );
      return md;
    } catch (IOException x ) {
      Log.log("Commit failed, x="+x,Log.FATALERROR);
      return mdRo;
    }
  }

  /** Get metdata for version.
    * @param id object to retrieve metadata for
    * @param version version to retrieve
    * @return object metadata, or <code>null</code> if not in repository
    */

  public synchronized FullMetadata getMetadata(GUID id, int version ) {
    String key = guidToFile(id);
    File mdFile = new File( root, key + META_SUFFIX + version );
    if( !mdFile.exists() )
      return null; // No such version
    FullMetadata md = new fc.syxaw.fs.FullMetadataImpl();
    try {
      Util.readObjectAsXML(mdFile, md);
    } catch (IOException x ) {
      Log.log("cannot get-md for "+key+", ver="+version,Log.FATALERROR);
    }
    return md;
  }

  /** Get data for version.
    * @param id object to retrieve data for
    * @param version version to retrieve
    * @return object data, or <code>null</code> if not in repository
    */

  public  synchronized InputStream getData(GUID id, int version ) {
    String key = guidToFile(id);
    File dFile = new File( root, key + DATA_SUFFIX + version );
    if( !dFile.exists() )
      return null; // No such version
    try {
      return new FileInputStream(dFile);
    } catch (IOException x ) {
      Log.log("cannot get-data for "+key+", ver="+version,Log.FATALERROR);
    }
    return null;
  }

  /** Get current (last assigned) version number for object. */
  protected synchronized int getCurrentVersion(GUID id) {
    VersionList list = getVersionList(id);
    return list.lastMetaVersion;
  }


  /** Get previous version with differing data.
   * Returns the highest version number <i>v</i>, whose data differs from the
   * reference version <i>r</i>, i.e. the data of versions <i>v+1</i>,..,<i>r</i>
   * is equal.
   * @param id object in repository
   * @param version reference version <i>r</i>.
   * @return version of previous data,
   * or {@link fc.syxaw.fs.Constants#NO_VERSION} if no previous data
   * (which <b>does not</b> imply that <i>r</i> is the first version in the repository!).
   */
  protected synchronized  int getPreviousData(GUID id, int version) {
    VersionList list = getVersionList(id);
    return list.getPreviousData(version);
  }

  /** Get previous version with differing metadata.
   * Returns the highest version number <i>v</i>, whose metadata differs from the
   * reference version <i>r</i>, i.e. the metadata of versions <i>v+1</i>,..,<i>r</i>
   * is equal.
   * @param id object in repository
   * @param mdversion reference version <i>r</i>.
   * @return version of previous metadata,
   * or {@link fc.syxaw.fs.Constants#NO_VERSION} if no previous metadata
   * (which <b>does not</b> imply that <i>r</i> is the first version in the repository!).
   */

  protected  synchronized int getPreviousMetadata(GUID id, int mdversion) {
    VersionList list = getVersionList(id);
    return list == null ? Constants.NO_VERSION :
          list.getPreviousMetadata(mdversion);
  }

  /** Get versions in repository.
   *
   * @param id object to retrieve versions for
   * @return array of versions, sorted in ascending order.
   */
  protected  synchronized int[] getRepositoryVersions(GUID id) {
    VersionList list = getVersionList(id);
    return list != null ? list.getRepositoryVersions() : null;
  }

  /** Get version history for object. */
  public fc.syxaw.fs.VersionHistory getHistory( GUID g ) {
    return new VersionHistoryImpl(g);
  }


  private VersionList getVersionList( GUID id ) {
    return getVersionList(guidToFile(id));
  }

  private VersionList getVersionList( String key ) {
    VersionList list = (VersionList) listCache.get(key);
    if( list == null ) {
      list = new VersionList(root,key);
      listCache.put(key,list);
    }
    return list;
  }

  private static class VersionList {
    final String key;
    int lastMetaVersion = Constants.NO_VERSION;
    int lastDataVersion = Constants.NO_VERSION;
    SortedMap versionList; // Entries = (metaver,dataver)

    public VersionList(File dir, String aEntry ) {
      key = aEntry;
      String[] entries = dir.list(new FilenameFilter() {
        public boolean accept( File f, String s ) {
          return s.startsWith(key);
        }
      });
      versionList = new TreeMap();

      // build list of metavers
      String metaPrefix = key + META_SUFFIX;
      for( int i=0;i<entries.length;i++) {
        if( entries[i].startsWith(metaPrefix) ) {
          Integer version =
              new Integer(entries[i].substring(metaPrefix.length()));
          versionList.put( version, null);
        }
      }

      // put in data versions
      String dataPrefix = key + DATA_SUFFIX;
      for( int i=0;i<entries.length;i++) {
        if( entries[i].startsWith(dataPrefix) ) {
          Integer version = new Integer(entries[i].substring(dataPrefix.length()));
          versionList.put(version, version);
        }
      }

      // Associate unmarked metavers with correct dataver
      Integer lastVer = null;
      for( Iterator i = versionList.entrySet().iterator();i.hasNext();) {
        SortedMap.Entry e = (SortedMap.Entry) i.next();
        Integer val = (Integer) e.getValue();
        lastVer = (Integer) e.getKey();
        if( val != null )
          lastDataVersion = val.intValue();
        else {
          if( lastDataVersion == Constants.NO_VERSION ) Log.log("Meta w/o data",Log.FATALERROR);
          e.setValue(new Integer(lastDataVersion));
        }
      }
      if( lastVer == null ) {
        // Empty store
        lastDataVersion = Constants.NO_VERSION;
        lastMetaVersion = Constants.NO_VERSION;
      } else
        lastMetaVersion = lastVer.intValue();
      Log.log("Ver list for "+key+":\nlastVer"+lastMetaVersion+", verlist=\n"+
              versionList.toString(),Log.INFO);
    }

    public int allocVersion(int requestedVersion, boolean allocData, int first) {
      if( requestedVersion == Constants.NO_VERSION )
        requestedVersion = lastMetaVersion < first ?
            first : lastMetaVersion + 1;
      if( lastMetaVersion >= requestedVersion ) {
        Log.log("Trying to add version older than most current", Log.FATALERROR);
        throw new IllegalArgumentException("Version already in use.");
      }
      lastMetaVersion=requestedVersion;
      if( allocData )
        lastDataVersion = lastMetaVersion;
      versionList.put(new Integer(lastMetaVersion),new Integer(lastDataVersion));
      return lastMetaVersion;
    }


    public int getDataVersion( int mdVersion ) {
      Integer dv = (Integer) versionList.get(new Integer(mdVersion));
      if( dv == null ) {
        //Log.log("No data for mdver "+mdVersion,Log.WARNING);
        return Constants.NO_VERSION;
      }
      return dv.intValue();
    }

    public int getMetadataVersion( int dVersion ) {
      return dVersion; // By definition
    }

    public int getPreviousMetadata(int ver) {
      if( ver == Constants.NO_VERSION )
        return Constants.NO_VERSION;
      int prevver = ver -1;
      return versionList.containsKey(new Integer(prevver)) ?
          prevver : Constants.NO_VERSION;
    }

    public int getPreviousData(int ver) {
      int thisdataver = getDataVersion(ver);
      if( thisdataver == Constants.NO_VERSION )
        return Constants.NO_VERSION;
      do {
        ver = getPreviousMetadata(ver);
      } while( thisdataver == getDataVersion(ver) );
      return ver;
    }

    public int[] getRepositoryVersions() {
      int[] versions = new int[versionList.size()];
      int pos = 0;
      for( Iterator i = versionList.keySet().iterator(); i.hasNext();) {
        versions[pos++]= ((Integer) i.next()).intValue();
      }
      return versions;
    }

  }

  protected static String guidToFile(GUID g) {
    UID u = g.getUId();
    if( u instanceof QueriedUID && !Util.isEmpty(((QueriedUID) u).getQuery())) {
      return URLEncoder.encode(((QueriedUID) u).toBase64Q())
         + "@" + g.getLocation();
    }
    return g.getUId().toBase64()+"@"+g.getLocation();
  }

  private class VersionHistoryImpl implements fc.syxaw.fs.VersionHistory {

    GUID key;

    VersionHistoryImpl(GUID aKey ) {
      key = aKey; //(nowadays immutable) (GUID) aKey.clone();
    }

    public FullMetadata getMetadata(int mdVersion) {
      return VersionDB.this.getMetadata(key,mdVersion);
    }

    public InputStream getData(int version) {
      return VersionDB.this.getData(key,version);
    }

    public int getPreviousData(int version) {
      return VersionDB.this.getPreviousData(key,version);
    }

    public int getPreviousMetadata(int mdversion) {
      return VersionDB.this.getPreviousMetadata(key,mdversion);
    }

    private int[] getVersions() {
      return getRepositoryVersions(key);
    }

    public int getCurrentVersion() {
      return VersionDB.this.getCurrentVersion(key);
    }

    // NOTE: NO_VERSION != NO_VERSION
    public boolean versionsEqual(int v1, int v2, boolean meta) {
      if (v1 == Constants.NO_VERSION || v2 == Constants.NO_VERSION)
        return false;
      if (v1 == v2)
        return true;
      if( v1 == Constants.ZERO_VERSION || v2 == Constants.ZERO_VERSION )
        return false; // We don't record these explicitly, so handle as special case
      int maxv = Math.max(v1, v2);
      int minv = Math.min(v1, v2);
      int prevVersion = meta ? getPreviousMetadata(maxv) :
          getPreviousData(maxv);
      if (prevVersion == Constants.NO_VERSION ||
          minv > prevVersion)
        return true;
      return false;
    }

    public List getFullVersions(String branch) {
      if( branch != null && !branch.equals(Version.TRUNK) &&
          !branch.equals(Version.CURRENT_BRANCH) )
        throw new NotInPrototypeException();
      int[] vers = getVersions();
      List vl = new LinkedList();
      for( int i=0;i<vers.length;i++)
        vl.add(new Version(vers[i],Version.TRUNK,Constants.NO_VERSION));
      return vl;
    }

    public boolean onBranch() {
      Log.log("Won't work if working on branches",Log.WARNING);
      return false; //FIXME-P
    }
  }

  public static class Maintenance {
    public static void clean(File root) {
      File[] verdbfiles = root.listFiles();
      if( verdbfiles.length == 0)
        return;
      Log.log("Cleaning version db @" + root + ", entries=" +
              verdbfiles.length / 2, Log.INFO);
      if (verdbfiles[0].getName().indexOf(",m") == -1 &&
         verdbfiles[0].getName().indexOf(",d") == -1 )
       // FP-note: less sure check than in full syxaw
        Log.log("Seems I may not be cleaning the correct dir, bailing out",
                Log.ASSERTFAILED);
      for( int i=0;i<verdbfiles.length;i++) {
        if( !verdbfiles[i].delete() )
          Log.log("Cleaning failed",Log.FATALERROR);
      }
    }

    public static void init(File root) {
      if (!root.exists() && !root.mkdir())
        Log.log("Can't create " + root, Log.FATALERROR);
    }
  }
}
// arch-tag: 3fa890194eed5fbd78a0e02c3622ae04 *-
