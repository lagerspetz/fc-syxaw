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

// $Id: FileSystem.java,v 1.54 2005/06/08 13:03:28 ctl Exp $
package fc.syxaw.storage.hierfs;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import fc.syxaw.fs.Config;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.UID;
import fc.syxaw.hierfs.DirectoryEntry;
import fc.syxaw.storage.hfsbase.AbstractFileSystem;
import fc.syxaw.storage.hfsbase.AbstractSyxawFile;
import fc.syxaw.storage.hfsbase.ForeignMetadata;
import fc.syxaw.storage.hfsbase.ObjectDb;
import fc.syxaw.storage.hfsbase.ServerConfig;
import fc.syxaw.storage.hfsbase.StoredDirectoryTree;
import fc.syxaw.storage.hfsbase.StringDb;
import fc.syxaw.transport.PropertyDeserializer;
import fc.syxaw.transport.PropertySerializer;
import fc.syxaw.util.Cache;
import fc.syxaw.util.LruCache;
import fc.syxaw.util.SynchronizedCache;
import fc.syxaw.util.Util;
import fc.util.StringUtil;
import fc.util.log.Log;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.model.StringKey;

/** Generic hierarchical filesystem. */

public class FileSystem extends AbstractFileSystem {

  static FileSystem FS_INSTANCE = null;
  
  private StringDb UIDsbyDname = new StringDb(HfsToolkit.UIDDB);

  private ObjectDb metaByUID = new Metadata.MetaDb(HfsToolkit.METADB,true);

  //Cache of (java.io.File,UID)
  //NOTE: Aggressively cleaned on FreeUID; if made much larger
  // then perhaps we should clean it selectively in freeUID

  private Cache uidbyfile = makeUidCache();

  // FIXME:
  // if file.equals on pathname is used, this may break for e.g two files
  // with same pathname, where a dir on the path has been swapped
  // e.g. /foo/bar/zoo, where bar.uid has changed
  // OTOH; uidcache in SyxawFile will break too in that case!

  private static final Cache makeUidCache() {
    return new SynchronizedCache( new LruCache( Cache.TEMP_MED,64,32 ) );
  }

  public FileSystem() {
    super();
    if( FS_INSTANCE != null )
      throw new IllegalStateException("Only one instance of the file system can be made");
    FS_INSTANCE = this;
    if( ServerConfig.RESTORE_METDADB != null ) {
      restoreMetaDb(new File(ServerConfig.RESTORE_METDADB));
    }
    init(new FreeUIDMonitor());
    UID uid = getUid(ServerConfig.ROOT_FOLDER_FILE);
    Log.log("Fs is Generic HierFs at " + ServerConfig.ROOT_FOLDER_FILE +
            ", root=" + uid.toBase64(),
            Log.INFO);
    getFullMetadata(uid);
  }

  // Fs interface
  public boolean delete(File f, UID uid) {
    uidbyfile.remove(f);
    boolean isDir = f.isDirectory();
    if( isDir ) {
      String[] entries = f.list();
      if( entries==null || entries.length > 1 ||
          !HfsToolkit.DIRUID_FILE.equals(entries[0]))
        return false; // Only empty dirs (lest diruid) may be deleted
      File duf = new File(f,HfsToolkit.DIRUID_FILE);
      if( !duf.delete() )
        Log.log("Can't kill uid for "+f,Log.FATALERROR);
    }
    return f.delete();
  }

/*  public File getFileForUId(UID uid) {
    File f = _getFileForUId(uid);
    Log.log("File for uid "+uid+"="+f,Log.INFO);
    return f;
  }*/

  public File getFileForUId(UID uid) {
    int scansLeft = 1;
    uid = uid == UID.DBO ? getUid(ServerConfig.ROOT_FOLDER_FILE) : uid;
    // BUGFIX-080404-1: Avoid assertion failure when requesting unknown UID
    Metadata md = (Metadata) metaByUID.lookup(uid.toBase64());
    if( md == null )
        return null;
    StringKey id = md.getId();

    // f by uid does either not exist, or then it has appeared into the
    // fs, but we haven't synced the fs into the local tree db yet
    // sync, and try again:

    if( id == null ) {
        // No id yet, force alloc by scan
        Log.log("No ID for "+uid.toBase64()+", scanning tree...",Log.WARNING);
        localTree.ensureLocalAndLiveInSync(null,false);
        scansLeft --;
        md = getFullMetadata(uid);
        if( md != null )
            id = md.getId();
        Log.log("Retrieved id is "+id,Log.INFO);
    }
    if( id != null ) {
      File f = getFile(localTree.getNode(id),id);
      if( f != null )
          return f;
      if( scansLeft > 0 ) {
          Log.log("Cannot locate " + uid.toBase64() + ", scanning tree...",
                  Log.WARNING);
          localTree.ensureLocalAndLiveInSync(null, false);
      }
      return getFile(localTree.getNode(id),id);
    } else
      return null;
  }

  public byte[] getHash(File f) {
    Metadata md = getFullMetadata(getUid(f));
    return md != null ? md.getHash() : null;
  }

  public String getLink(File f) {
    Metadata md = getFullMetadata(getUid(f));
    return md != null ? md.getLink() : null;
  }

  public long getLinkDataVersion(File f) {
    Metadata md = getFullMetadata(getUid(f));
    return md != null ? md.getLinkDataVersion() : Constants.NO_VERSION;
  }

  public long getMetaModTime(File f) {
    Metadata md = getFullMetadata(getUid(f));
    return md != null ? md.getMetaModTime() : 0l;
  }

  public ForeignMetadata getMetadata(File f) {
    UID u = getUid(f);
    Metadata md = getFullMetadata(u);
    if( md == null )
      return null;
    return new LazyForeignMeta(md,f);
  }

  public int getModflags(File f) {
    int flags = 0; // = All changed
    Metadata md = getFullMetadata(getUid(f));
    if( md == null )
      Log.log("Missing meta for "+f,Log.WARNING);
    if( md == null || (Config.DEBUG_DIRMODFLAG && 
        fc.syxaw.storage.hfsbase.Config.DIRECTORY_TYPE.equals( md.getType() ) ))
      return flags;
    try {
      long localS = md.getLocalModStamp();
      long linkS = md.getLinkModStamp();
      long currentStamp = mkmodflag(f);
      if (currentStamp == localS)
        flags |= ForeignMetadata.DATA_EQ_LOCALVER;
      if (currentStamp == linkS)
        flags |= ForeignMetadata.DATA_EQ_LINKVER;
      flags |= md.getMetaModStamp();
    } catch( NumberFormatException x) {
      Log.log("Invalid modflags for "+f,Log.ERROR);
    }
    return flags;
  }

  private static final long mkmodflag(File f) {
//    Log.log("Making modstamp from "+modt+", size "+size+" =" +
//            ((size>>32 ^ size) << 32) + ((modt>>32 ^ modt) & 0xffff),Log.DEBUG);
    long modt = AbstractSyxawFile._n95_cap_modtime(f.lastModified());
    long size = f.isDirectory() ? 1 : f.length();
    return ((size>>32 ^ size) << 32) + ((modt>>32 ^ modt) & 0xffff);
  }

  public StringKey getNodeId(File f) {
    Metadata md = getFullMetadata(getUid(f));
    return md != null ? md.getId() : null;
  }

  public String getType(File f) {
    Metadata md = getFullMetadata(getUid(f));
    return md != null ? md.getType() : null;
  }

  public UID getUid(File f) {
    return getUid(f,null,false,null);
  }

  private final UID getUid(File f, UID nu, boolean inCreate,
                           Metadata inRestore) {
    // Figure out diruid + Name
    if( !f.exists() )
      return null;
    String uidstr = (String) uidbyfile.get(f);
    if( uidstr == null ) {
      boolean isDir = f.isDirectory();
      File duf = isDir ?
          new File(f,HfsToolkit.DIRUID_FILE) :
          new File(f.getParentFile(), HfsToolkit.DIRUID_FILE);
      try {
        FileInputStream dufin = new FileInputStream(duf);
        ByteArrayOutputStream bin = new ByteArrayOutputStream(64);
        Util.copyStream(dufin, bin);
        dufin.close();
        if( isDir )
          // Dirs by their UID file
          uidstr = bin.toString();
        else {
          // Files by parent UID + name
          String key = getKey(bin.toString(),f);
          if( inRestore != null )
            inRestore.setDkey(key);
          uidstr = UIDsbyDname.lookup(key);
          boolean noUid = Util.isEmpty(uidstr);
          boolean replaceKey=false;
          if ( inCreate && !noUid ) {
            // UID is of old deleted file, ensure new one
            UID oldUID = UID.createFromBase64(uidstr);
            Metadata md = getFullMetadata(oldUID);
            md.setDkey(null); // Must unlink Dkey
            setFullMetadata(oldUID,md);
            //done below with update UIDsbyDname.delete(key);
            Log.log("Killed stale dkey "+key,Log.INFO);
            noUid = true;
            replaceKey=true;
          }
          if ( noUid ) {
            UID u = nu == null ? UID.newUID() : nu;
            //Log.log("NEW_UID: "+u.toBase64()+" for "+f,Log.INFO);
            uidstr = u.toBase64();
            if (replaceKey)
              UIDsbyDname.update(key, uidstr);
            else
              UIDsbyDname.insert(key, uidstr);
            uidbyfile.put(f, uidstr);
            setFullMetadata(u, inRestore != null ? inRestore :
                            new Metadata(null,0,"",Constants.NO_VERSION,
                                     System.currentTimeMillis(),key));
            return u;
          }
        }
        uidbyfile.put(f, uidstr);
      } catch (FileNotFoundException ex) {
        // duf missing; alloc diruid
        try {
          FileOutputStream dufof = new FileOutputStream(duf);
          UID duid = inRestore != null && nu != null ? nu : UID.newUID();
          //if( duid != nu )
          //Log.log("NEW_UID2: "+duid.toBase64()+" for "+duf,Log.INFO);
          byte[] duidb = duid.toBase64().getBytes();
          dufof.write(duidb);
          dufof.close();
          setFullMetadata(duid, inRestore != null ? inRestore :
                          new Metadata(null,0,
                           fc.syxaw.storage.hfsbase.Config.DIRECTORY_TYPE,
                           Constants.NO_VERSION,
                           System.currentTimeMillis(),null));
        } catch (IOException ex1) {
          Log.log("Error writing dir UID", Log.FATALERROR, ex1);
        }
        return getUid(f,null,false,inRestore);
      } catch (IOException ex2) {
        Log.log("Error obtaining UID", Log.FATALERROR, ex2);
      }
    }
    return UID.createFromBase64(uidstr);
  }

  private final String getKey(String uid, File f) {
    return uid + "." +
             (ServerConfig.CASE_INSENSITIVE_HOSTFS ? f.getName().toLowerCase() :
              f.getName());

  }

  public long getDataVersion(File f) {
    Metadata md = getFullMetadata(getUid(f));
    return md != null ? md.getDataVersion() : Constants.NO_VERSION;
  }


  // -- setters --

  public void setHash(File f, byte[] aHash) {
    UID u = getUid(f);
    MutableMetadata md = MutableMetadata.create( getFullMetadata(u) );
    if( md != null ) {
      md.setHash(aHash);
      md.setMetaModStamp(0); // All changed
      setFullMetadata(u,md);
    }
  }

  public void setLink(File f, String aLink) {
    UID u = getUid(f);
    MutableMetadata md = MutableMetadata.create( getFullMetadata(u) );
    if( md != null ) {
      md.setLink(aLink);
      md.setMetaModStamp(0); // All changed
      setFullMetadata(u,md);
    }
  }

  public void setLinkDataVersion(File f, long version) {
    UID u = getUid(f);
    MutableMetadata md = MutableMetadata.create(getFullMetadata(u) );
    if( md != null ) {
      md.setLinkDataVersion(version);
      md.setMetaModStamp(0); // All changed
      setFullMetadata(u,md);
    }
  }

  public long getLinkMetaVersion(File f) {
    Metadata md = getFullMetadata(getUid(f));
    return md != null ? md.getLinkMetaVersion() : Constants.NO_VERSION;
   }
  
  public void setLinkMetaVersion(File f, long version) {
    UID u = getUid(f);
    MutableMetadata md = MutableMetadata.create(getFullMetadata(u) );
    if( md != null ) {
      md.setLinkMetaVersion(version);
      md.setMetaModStamp(0); // All changed
      setFullMetadata(u,md);
    }
  }

  public void setMetaModTime(File f, long aMetaModTime) {
    UID u = getUid(f);
    MutableMetadata md = MutableMetadata.create( getFullMetadata(u) );
    if( md != null ) {
      md.setMetaModTime(aMetaModTime);
      md.setMetaModStamp(0); // All changed
      setFullMetadata(u,md);
    }
  }

  public void setMetadata(File f, ForeignMetadata fmd, boolean setMetaModified) {
    UID u = getUid(f);
    //Log.log("SET_MD for "+f+", uid="+u.toBase64(),Log.INFO);
    MutableMetadata md = MutableMetadata.create( getFullMetadata(u) );
    // FIXME! FIXME! This treats metadata as mutable, but it cannot be that
    // since we use it in lazy configs!!! Must create new one here!!!
    if (md != null) {
      md.setJoins(fmd.getJoins());
      md.setHash(fmd.getHash());
      //md.setId();
      md.setLink(fmd.getLink());
      //md.setLinkModStamp();
      md.setLinkDataVersion(fmd.getLinkDataVersion());
      md.setLinkMetaVersion(fmd.getLinkMetaVersion());
      //md.setLocalModStamp();
      if( setMetaModified ) {
        md.setMetaModStamp(0); // All changed
        md.setMetaModTime(System.currentTimeMillis());
      }
      md.setType(fmd.getType());
      md.setDataVersion(fmd.getDataVersion());
      md.setMetaVersion(fmd.getMetaVersion());
      md.setBranch(fmd.getBranch());
      setFullMetadata(u, md);
    }

  }

  public void setModflags(File f, int aModflags) {
    UID u = getUid(f);
    MutableMetadata md = MutableMetadata.create( getFullMetadata(u) );
    if( md == null)
      return;
    long changeStamp= mkmodflag(f);
    md.setLocalModStamp((aModflags & ForeignMetadata.DATA_EQ_LOCALVER) != 0 ?
             changeStamp : -1l);
    md.setLinkModStamp((aModflags & ForeignMetadata.DATA_EQ_LINKVER) != 0 ?
               changeStamp : -1l);
    md.setMetaModStamp(aModflags);
    setFullMetadata(u,md);
  }

  public void setNodeId(File f, StringKey id) {
    UID u = getUid(f);
    MutableMetadata md = MutableMetadata.create( getFullMetadata(u) );
    if( md != null ) {
      md.setId(id);
      md.setMetaModStamp(0); // All changed
      setFullMetadata(u,md);
    }
  }

  public void setType(File f, String aType) {
    UID u = getUid(f);
    MutableMetadata md =  MutableMetadata.create( getFullMetadata(u) );
    if (md != null) {
      md.setType(aType);
      md.setMetaModStamp(0); // All changed
      setFullMetadata(u, md);
    }
  }

  public void setDataVersion(File f, long version) {
    UID u = getUid(f);
    MutableMetadata md = MutableMetadata.create( getFullMetadata(u) );
    if (md != null) {
      md.setDataVersion(version);
      md.setMetaModStamp(0); // All changed
      setFullMetadata(u, md);
    }
  }
  
  public void setMetaVersion(File f, long version) {
    UID u = getUid(f);
    MutableMetadata md = MutableMetadata.create( getFullMetadata(u) );
    if (md != null) {
      md.setMetaVersion(version);
      md.setMetaModStamp(0); // All changed
      setFullMetadata(u, md);
    }
  }

  public boolean create(File f, UID u) throws IOException {
    if( f.createNewFile() ) {
      uidbyfile.remove(f);
      getUid(f,u,true,null);
      return true;
    }
    return false;
  }

  private Metadata getFullMetadata(UID u) {
    //Log.log("GET_MD for "+u.toBase64(),Log.INFO);
    Metadata md = null;
    if( u== null ) {
      Log.log("Trying to get md for null UID",Log.WARNING);
      return null;
    }
    md = (Metadata) metaByUID.lookup(u.toBase64());
    if( md == null )
      Log.log("Returning null md for "+u+
              ". NOTE: This happens if you copy .syxaw-uid files into a"+
              " share from elsewhere (i.e. they are not locally allocated)!"+
              " Did you just do that?" ,Log.ASSERTFAILED);
    return md;
  }

  private void setFullMetadata(UID u, Metadata md ) {
    //Log.log("SET_FullMD for "+u.toBase64(),Log.INFO,md);
    metaByUID.update(u.toBase64(),md);
  }

  private File getFile(RefTreeNode n, Key id) {
    if (n == null)
      Log.log("Cannot find node "+id,Log.FATALERROR);
    RefTreeNode p = n.getParent();
    if( p == null )
      return ServerConfig.ROOT_FOLDER_FILE;
    else
      return new File(getFile(p,p.getId()),
                      ( (DirectoryEntry) n.getContent()).getName());
  }

  // Backup/restore metadata facilities

  void dumpMetaDb(File root, PrintWriter pw) throws IOException {
    UID u = getUid(root);
    // encode md
    Map list = new HashMap();
    PropertySerializer ps = new PropertySerializer(list);
    ps.writeObject(getFullMetadata(u));
    pw.print( pathToDumpPath(root) );
    list.put("uid",u.toBase64());
    for( Iterator i = list.entrySet().iterator();i.hasNext();) {
      Map.Entry e = (Map.Entry) i.next();
      pw.print(" "+e.getKey()+"="+Util.encodeStr((String) e.getValue()));
    }
    pw.println();
    if( root.isDirectory() ) {
      String children[] = root.list(HfsToolkit.NO_DOT_SYXAW_PFX);
      for( int i=0;i<children.length;i++)
        dumpMetaDb(new File(root,children[i]),pw);
    }
  }

  private String pathToDumpPath(File f) {
    String path = f.getAbsolutePath().substring(ServerConfig.ROOT_FOLDER.length());
    path=path.replace('\\','/');
    return Util.encodeStr( path.length()==0 ? "/" : path);
  }

  // NOTE: Called before SyxawFile is inited, so we can only use files
  void restoreMetaDb(File metadb) {
    Log.log("Restoring metdata db from "+metadb+
            ". This may require a lot of memory",Log.WARNING);
    // Read to in-memory representation
    Map mdb = new HashMap();
    Map uids = new HashMap();
    try {
      BufferedReader rd = new BufferedReader( new InputStreamReader(
              new FileInputStream( metadb )));
      int lc = 0;
      for( String line=null; (line = rd.readLine()) != null; ) {
        if( lc%250 == 0 )
          Log.log("Reading line "+(lc+1),Log.INFO);
        lc ++;
        String[] keyvals = StringUtil.splitWords(line);
        String key = keyvals[0];
        Map props = new HashMap();
        for( int i=1;i<keyvals.length;i++) {
          int spos = keyvals[i].indexOf('=');
          if( spos == -1 )
            Log.log("Invalid line "+lc+":"+line,Log.FATALERROR);
          props.put(keyvals[i].substring(0,spos),
                  Util.decodeStr(keyvals[i].substring(spos+1)));
        }
        uids.put(key,UID.createFromBase64((String) props.get("uid")));
        PropertyDeserializer ds = new PropertyDeserializer(props);
        Metadata md = new Metadata();
        ds.readObject(md);
        mdb.put(key,md);
      }
      Log.log("Read "+lc+" entries.",Log.INFO);
    } catch ( Exception x ) {
      Log.log("Exception while restoring metadb:",Log.FATALERROR,x);
    }
    // Scan fs, and restore meta for each found entry
    LinkedList l = new LinkedList();
    l.add(ServerConfig.ROOT_FOLDER_FILE);
    while( l.size() > 0 ) {
      File f = (File) l.removeFirst();
      String key = pathToDumpPath(f);
      Metadata md = (Metadata) mdb.remove(key);
      UID ru = (UID) uids.remove(key);
      if( md == null ) {
        ru = null; // Will alloc new
        Log.log("File not in saved metadb, generating new meta: "+
                pathToDumpPath(f),Log.WARNING);
      }
      String oldDk = md.getDkey();
      UID u = getUid(f,ru,false,md);
      if( !Util.equals(md.getDkey(),oldDk) )
        Log.log("Reassinged dKey for "+pathToDumpPath(f)+" old="+oldDk+", new="+
                md.getDkey(),Log.WARNING);
      if( ru != null && !u.equals(ru) )
        Log.log("UID restore failed",Log.ASSERTFAILED);
      if( f.isDirectory() ) {
        // Subentries
        String[] dents = f.list(HfsToolkit.NO_DOT_SYXAW_PFX);
        for( int i=0;i<dents.length;i++)
          l.add(new File(f,dents[i]));
      }
    }
    if( mdb.size() > 0 ) {
      Log.log("The following entries are no longer used: "+mdb.keySet(),Log.WARNING);
    }
    if( uids.size() > 0 ) {
      Log.log("The following uids are no longer used: "+uids.keySet(),Log.WARNING);
    }
  }

  protected AbstractSyxawFile newFile(File f) {
    return new SyxawFile(f);
  }

  private class FreeUIDMonitor extends StoredDirectoryTree.ChangeMonitor{
    /**
     * Idea: file deletes done outside Syxaw are detected and the uids removed here.
     * Metadata is not deleted in delete for the purposes of DirectoryTrees.
     */
    public void freeUID(String uid) {
      Metadata md = getFullMetadata(UID.createFromBase64(uid));
      if( md != null ) {
        String dKey = md.getDkey();
        Log.log("Freeing UID "+uid+", dkey="+md.getDkey(),Log.INFO);
        if( !Util.isEmpty(dKey) ) // Empty key means md is orphaned by new same-name file
          UIDsbyDname.delete(md.getDkey());
        metaByUID.delete(uid);
        uidbyfile.clear();
      } else
        Log.log("Missing meta for uid "+uid,Log.FATALERROR);
    }
  }

}
// arch-tag: 3f40497b24d9697157aff4c4ff04faf6 *-
