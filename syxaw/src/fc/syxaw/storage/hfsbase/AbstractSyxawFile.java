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

// $Id: AbstractSyxawFile.java,v 1.7 2005/06/08 13:09:10 ctl Exp $
package fc.syxaw.storage.hfsbase;

// Java
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import fc.syxaw.api.ISyxawFile;
import fc.syxaw.api.Metadata;
import fc.syxaw.api.StatusCodes;
import fc.syxaw.api.SynchronizationException;
import fc.syxaw.fs.BLOBStorage;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.FullMetadataImpl;
import fc.syxaw.fs.GUID;
import fc.syxaw.fs.ObjectProvider;
import fc.syxaw.fs.ObjectMerger;
import fc.syxaw.fs.QueriedUID;
import fc.syxaw.fs.OldRepository;
import fc.syxaw.fs.Repository;
import fc.syxaw.fs.SynchronizationEngine;
import fc.syxaw.fs.UID;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.hierfs.DirectoryMerger;
import fc.syxaw.hierfs.SyxawFile;
import fc.syxaw.merge.DefaultObjectMerger;
import fc.syxaw.proto.NotInPrototypeException;
import fc.syxaw.proto.Version;
import fc.syxaw.util.Util;
import fc.util.log.Log;
import fc.xml.xas.XasUtil;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.model.StringKey;

/* Base for syxawfiles on hierarchical filesystems. Provides default
 * implementations for some methods. The default implementations use the
 * abstract mehods that have to be implemented specifically for each hierfs
 * implementation (NFS,XFS,generic hierfs etc)
 */

public abstract class AbstractSyxawFile extends SyxawFile 
  implements ObjectProvider  { // NOTE: see getObjectProvider() 

  protected String query=null;
  protected File f;
  private UID cachedUID=null; // FIXME-W caching here is maybe not smart

  private static final Random rnd = new Random(System.currentTimeMillis());

  private static final String NO_LINK_CHANGE = "{{Dont-change-link}}";

  protected static AbstractFileSystem fs = Toolkit.getInstance().getFs();

  protected static final VersionMapper vmapper = 
    new VersionMapper(Toolkit.VERMAP_DB); 
  
  protected static final Map repositories;

  protected static final Map objectProviders;
  
  public static final QueryStreamFactory qsf= new QueryStreamFactory();

  static {
    objectProviders = new HashMap();
    repositories = new HashMap();
    repositories.put(null, new FakeRepository());
    repositories.put(Config.DIRECTORY_TYPE, new DirectoryRepository());
    Repository br = new BasicRepository();
    repositories.put("text", br);
    repositories.put("text/plain", br);
    repositories.put(XasUtil.XML_MIME_TYPE, br);
  }

  /** Syxaw system directory. The system directory is used to store
   * special Syxaw file system data that is accessible to the user,
   * such as conflict information.
   * <p>Initialized to /{@link Config#SYSTEM_FOLDER} */
  //protected static SyxawFile SYS_FOLDER = null;

  /** Syxaw conflict directory. Syxaw stores conflict information in this
   * directory. Assume the synchronization of file, whose GUID is <i>g</i>,
   * results in a conflict. The conflicting data downloaded from the peer
   * is then stored in this folder as the file <i>g'</i>, and any conflict
   * log is stored as <code><i>g'</i>.log</code>, where <i>g'</i> is the
   * file name determined by {@link fc.syxaw.fs.UID#toBase64
   * UID.toBase64()}.
   *
   * <p>Initialized to
   * {@link Config#SYSTEM_FOLDER}/{@link Config#CONFLICT_FOLDER} */

  //protected static SyxawFile SYS_CONFLICT_FOLDER = null;

//==========================================================================

  protected  AbstractSyxawFile( UID id ) {
    String query = null;
    if( id instanceof QueriedUID )  {
      // Make sure only  non-query UIDs go into the fs
      query = ((QueriedUID) id).getQuery();
      id = ((QueriedUID) id).getUid();
    }
    File uf = fs.getFileForUId(id); // BUGFIX-080404-1
    init( uf == null ? new File((String) null) : uf, query);
  }

  /*
  protected  AbstractSyxawFile( UID id, String query ) {
    init( fs.getFileForUId(id), query );
  }*/

  protected AbstractSyxawFile(fc.syxaw.fs.SyxawFile parent, String child) {
    if (parent != null && !(parent instanceof AbstractSyxawFile))
      Log.log("Trying to instantiate across incompatible storage implementations",
              Log.ASSERTFAILED);
    init(new File(parent != null ? ((AbstractSyxawFile) parent).f : null,
                  child), ((AbstractSyxawFile) parent).query);
  }

  // Allow // = root of current

  protected AbstractSyxawFile(String path) {
    if( path != null && path.startsWith("//") )
      init(ServerConfig.ROOT_FOLDER_FILE+
           File.separator+path.substring(2),null);
    else
      init(path,null);
  }

  protected AbstractSyxawFile(File file) {
    init(file,null);
  }

  protected void init(String pathName, String aQuery) {
    if( aQuery == null ) {
      int split = pathName != null ? pathName.indexOf(querySeparatorChar) :
                  -1;
      if (split > -1) {
        query = pathName.substring(split + 1);
        pathName = pathName.substring(0, split);
      }
    } else
      query = aQuery;
    f = new File(pathName);
  }

  protected void init(File file, String aQuery) {
    query = aQuery;
    f = file;
  }

// Extended ops
  public FullMetadata getFullMetadata() throws FileNotFoundException {
    return getObjectProvider().getFullMetadata(this);
  }
  
  public FullMetadata getFullMetadata(fc.syxaw.fs.SyxawFile _) throws FileNotFoundException {
    if (!f.exists())
      throw new FileNotFoundException("" + f);
    return new LazyMetadata(fs.getMetadata(this.f),this);
  }

  protected ForeignMetadata getForeignMeta() {
    return fs.getMetadata(this.f);
  }

  protected MutableForeignMeta getMutableForeignMeta() {
    ForeignMetadata md = fs.getMetadata(this.f);
    return MutableForeignMeta.createFrom(md);
  }
  
  
  public GUID getGuid() {
    return new GUID(GUID.getCurrentLocation(), getUid());
  }

  public UID getUid() {
    return cachedUID != null ? cachedUID : (cachedUID = fs.getUid(f));
  }

  public String getLink() {
    return fs.getLink(f);
  }
  public boolean setLink(Integer version, Boolean metaModified,
      Boolean dataModified) {
    return setLink(NO_LINK_CHANGE, version, metaModified, dataModified, true, true);
  }
  
  public boolean setLink(Integer version, Boolean metaModified,
                         Boolean dataModified, boolean datasync, boolean metasync) {
    return setLink(NO_LINK_CHANGE, version, metaModified, dataModified, datasync, metasync);
  }
  
  public boolean setLink(String link, Integer version, Boolean metaModified,
      Boolean dataModified) {
    return setLink(link, version, metaModified, dataModified, true, true);
  }
  
  public boolean setLink(String link, Integer version, Boolean metaModified,
                         Boolean dataModified, boolean datasync, boolean metasync) {
    if (!f.exists())
      return false;
    MutableForeignMeta fmd = getMutableForeignMeta();
    if (link != NO_LINK_CHANGE)
      fmd.setLink(link);
    if (version != null) {
      if (datasync)
        fmd.setLinkDataVersion( version.intValue()); // FIXME-versions
      if (metasync)
        fmd.setLinkMetaVersion( version.intValue()); // FIXME-versions
    }
    fs.setMetadata(f,fmd,false);
    if (metaModified != null) {
      if (metaModified.booleanValue())
        fs.setModflags(f, fs.getModflags(f) & (~ForeignMetadata.META_EQ_LINKVER));
      else
        fs.setModflags(f, fs.getModflags(f) | ForeignMetadata.META_EQ_LINKVER);
    }
    if (dataModified != null) {
      if (dataModified.booleanValue())
        fs.setModflags(f, fs.getModflags(f) & (~ForeignMetadata.DATA_EQ_LINKVER));
      else
        fs.setModflags(f, fs.getModflags(f) | ForeignMetadata.DATA_EQ_LINKVER);
    }
    return true;
  }

  public Metadata getMetadata() throws FileNotFoundException {
    return getFullMetadata();
  }

  public void setMetadata(Metadata smd) throws FileNotFoundException {
    getObjectProvider().setMetadata(this, smd);
  }
  
  public void setMetadata(fc.syxaw.fs.SyxawFile _,Metadata smd) throws FileNotFoundException {
    if (!f.exists())
      throw new FileNotFoundException("" + f);
    // Foregin part
    MutableForeignMeta fmd = getMutableForeignMeta();
    fmd.setFromMetadata(smd);
    fs.setMetadata(f,fmd,true);
    // Native part
    f.setLastModified(smd.getModTime());
    if (smd.getReadOnly())
      f.setReadOnly();
    else
      f.canWrite();
  }

  public InputStream getInputStream() throws FileNotFoundException {
    return getInputStream(false);
  }

  public InputStream getLinkInputStream() throws FileNotFoundException {
    return getInputStream(true);
  }

  public OutputStream getOutputStream(boolean append) throws
          FileNotFoundException {
    return getOutputStream(append, false);
  }

  public OutputStream getLinkOutputStream(boolean append) throws
          FileNotFoundException {
    return getOutputStream(append, true);
  }

  protected InputStream getInputStream(boolean linkFacet) throws
  FileNotFoundException {
    return getObjectProvider().getInputStream(this, linkFacet);
  }
  
  public InputStream getInputStream(fc.syxaw.fs.SyxawFile _,boolean linkFacet) throws
          FileNotFoundException {
    if (query != null)
      return qsf.getInputStream(newInstance(f.getPath()), query,
                                linkFacet);
    if (f.isDirectory())
      return linkFacet ? fs.getLinkTree().getDeltaInputStream() :
              fs.getLocalTree().getDeltaInputStream();
    else
      return new FileInputStream(f);
  }

  public OutputStream getOutputStream(boolean append, boolean linkFacet) throws
    FileNotFoundException {
    if( !f.exists() ) {
      // We need to do this because the semantics of the method dictates
      // that a file may be created. However, to get the object provider, we 
      // need some metadata -> we must create the file explicitly in our code
      try {
        createNewFile();
      } catch( IOException e) {
        // Deliberately empty
      }
    }
    return getObjectProvider().getOutputStream(this, append, linkFacet);
  }
  
  public OutputStream getOutputStream(fc.syxaw.fs.SyxawFile _,boolean append, boolean linkFacet) throws
          FileNotFoundException {
    if (query != null)
      return qsf.getOutputStream( newInstance(f.getPath()), query,
                                linkFacet);
    if (f.isDirectory()) {
      // Overly pessimistic..
      if (!Config.TRUE_DIR_MODFLAGS) {
        setLink(null, new Boolean(true), new Boolean(true), true, true);
        setLocal(null, new Boolean(true), new Boolean(true));
      }
      if (linkFacet) // Never needed?
        throw new UnsupportedOperationException("DIRSTREAM");
      return fs.getLocalTree().getDeltaOutputStream(true);
    } else
      return new FileOutputStream(f.toString(), append);
  }

  public String getId() throws FileNotFoundException {
    UID u = getUid();
    return u != null ? u.toBase64() : null;
  }

  public boolean isDataModified(boolean link) throws FileNotFoundException {
    return getObjectProvider().isDataModified(this, link);
  }

  public boolean isDataModified(fc.syxaw.fs.SyxawFile _,boolean link) throws FileNotFoundException {
    if( ServerConfig.CALC_DIRMOD && isDirectory() ) {
      boolean retval;
      if( link ) {
        Log.log("Stored flags are "+
                Integer.toHexString(fs.getModflags(f)),Log.DEBUG);
        if( (fs.getModflags(f) & ForeignMetadata.DATA_EQ_LINKVER) == 0)
          return true;
          //if( getLinkVersion() < Constants.FIRST_VERSION )
        //  return true;
        LinkTree tree = fs.getLinkTree();
        retval = tree.hasChanges(); // For uncomitted, but already scanned chgs
        if( !retval ) {
          tree.pushChangeBuffer();
          tree.ensureLocalAndLiveInSync(new HashMap(), true);
          retval = tree.hasChanges();
          tree.popChangeBuffer();
        }
      } else {
        if( (fs.getModflags(f) & ForeignMetadata.DATA_EQ_LOCALVER) == 0)
          return true;
        //if( getVersion() < Constants.FIRST_VERSION )
        //  return true;
        LocalTree tree = fs.getLocalTree();
        retval = tree.hasChanges(); // For uncomitted, but already scanned chgs
        if( !retval ) {
          //Log.log("Before treepush: " + tree.hasChanges(), Log.INFO);
          tree.pushChangeBuffer();
          tree.ensureLocalAndLiveInSync(null, false);
          retval = tree.hasChanges();
          tree.popChangeBuffer();
          //Log.log("After treepop: " + tree.hasChanges(), Log.INFO);
        }
      }
      Log.log((link ? "Link tree " : "Local tree ") +
              (retval ? "CHANGED" : "has no changes"), Log.INFO);
      return retval;
    }
    return isModified(link ? ForeignMetadata.DATA_EQ_LINKVER :
                      ForeignMetadata.DATA_EQ_LOCALVER);
  }

  public boolean isMetadataModified(boolean link) throws FileNotFoundException {
    return getObjectProvider().isMetadataModified(this, link);
  }
  
  public boolean isMetadataModified(fc.syxaw.fs.SyxawFile _,boolean link) throws FileNotFoundException {
    return isModified(link ? ForeignMetadata.META_EQ_LINKVER :
                      ForeignMetadata.META_EQ_LOCALVER);
  }

  protected boolean isModified(int flags) throws FileNotFoundException {
    if (!f.exists())
      throw new FileNotFoundException(toString());
    return (fs.getModflags(f) & flags) != flags;
  }

  public void createLink(String target) throws IOException {
    // copied from hierfs.SyxawFile
    if (exists())
      throw new IOException("Link destination file already exists.");
    if (!createNewFile())
      throw new IOException("Cannot create linked file.");
    if (!setLink(target, new Integer(Constants.NO_VERSION), new Boolean(false),
                 new Boolean(false), true, true))
      throw new IOException("Failed to set link.");
  }

  public void mount(String target) throws IOException {
    if (!f.equals(ServerConfig.ROOT_FOLDER_FILE))
      throw new IOException(
              "Only root mounting supported in this version of Syxaw");
    if (!setLink(target, new Integer(Constants.NO_VERSION), new Boolean(false),
                 new Boolean(false), true, true))
      throw new IOException("Failed to set link.");
  }

  public void sync() throws SynchronizationException, IOException {
    sync(true, true);
  }
  
  public void sync(boolean getData, boolean getMetadata) throws SynchronizationException, IOException {
    boolean isDir = isDirectory();
    /*NOCHANGED    if( fs.getChangeD() != null ) {
        fs.getChangeD().stop();
      }*/
    if (isDir) {
      fs.getLinkTree().setMdOnly(getMetadata && !getData);
      fs.getLinkTree().ensureLocalAndLiveInSync(new HashMap(), true);
    }
    int error = SynchronizationEngine.getInstance().synchronize(this, getData, getMetadata);
    /*NOCHANGED     if( fs.getChangeD() != null ) {
        fs.getChangeD().start(null);
      }*/
    if (error != fc.syxaw.protocol.TransmitStatus.OK)
      throw new SynchronizationException("Sync error code", error);
  }


  public void umount() throws IOException {
    if (!isDirectory() || Util.isEmpty(getLink()))
      throw new IOException("Not mounted " + f);
    String[] entries = list();
    for (int i = 0; i < entries.length; i++)
      Util.delTree(newInstance(this, entries[i]));
    if (!setLink("", new Integer(Constants.NO_VERSION), new Boolean(false),
                 new Boolean(false), true, true))
      throw new IOException("Failed to unset link.");
    fs.setLinkDataVersion(f, Constants.NO_VERSION);
    fs.setLinkMetaVersion(f, Constants.NO_VERSION);
  }

// Version management
  
  public FullMetadata commit(boolean data, boolean metadata) throws IOException {
    // Committing local facet
    if (isDataModified(false) || isMetadataModified(false)) {
      InputStream is = null;
      Lock l = null;
      try {
        l = lock();
        is = !isDirectory() && isDataModified(false) ? getInputStream() : null;
        FullMetadata md = getFullMetadata();
        Repository r = getObjectProvider().getRepository(this);
        md=r.commit(this, is, md, false, Constants.NO_VERSION, data, metadata);
        //getRepository(md.getType()).doCommit(Constants.NO_VERSION, this, false, data, metadata,
        //                                     is, md);
        // Update file metadata
        if (data) {
          fs.setDataVersion(f, md.getDataVersion());
        }
        if (metadata) {
          fs.setMetaVersion(f, md.getMetaVersion());
        }
        fs.setHash(f, md.getHash());
        setLocal(new Integer(md.getDataVersion()), new Boolean(false), new Boolean(false)); 
        // FIXME-versions: is dataVersion here alright?
        return md;
      } finally {
        l.release();
        if (is != null)
          is.close();
      }
    } else
      return getFullMetadata();
  }

  private Metadata commitLink(InputStream is, Metadata md, int version, boolean gotData, boolean gotMetadata) throws
          IOException {
    String link = getLink();
    if (Util.isEmpty(link)) {
      Log.log("commitLink when no link set!", Log.FATALERROR);
      return null;
    }
    FullMetadata mdRo = null;
    FullMetadataImpl fmd = FullMetadataImpl.createFrom(getFullMetadata());
    int oldLocalVer = fmd.getDataVersion(); // FIXME: BUGFIX-20061117-2 This was not set properly
    fmd.assignFrom(md);
    Repository r = getObjectProvider().getRepository(this);
    if( r instanceof OldRepository )
      mdRo=r.commit(this, is, fmd, true, version, gotData, gotMetadata);
     else {
       mdRo=r.commit(this, is, fmd, true, Constants.NO_VERSION, gotData, gotMetadata);
     if( version != Constants.NO_VERSION )
        vmapper.map(this, version, mdRo.getDataVersion());
      FullMetadataImpl md2 = FullMetadataImpl.createFrom(mdRo);
      md2.setLinkDataVersion(version);  // FIXME-versions: What about metaVersions in this method?
      mdRo = md2;
    }
    // we lost equality to localver (as new data was passed
    // to this commit call
    if( mdRo.getDataVersion() != oldLocalVer )
      setLocal( new Integer(mdRo.getDataVersion()), new Boolean(false),
                new Boolean(false));
    else
      setLocal( null , new Boolean(true), new Boolean(true));
    setLink(new Integer(version), new Boolean(false), new Boolean(false), gotData, gotMetadata);
    return mdRo;
  }

  public Metadata commitLink(int version, boolean gotData, boolean gotMetadata) throws IOException {
    // .hierfs copypaste
    Lock l = null;
    InputStream in = null;
    Metadata md = null;
    try {
      l = lock();
      in = isDirectory() ? null : getLinkInputStream();
      md = commitLink(in, getMetadata(), version, gotData, gotMetadata);
    } finally {
      l.release();
      if (in != null)
        in.close();
    }
    Log.log("Link data mod is "+isDataModified(true),Log.DEBUG);
    return md;
  }

  public int getNextVersion() throws FileNotFoundException {
   return getObjectProvider().getRepository(this).getNextVersion(this);
  }

  public int getLinkDataVersion() throws FileNotFoundException {
    // copypaste from hierfs!
    ForeignMetadata m = fs.getMetadata(this.f);
    if (m == null)
      throw new FileNotFoundException(toString());
    return m.getLinkDataVersion();
  }
  
  public int getLinkMetaVersion() throws FileNotFoundException {
    // copypaste from hierfs!
    ForeignMetadata m = fs.getMetadata(this.f);
    if (m == null)
      throw new FileNotFoundException(toString());
    return m.getLinkMetaVersion();
  }

  // Not official, should probably be (given getLinkVersion())
  private int getVersion() throws FileNotFoundException {
    ForeignMetadata m = fs.getMetadata(this.f);
    if (m == null)
      throw new FileNotFoundException(toString());
    return m.getDataVersion();
  }

  public VersionHistory getVersionHistory() {
    try {
      return getObjectProvider().getRepository(this).getVersionHistory(this);
    } catch (FileNotFoundException x) {
      Log.log("File disappeared? "+this,Log.WARNING);
    }
    return null;
  }

  public VersionHistory getLinkVersionHistory(boolean meta) {
    try {
      Repository repo = getObjectProvider().getRepository(this);
      if (repo instanceof OldRepository) {
        return ((OldRepository) repo).getLinkVersionHistory(this);
      } else
        return vmapper.getLinkVersionHistory(this, repo.getVersionHistory(this));
    }  catch (FileNotFoundException x) {
      Log.log("File disappeared? "+this,Log.WARNING);
    }
    return null;
  }

  // Storages
  public BLOBStorage createStorage(String purposeHint, boolean isTemporary) throws
    IOException {
    return getObjectProvider().createStorage(this, purposeHint, isTemporary);
  }
  
  public BLOBStorage createStorage(fc.syxaw.fs.SyxawFile _,String purposeHint, boolean isTemporary) throws
          IOException {
    if (isDirectory())
      // Dir storages are held in memory. (since they need to fit there anyway)
      return new fc.syxaw.storage.hfsbase.BLOBStorageImpl(
              fs.getLocalTree().getDeltaOutputStream(false), isTemporary);
    File storage = new File(Toolkit.TEMP_FOLDER_FILE,
                            /*f.isDirectory() ? f : f.getParentFile(),*/
                            f.getName() + "-" + purposeHint + "-" +
                            Integer.toHexString(rnd.nextInt() | 0x80000000));
    if (storage.exists())
      Log.log("Storage name collision", Log.ASSERTFAILED);
    return new fc.syxaw.storage.hfsbase.BLOBStorageImpl(storage,
            isTemporary);
  }

  public void rebindStorage(BLOBStorage s) throws IOException {
    getObjectProvider().rebindStorage(this, s, false);
  }

  public void rebindStorage(fc.syxaw.fs.SyxawFile _,BLOBStorage s, boolean linkFacet) throws IOException {
    rebindStorage(s, linkFacet, f);
  }

  public void rebindLinkStorage(BLOBStorage s) throws IOException {
    getObjectProvider().rebindStorage(this, s, true);
  }

  protected void rebindStorage(BLOBStorage s, boolean linkFacet, File src) throws
          IOException {
    // FIXME-W: Rebinding by rename not possible, as that would change the inode
    // of src, which we currently rely on
    if (isDirectory()) {
      OutputStream ss = s.getOutputStream(false);
      if (!(ss instanceof FacetedTree.TreeOutputStream))
        Log.log("Expected a TreeOutputStream", Log.ASSERTFAILED);
      try {       
        ((FacetedTree.TreeOutputStream) ss).apply();
        if (!Config.TRUE_DIR_MODFLAGS) {
          setLink(null, new Boolean(true), new Boolean(true), true, true);
          setLocal(null, new Boolean(true), new Boolean(true));
        }
      } catch (NodeNotFoundException ex) {
        throw new IOException("Missing node: " + ex.getId());
      }
      return ; // Return, man, return for god's sake!
    }
    OutputStream out = null;
    InputStream in = null;
    try {
      out = linkFacet ? getLinkOutputStream(false) : getOutputStream(false);
      in = s != null ? s.getInputStream() : new FileInputStream(src);
      Util.copyStream(in, out);
      out.close();
      out = null;
      in.close();
      in = null;
      if (s != null)
        s.delete();
      else if (!src.delete())
        throw new IOException("Can't remove temp storage " + src);
    } finally {
      if (out != null) out.close();
      if (in != null) in.close();
    }
  }

// Misc

  public Lock lock() {
    return new Lock();
  }

  public String toString() {
    return f != null ? getPath() : null;
  }

// Basic file ops

  public boolean canRead() {
    return getObjectProviderNoEx().canRead(this);
  }

  public boolean canRead(fc.syxaw.fs.SyxawFile _) {
    return f.canRead();
  }

  public boolean canWrite() {
    return getObjectProviderNoEx().canWrite(this);
  }

  public boolean canWrite(fc.syxaw.fs.SyxawFile _) {
    return f.canWrite();
  }

  public boolean delete() {
    return getObjectProviderNoEx().delete(this);
  }

  public boolean delete(fc.syxaw.fs.SyxawFile _) {
    boolean ok= fs.delete(f, getUid());
    if( ok ) {
      vmapper.clean(this);
    }
    return ok;
  }

  public boolean exists() {
    return f.exists()
      && f.getAbsolutePath().startsWith(ServerConfig.ROOT_FOLDER);
  }

  public String getName() {
    return f.getName();
  }

  public fc.syxaw.hierfs.SyxawFile getParentFile() {
    File p = f.getParentFile();
    return p == null ? null : newInstance(p);
  }

  public boolean isDirectory() {
    return f.isDirectory() && f.exists()
        && fc.syxaw.storage.hfsbase.Config.DIRECTORY_TYPE.equals(fs.getType(f));
  }

  public boolean isFile() {
    return f.isFile();
  }

  public abstract String[] list();

  public boolean mkdir() {
    return f.mkdir();
  }

  public boolean renameTo(fc.syxaw.hierfs.SyxawFile dest) {
    return f.renameTo(((AbstractSyxawFile) dest).f);
  }

  public fc.syxaw.hierfs.SyxawFile getChildFile(String child) {
    return newInstance(this, child);
  }

  // Mergers
  public ObjectMerger getLinkObjectMerger() throws FileNotFoundException {
    return getObjectProvider().getLinkObjectMerger(this);
  }

  public ObjectMerger getLinkObjectMerger(fc.syxaw.fs.SyxawFile _) throws FileNotFoundException {
    ObjectMerger merger = Toolkit.getInstance().getMerger(this, true);
    if (merger == null) {
      merger = new DefaultObjectMerger(this, getLinkVersionHistory(false), 
                                       getLinkDataVersion()); //FIXME-versions:
    }
    return merger;
  }

  // --- NFSD specific interface

  public String getAbsolutePath() {
    if( query != null )
      return f.getAbsolutePath() + querySeparator + query;
    return f.getAbsolutePath();
  }

  public RandomAccessFile getRandomAccessFile(String mode) throws
          FileNotFoundException {
    return new RandomAccessFile(f, mode);
  }

  public long lastModified() {
    return _n95_cap_modtime(f.lastModified());
  }

  public long length() {
    return f.length();
  }


  // Dent interface --- should it be here...?

  StringKey getDent() {
    return fs.getNodeId(f);
  }

  void setDent(StringKey dent) {
    fs.setNodeId(f, dent);
  }


  /** Get human-readable name for file. Typically a path rather than an UID.
   *
   * @return human-readable name
   */

  public String getNameFor() {
    try {
      return fs.getLocalTree().getFile(getDent()).getPath();
    } catch (NodeNotFoundException x) {
      return "<id: " + getDent() + ">";
    }
  }

  String getPath() {
    if( query != null )
      return f.getPath() + querySeparator + query;
    return f.getPath();
  }


  long getMetaModtime() {
    return fs.getMetaModTime(f);
  }

  /** Factory for link directory mergers.
   */

  static class DirectoryMergerFactory implements ObjectMerger.MergerFactory {
    public ObjectMerger newMerger(fc.syxaw.fs.SyxawFile f,
                                  boolean linkFacet) throws
            FileNotFoundException {
      if (!linkFacet) {
        Log.log("This release of Syxaw only support merging of the link facet",
                Log.ASSERTFAILED);
      }
      SyxawFile file = (SyxawFile) f;
      Version v = new Version(file.getLinkDataVersion(),file.getBranch());//FIXME-versions:
      fs.getLinkTree().getLocalVersion(v);
      return new DirectoryMerger(file, file.getLinkVersionHistory(false),
                                 fs.getLinkTree(),v,
                                 fs.getLinkTree().getTaintedUIDs());

    }

  }

  public boolean createNewFile() throws IOException {
    return createNewFile(null);
  }

  // Flags should be suitable for local file creation!
  public boolean createNewFile(UID uid) throws IOException {
    boolean ok = fs.create(f,uid);
    if( ok ) {
      // initial 0-byte version, flags according to this
      fs.setDataVersion(f, Constants.NO_VERSION);
      fs.setMetaVersion(f, Constants.NO_VERSION);
      fs.setLinkDataVersion(f, Constants.NO_VERSION);
      fs.setLinkMetaVersion(f, Constants.NO_VERSION);
      fs.setModflags(f,0);
    }
    return ok;
  }

  public ISyxawFile getConflictingFile() throws IOException {
    if( !exists() )
      throw new FileNotFoundException( f.getAbsolutePath() /* toString()*/);
    return newInstance(
         new File( Toolkit.CONFLICT_FOLDER_FILE,
                   getUid().toBase64() ));
  }

  public ISyxawFile getConflictLog() throws IOException {
    if( !exists() )
      throw new FileNotFoundException(toString());
    return newInstance( new File(
          Toolkit.CONFLICT_FOLDER_FILE, getUid().toBase64()+".log" ));
  }

  public boolean hasConflicts() throws IOException {
    return ((SyxawFile) getConflictingFile()).exists();
  }

  public void conflictsResolved() throws IOException, SynchronizationException {
    if( !hasConflicts() )
      throw new SynchronizationException("File is not in conflict",
                                         StatusCodes.NO_CONFLICT);
    // Update linkversion for current file
    SyxawFile cf = (SyxawFile) getConflictingFile();
    SyxawFile cl = (SyxawFile) getConflictLog();
    Integer linkVer = new Integer(cf.getLinkDataVersion());//FIXME-versions:
    if (cl.exists() && !cl.delete())
      throw new IOException("Can't remove conflict log");
    if( !cf.delete() )
      throw new IOException("Can't remove conflicting file");
    if( !setLink( linkVer,
            new Boolean(false),new Boolean(false), true, true) )
      Log.log("Can't update linkVer",Log.FATALERROR);
  }

  // Public local facet modflag manipulator. Consider making part of the
  // syxaw.fs interface
  public boolean setLocal(Integer version, Boolean metaModified,
                         Boolean dataModified ) {

   if( !f.exists() )
     return false;
   if( version != null ) {
     MutableForeignMeta fmd = getMutableForeignMeta();
     fmd.setDataVersion(version.intValue());
     fmd.setMetaVersion(version.intValue());
     fs.setMetadata(f,fmd,false);
   }
   if (metaModified != null) {
     if (metaModified.booleanValue())
       fs.setModflags(f, fs.getModflags(f) & (~ForeignMetadata.META_EQ_LOCALVER));
     else
       fs.setModflags(f, fs.getModflags(f) | ForeignMetadata.META_EQ_LOCALVER);
   }
   if (dataModified != null) {
     if (dataModified.booleanValue())
       fs.setModflags(f, fs.getModflags(f) & (~ForeignMetadata.DATA_EQ_LOCALVER));
     else
       fs.setModflags(f, fs.getModflags(f) | ForeignMetadata.DATA_EQ_LOCALVER);
   }
   return true;
  }


  protected ObjectProvider getObjectProviderNoEx()  {
    try {
      ObjectProvider op = (ObjectProvider) objectProviders.get(
          getFullMetadata(this).getType()); 
      return op == null ? this : op;
    } catch (FileNotFoundException e) {
      Log.info("Using default object provider for missing file");
      return this;
    }
  }

  // Get any installed object provider
  // If no object provider is returned, return a provider that does the 
  // default thing. The easiest, but a bit ugly way, to get a default 
  // implementation was to let this class implement ObjectProvider, 
  // but in a manner that disregards the passed file parameter.
  // (I.e. an instance of this object is an ObjcetProvider for the file
  // it encapsulates, no matter what file is passed to the ObjProvider methods)

  public ObjectProvider getObjectProvider() throws FileNotFoundException {
    ObjectProvider op = (ObjectProvider) objectProviders.get(
        getFullMetadata(this).getType()); // NOTE: We cannot use filtered meta here! 
    return op == null ? this : op;
  }
  
  protected abstract AbstractSyxawFile newInstance(File f);

  public void setBranch(String branch) {
    MutableForeignMeta fmd = getMutableForeignMeta();
    fmd.setBranch(branch);
    fs.setMetadata(f,fmd,false);
  }

  public String getBranch() {
    return fs.getMetadata(f).getBranch();
  }
  
  public Repository getRepository(fc.syxaw.fs.SyxawFile _) throws FileNotFoundException {
    String type = getMetadata().getType();
    OldRepository r = (OldRepository) repositories.get(type);
    if( r == null )
      r = (OldRepository) repositories.get(null);
    return r;
  };

  public void setBaseProvider(ObjectProvider op) {
    throw new IllegalStateException(); // Should never be called
  }
  
  private static class DirectoryRepository implements OldRepository {

    public FullMetadata commit(fc.syxaw.fs.SyxawFile f, InputStream data, 
          FullMetadata mdRo,  boolean linkFacet, int version, boolean gotData, boolean gotMetadata) throws
            IOException {
      FullMetadataImpl md = FullMetadataImpl.createFrom(mdRo);
      if (!linkFacet) {
        if (data != null)
          Log.log("Can't currently commit dirs as inputstreams",
                  Log.ASSERTFAILED);
        LocalTree lt = fs.getLocalTree();
        if (!FIXME_COMMIT_RECEIVED_NOT_CURRENT)
          lt.ensureLocalAndLiveInSync(null, false);
        lt.commit(Constants.NO_VERSION);
        if (gotData) {
          md.setDataVersion(lt.getCurrentVersion());
        }
        if (gotMetadata) {
          md.setMetaVersion(lt.getCurrentVersion());
        }
      } else {
        // NOTE: See notes in LocalTree.getDeltaOS
        // The "clean" way would to be to stream data to linktree.getos ->
        // restructure dirtree
        // + then a commit() of the localtree to the repo
        LinkTree currentTree = fs.getLinkTree();
        try {
          currentTree.commit(new Version(version,
                                         f.getFullMetadata().getBranch()));
        } catch (IOException ex) {
          Log.log("Can't commit new tree...", Log.ASSERTFAILED, ex);
        }
        if (gotData) {
        md.setLinkDataVersion(currentTree.getCurrentVersion());
        md.setDataVersion(fs.getLocalTree().getCurrentVersion());
        }
        if (gotMetadata) {
          md.setLinkMetaVersion(currentTree.getCurrentVersion());
          md.setMetaVersion(fs.getLocalTree().getCurrentVersion());
        }
        
      }
      return md;
    }

    public VersionHistory getVersionHistory(fc.syxaw.fs.SyxawFile f) {
      return fs.getLocalTree().getVersionHistory();
    }


    public VersionHistory getLinkVersionHistory(fc.syxaw.fs.SyxawFile f) { // FIXME-versions: boolean meta?
      return fs.getLinkTree().getVersionHistory();

    }
    
    
    public int getNextVersion(fc.syxaw.fs.SyxawFile f) throws FileNotFoundException {
      return fs.getLocalTree().getNextVersion();
    }
   
  }


  private static class FakeRepository implements OldRepository {
  
    public FullMetadata commit(fc.syxaw.fs.SyxawFile f, InputStream data,
          FullMetadata mdRo, boolean linkFacet, int version, boolean gotData, boolean gotMetadata) throws
              IOException {
      FullMetadataImpl md = FullMetadataImpl.createFrom(mdRo);
      if (linkFacet) {
        if (gotData) {
          md.setLinkDataVersion(version);// FIXME-versions: figure out which versions were updated, set those
        }
        if (gotMetadata) {
          md.setLinkMetaVersion(version);
        }
      } else {        
        if (gotData) {
          int oldDver = md.getDataVersion();
          int newDver = oldDver < Constants.FIRST_VERSION ? Constants.FIRST_VERSION :
                       oldDver + 1;
        md.setDataVersion(newDver);
        }
        if (gotMetadata) {
          int oldMver = md.getMetaVersion();
          int newMver = oldMver < Constants.FIRST_VERSION ? Constants.FIRST_VERSION :
            oldMver + 1;
          md.setMetaVersion(newMver);
        }
      }
      return md;
    }

    public VersionHistory getVersionHistory(fc.syxaw.fs.SyxawFile f) {
      return new FakeVersionHistory(f, false, false); // local, no meta flag
    }

    public VersionHistory getLinkVersionHistory(fc.syxaw.fs.SyxawFile f) {
      return getLinkVersionHistory(f, false);
    }
    
    public VersionHistory getLinkVersionHistory(fc.syxaw.fs.SyxawFile f, boolean meta) {// FIXME-versions
      String link = f.getLink();
      if (link == null)
        return null;
      return new FakeVersionHistory(f, true, meta);
    }
    
    public int getNextVersion(fc.syxaw.fs.SyxawFile f) throws FileNotFoundException {
      ForeignMetadata md = 
        fs.getMetadata(((AbstractSyxawFile) f).f);
      if (md == null)
        return Constants.NO_VERSION;
      if(md.getDataVersion() < Constants.FIRST_VERSION )// FIXME-versions
        return Constants.FIRST_VERSION;
      return  md.getDataVersion() + 1;
    }
    
  }


  private static class BasicRepository implements OldRepository {
  
      VersionDB db = new VersionDB(Toolkit.VERSION_FOLDER_FILE);
  
    public FullMetadata commit(fc.syxaw.fs.SyxawFile f, InputStream data, FullMetadata md,
                                    boolean link, int version , boolean gotData, boolean gotMetadata) throws
              IOException {
      md=db.commit(getKey(link, f), md, data,
                  link ? version : Constants.NO_VERSION);
        return md;
      }
  
    public VersionHistory getVersionHistory(fc.syxaw.fs.SyxawFile f) {
        if( !Version.TRUNK.equals(f.getBranch() ) )
            //No branch support yet
            throw new NotInPrototypeException();
      return db.getHistory(getKey(false,f));
    }


    public VersionHistory getLinkVersionHistory(fc.syxaw.fs.SyxawFile f) {// FIXME-versions: boolean meta?
      if( !Version.TRUNK.equals(f.getBranch() ) )
          //No branch support yet
          throw new NotInPrototypeException();
      return db.getHistory(getKey(true,f));
    }

    public int getNextVersion(fc.syxaw.fs.SyxawFile f) throws FileNotFoundException {
      ForeignMetadata md = 
        fs.getMetadata(((AbstractSyxawFile) f).f);
      if (md == null)
        return Constants.NO_VERSION;
      if(md.getDataVersion() < Constants.FIRST_VERSION ) // FIXME-versions
        return Constants.FIRST_VERSION;
      return  md.getDataVersion() + 1;
    }    

    protected GUID getKey(boolean link, fc.syxaw.fs.SyxawFile f) {
      if( link )
        return new GUID(f.getLink());
      else
        return new GUID(GUID.getCurrentLocation(),f.getUid());
    }
  }

  // KLUDGE: Cap modtimes that are completely out of range. At least the Nokia
  // 9500 .lastModified method is known to return bogus modtimes
  private static int _mt_kludge_cap_count = 5;
  
  public static final long _n95_cap_modtime(long modTime) {
    if( modTime > 0x7fffffffl*1000l  ) {
      if( _mt_kludge_cap_count-- > 0)
        Log.log("Capping insane modtime "+modTime+ 
            "(msg muted after "+_mt_kludge_cap_count+" more)",Log.WARNING);
      modTime = 0x7fffffffl*1000l-1 ;
    }
    return modTime;
  }
}
// arch-tag: d434dca314eba3fc0a6a4972e0d46498 *-
