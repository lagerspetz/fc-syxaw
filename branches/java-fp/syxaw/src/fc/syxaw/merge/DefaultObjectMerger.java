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

// $Id: DefaultObjectMerger.java,v 1.16 2004/12/29 11:28:17 ctl Exp $

package fc.syxaw.merge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Set;

import fc.syxaw.api.Metadata;
import fc.syxaw.fs.BLOBStorage;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.FullMetadataImpl;
import fc.syxaw.fs.GUID;
import fc.syxaw.fs.ObjectMerger;
import fc.syxaw.fs.SyxawFile;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.proto.Version;
import fc.syxaw.tdm.MergeUtil;
import fc.syxaw.util.Util;
import fc.util.Debug;
import fc.util.log.Log;

/** Default reconciliation algorithm for objects.
 * Implements a null reconciliation algorithm for data and basic reconciliation
 * of metadata. A conflict is
 * reported if data has been changed both locally and remotely, otherwise
 * the downloaded (i.e. modified) version of the data is stored in
 * <code>currentObject</code>. For a description of metadata reconciliation, see
 * {@link #mergeMetadata mergeMetaData}.
 */

public class DefaultObjectMerger implements ObjectMerger {

  private static boolean DEBUG_SAME_HASH_UPLOADS = 
    System.getProperty("fc.syxaw.debug.samehashuploads") != null;
  
  protected SyxawFile f;
  protected VersionHistory history;
  protected int currentVersion;

  /** Create a new merger.
   *
   * @param f SyxawFile upon which this merger operates
   * @param h VersionHistory of the file
   * @param currentVersion current version of the file
   */
  public DefaultObjectMerger(SyxawFile f, VersionHistory h, int currentVersion) {
    this.f = f;
    this.history = h;
    this.currentVersion = currentVersion;
  }

  public int mergeData(boolean currentChanged, boolean downloadChanged,
                       int downloadVersion, BLOBStorage downloadData,
                       int dlVerRef) throws IOException,
      ObjectMerger.Conflict {
    if (!downloadChanged || dlVerRef == Constants.ZERO_VERSION)
      // No remote change, keep status quo
      return currentChanged ? Constants.NO_VERSION : currentVersion;
    if( !currentChanged ) {
      return updateData(f, downloadData, history, dlVerRef,
                        downloadVersion,
                        currentVersion);
    }
    boolean needsMerge = true;
    InputStream fin = null; 
    try {
      fin = f.getInputStream();
      byte[] dlHash = null; 
      if( dlVerRef == Constants.NO_VERSION ) {
        dlHash = Util.copyAndDigestStream(downloadData.getInputStream(), 
            new Util.Sink());
      }
      if (dlHash != null) {
        byte[] thisHash = Util.copyAndDigestStream(fin, new Util.Sink());
        needsMerge = !java.security.MessageDigest.isEqual( dlHash, thisHash );
        Log.info("Hash equality when checking for conflicts=" + (!needsMerge) +
            " this=" + Util.toString(thisHash) + ", downloaded=" +
            Util.toString(dlHash) );
      } else {
        // No hash given by server, we could hash the received data here by ourselves
        // Currently no implemented, though
        Log.log("Cannot compute hash verRef'd data, verRef="+dlVerRef,
            Log.WARNING);
      }
    } finally {
      if( fin != null )
        fin.close();
    }
    if( needsMerge )
      throw new ObjectMerger.Conflict("Conflicting data. Merger=" +
          getClass().getName());
    else
      return DEBUG_SAME_HASH_UPLOADS ? Constants.NO_VERSION : 
        Math.max(currentVersion, downloadVersion );
  }

  /** Merges Syxaw metadata. The metadata fields are merged according
   * to the following algorithm:
   * <pre>
   * merge.setFormat(MergeUtil.merge(base.getFormat(), v1.getFormat(), v2.getFormat()));
   * merge.setReadOnly(MergeUtil.merge(base.getReadOnly(), v1.getReadOnly(),v2.getReadOnly()));
   * merge.setType((String) MergeUtil.merge(base.getType(), v1.getType(),v2.getType()));
   * merge.setMetaModTime(Math.max(v1.getMetaModTime(),v2.getMetaModTime()));
   * merge.setModTime(Math.max(v1.getModTime(),v2.getModTime()));
   * </pre>
   * where <code>base</code> is the previously merged version of the metadata,
   * <code>v1</code> is
   * the current metadata,<code>v2</code> is the downloaded metadata, and
   * <code>merge</code> is the merged metadata. Conflicts
   * are thrown according to {@link fc.syxaw.tdm.MergeUtil#merge(Object, Object, Object)
   * fc.syxaw.tdm.MergeUtil.merge}.
   * @see fc.syxaw.tdm.MergeUtil
   */

  public int mergeMetadata(boolean currentChanged, boolean downloadChanged,
                           int downloadVersion, Metadata downloadMeta,
                           int dlVerRef) throws
      IOException, ObjectMerger.Conflict {
   if( !downloadChanged || dlVerRef == Constants.ZERO_VERSION )
     // No remote change, keep status quo
     return currentChanged ? Constants.NO_VERSION : currentVersion;
   if( currentChanged && currentVersion != Constants.NO_VERSION  ) {
     Log.log("Merging metadata...",Log.INFO);
     Metadata base = history.getMetadata(currentVersion);
     if( base == null )
       throw new ObjectMerger.Conflict("Base metadata not available (version "+
                                       currentVersion+")");
     Metadata v1 = f.getFullMetadata();
     Metadata v2 = dlVerRef == Constants.NO_VERSION ?
         downloadMeta : history.getMetadata(dlVerRef);
     if( v2 == null)
       Log.log("No remote metadata for merge.",Log.FATALERROR);
     if( v1 == null )
       Log.log("No local metadata for merge??",Log.ASSERTFAILED);
     FullMetadataImpl merge = fc.syxaw.fs.Syxaw.getStorageProvider().createMetadata();
     Log.log("MD_MERGE:   Base meta is ",Log.INFO,base);
     Log.log("MD_MERGE:  Local meta is ",Log.INFO,v1);
     Log.log("MD_MERGE: Remote meta is ",Log.INFO,v2);
     try {
       Set joins1 = Version.parseJoins(v1.getJoins());
       Set joins2 = Version.parseJoins(v2.getJoins());
       for( Iterator i=joins2.iterator();i.hasNext(); )
         ((Version) i.next()).addToJoins(joins1);
       merge.setJoins(Version.makeJoins(joins1));
       merge.setReadOnly(MergeUtil.merge(base.getReadOnly(), v1.getReadOnly(),
                                       v2.getReadOnly()));
       merge.setType((String) MergeUtil.merge(base.getType(), v1.getType(),
                                       v2.getType()));
       merge.setMetaModTime(Math.max(v1.getMetaModTime(),
                                            v2.getMetaModTime()));
       merge.setModTime(Math.max(v1.getModTime(),v2.getModTime()));
       
       String kw1 = v1.getKeywords() != null ?
             v1.getKeywords().toStrings() : "";
       String kw2 = v2.getKeywords() != null ?
             v2.getKeywords().toStrings() : "";
       
       Log.log("B: " 
           + ( base.getKeywords() != null ? base.getKeywords().toStrings() : "") 
           + "\n" 
           + "L: " + kw1 + "\n"
           + "R: " + kw2, Log.INFO);
       
       if (kw1.equals("")){
         merge.setKeywords(v2.getKeywords());  
       } else if (kw2.equals("")){
         merge.setKeywords(v1.getKeywords());
       }
       if (!kw1.equals("") && !kw2.equals("")){ 
         if (!kw1.equals(kw2)) {
           throw new ObjectMerger.Conflict("File needs to be reindexed to update keywords.");           
         }
       }
     } catch (MergeUtil.Conflict c) {
       throw new ObjectMerger.Conflict("Conflicting modifications to metadata");
     }
     Log.log("MD_MERGE: Merged meta is ",Log.INFO,merge);
     f.setMetadata(merge);
     return Constants.NO_VERSION; // Newly created merge has no version
   } else // Apply downloaded
     return updateMeta(f,downloadMeta,history,dlVerRef,
                     downloadVersion,currentVersion);
  }

  public InputStream getMergedData() throws IOException {
    return f.getInputStream();
  }

  public FullMetadata getMergedMetadata() throws IOException  {
    return f.getFullMetadata();
  }


  // Returns true if we wrote new data
  protected int updateData(SyxawFile file, BLOBStorage data,
                           VersionHistory history, int verref,
                           int storageVersion,
                           int currentLinkVer) {
    if (history.versionsEqual(currentLinkVer, verref, false)) {
      // Already current version, so nop
      return verref;
    }
    else if (verref != Constants.NO_VERSION) {
      // Retrieving old version
      InputStream in = null;
      OutputStream out = null;
      try {
        try {
          in = history.getData(verref);
          out = file.getOutputStream(false);
          Util.copyStream(in, out);
        }
        finally {
          if (in != null)
            in.close();
          if (out != null)
            out.close();
        }
      }
      catch (IOException x) {
        Log.log("Cannot retrieve old version", Log.FATALERROR);
      }
      return verref;
    }
    else {
      // Rebind to updated data
      try {
        file.rebindLinkStorage(data);
      }
      catch (IOException x) {
        Log.log("Data rebind failed", Log.FATALERROR, x); // Should not happen
      }
      return storageVersion;
    }
  }

  // Returns true if we wrote new meta
  protected int updateMeta(SyxawFile file, Metadata md,
                           VersionHistory history, int verref,
                           int storageVersion,
                           int currentLinkVer) throws IOException {
    if (history.versionsEqual( currentLinkVer, verref, true)) {
      // Already current version, nop
      return verref;
    }
    else if (verref != Constants.NO_VERSION) {
      try {
        FullMetadata refdmd = history.getMetadata(verref);
        file.setMetadata(refdmd);
      }
      catch (IOException x) {
        Log.log("Can't retrieve old metadata", Log.FATALERROR);
      }
      return verref;
    }
    else {
      try {
        file.setMetadata(md);
      }
      catch (IOException x) {
        Log.log("Metadata assign failed", Log.FATALERROR); // Should not happen
      }
      return storageVersion;
    }
  }

  /** Return no dependent objects.
   * @return <code>null</code>.
   */

  public Set getObjectsNeedingSync() {
    return null; // No objects
  }

  /** Get dependent location. Return the location of the link of the
   * object's file.
   *
   * @return dependent location, or <code>null</code> if none.
   */
  public String getDependentLID() {
    String link = f.getLink();
    return Util.isEmpty(link) ? null :  new GUID(f.getLink()).getLocation();
  }
}
// arch-tag: 21a7917d6bc543e0a47f6a768fe907ef *-
