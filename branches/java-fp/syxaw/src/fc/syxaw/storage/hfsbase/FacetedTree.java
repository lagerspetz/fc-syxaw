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

// $Id: FacetedTree.java,v 1.1 2005/06/08 13:14:27 ctl Exp $
// Was preiviously in storage/xfs as
// Id: FacetedTree.java,v 1.24 2005/06/08 12:46:12 ctl Exp

package fc.syxaw.storage.hfsbase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import fc.raxs.DeltaStream;
import fc.raxs.NoSuchVersionException;
import fc.syxaw.fs.Config;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.GUID;
import fc.syxaw.fs.UID;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.hierfs.DirectoryEntry;
import fc.syxaw.hierfs.DirectoryTreeOutputStream;
import fc.syxaw.hierfs.DirectoryTreeStore;
import fc.syxaw.proto.Version;
import fc.syxaw.util.Cache;
import fc.syxaw.util.LruCache;
import fc.syxaw.util.Util;
import fc.syxaw.util.XmlUtil;
import fc.util.io.DelayedInputStream;
import fc.util.log.Log;
import fc.xml.xas.ItemSource;
import fc.xml.xas.XmlOutput;
import fc.xml.xmlr.AbstractMutableRefTree;
import fc.xml.xmlr.ChangeTree;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTreeImpl;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTreeNodeImpl;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.Reference;
import fc.xml.xmlr.TreeReference;
import fc.xml.xmlr.model.StringKey;
import fc.xml.xmlr.model.TreeModels;
import fc.xml.xmlr.tdm.Diff;
import fc.xml.xmlr.xas.XasSerialization;

// Description of KLUDGE050202:
// Reason:
// The idea of apply()ing the live tree to the link/local facet seems
// fundamentally flawed, as the live tree contains more data than can be
// fit in either facet (e.g. uid and linkuid). It appears that the livetree
// should always be applied to the VersionedDirTree directly
// This, of course, implies that no changes may be outstanding in either the
// local or linktree changebuffer. I'm not sure this is always the case, esp.
// when being client (local tree->changebuf, sync & merge->changebuf, THEN
// commit). Perhaps link & localtree should share a single changebuf (to
// which livetree would also write)
//
// The kludge: pass local uid from link facet in the locationid field.
// Needed to ensure UID in storedtree so that freeUID will work.

/** A mutable reftree that is a facet of another mutable directory reftree.
 * In addition, the faceted tree provides methods for applying the
 * MutableRefTree operations to a hierarchical file system.
 */

public abstract class FacetedTree extends AbstractMutableRefTree implements
    DirectoryTreeStore {

  // FIXME-Dessy ...
  public void setMdOnly(boolean md) {
    this.meta = md;
  }
  
  private boolean meta = false; 
  // ... FIXME-Dessy
  
  protected final VersionedDirectoryTree repo;
  protected AbstractSyxawFile rootDir;
  protected ChangeTree fullTree;
  protected boolean __toFs=true; //DEBUG 041216, FIXME-W: flag to temporarily
  // Disable change propagation to file system; better code is to have a mutable
  // facet of this tree (like LocalTree)

  private Stack changeBuffers = new Stack();

  private boolean firstEnsure= fc.syxaw.fs.Config.STARTUP_INITFS;

  protected Set linkModFiles=new HashSet();

  void pushChangeBuffer() {
    changeBuffers.push(fullTree);
    fullTree = new ChangeTree(fullTree);
  }

  void popChangeBuffer() {
    fullTree = (ChangeTree) changeBuffers.pop();
  }

  /** Create a new faceted tree.
   *
   * @param root directory corresponding to the root node of this tree
   */
  protected FacetedTree(AbstractSyxawFile root, VersionedDirectoryTree repo) {
    this.repo = repo;
    rootDir = root;
  }

    class DeltaInputStream extends DelayedInputStream implements DeltaStream {

      int baseVersion = Constants.NO_VERSION;
      RefTree t = FacetedTree.this;

      public DeltaInputStream() {
        super();
      }
      public void setBaseVersion(int version) throws NoSuchVersionException {
        if (Util.arrayLookup(
                Version.getNumbers(
                getVersionHistory().getFullVersions(Version.CURRENT_BRANCH)),
                version,
                version + 1) == -1)
          throw new NoSuchVersionException(version);
        baseVersion = version;
      }

      public long getDeltaSize() {
        return -1L;
      }

      protected void stream(OutputStream out) throws IOException {
        try {
          if (baseVersion == Constants.NO_VERSION) {
//            Log.log("CANT USE DELTA",Log.WARNING);
            t = FacetedTree.this;
            XmlUtil.writeRefTree(t,out,DirectoryEntry.XAS_CODEC);
            // DEBUG
            /*Log.log("Tree to send is:",Log.INFO);
            try{XmlUtil.writeRefTree(t,System.err,DirectoryEntry.XAS_CODEC);
            } catch(Exception x) {}
            System.err.flush();*/

          } else {
            t = getRefTree(Constants.CURRENT_VERSION, baseVersion);
            Diff d =
               fc.syxaw.storage.hfsbase.Config.NO_XMLR_DELTA ? null :
               Diff.encode(getTree(baseVersion), t);
/*
            Log.log("Dumping delta("+baseVersion+",CURRENT_VERSION (last committed="+getCurrentVersion()+")):",Log.INFO);
            XmlWriter wd = XmlUtil.getXmlWriter(System.err);
            wd.addEvent(Event.createStartDocument());
            wd.addEvent(Event.createNamespacePrefix(ReferenceEvent.REF_NS,
                "ref"));
            d.writeDiff(wd, DirectoryEntry.XAS_CODEC);
            wd.addEvent(Event.createEndDocument());
            wd.flush();
            Log.log("--end-of-dump--",Log.INFO);
            System.err.flush();*/
            // DEBUG ENDS
            XmlOutput xo = XmlUtil.getXmlSerializer(out);
            /* FIXME-20061016-1
            w.addEvent(Event.createNamespacePrefix(ReferenceEvent.REF_NS,
                "ref"));*/
            if( fc.syxaw.storage.hfsbase.Config.NO_XMLR_DELTA )
              XasSerialization.writeTree(t,xo,DirectoryEntry.XAS_CODEC);
            else
              d.writeDiff(xo, TreeModels.xmlr1Model().swapCodec(
                  DirectoryEntry.XAS_CODEC ) );
            xo.flush();
          }
        } finally {
          out.close();
        }
      }
      public int getBaseversion() {
        return baseVersion;
      }

    }
    /** Get XML delta input stream for this tree.
     *
     * @return delta input stream to the XML representation of this tree
     */
    public InputStream getDeltaInputStream() {

    return new DeltaInputStream();
  }

  /** Get version history of the tree.
   *
   * @return VersionHistory of the tree
   */
  public abstract VersionHistory getVersionHistory();

  /** Get current version of the tree. The current version is the last version
   * assigned on commit. The tree may have uncommitted changes.
   *
   * @return current version
   */
  public abstract int getCurrentVersion();

  // ignorebefore = map of UID, minmodtime, which means that any dirty entries whose
  // modftime < minmodtime should not be tainted (used to ignore taints caused by commit)

  /** Synchronize this tree with the XFS live tree. Any changes on the
   * XFS live tree are applied to this tree.
   *
   * @param ignoreBefore Map of <UID,long modtime> of modified UIDs to
   * potentially ignore as having been modified. An entry <code>e</code>
   * is ignored if <code>e.UID.getModTime() <= e.modtime</code>. Only used
   * if the experimental change daemon is enabled!
   */
  public void ensureLocalAndLiveInSync(Map ignoreBefore, boolean linkFacet) {
    aliases.clear(); // Aliases no longer valid when we sync live->facetedTree
    LiveTree currentLive =
        getLiveTree((!Config.DEBUG_DIRMODFLAG && !firstEnsure ) ? 0:
                    Integer.MAX_VALUE,linkFacet);
    firstEnsure = false;
    // DEBUG041216 START, Note: beware of IDENTICAL_CHILDLIST, it won't print
    /*
    Log.log("Change tree is:",Log.WARNING);
    try{XmlUtil.writeRefTree(currentLive,System.out,DirectoryEntry.XAS_CODEC);
    } catch(Exception x) {
      Log.log("Except on dump", Log.ERROR, x);
    }
    System.out.flush();
    Log.log("Link tree is:",Log.INFO);
    try{XmlUtil.writeRefTree(this,System.out,DirectoryEntry.XAS_CODEC);
    } catch(Exception x) {
      Log.log("Except on dump", Log.ERROR, x);
    }
    System.out.flush();

    Log.log("Change to repo tree is:",Log.INFO);
    try{XmlUtil.writeRefTree(fullTree.getChangeTree(),System.out,DirectoryEntry.XAS_CODEC);
    } catch(Exception x) {
      Log.log("Except on dump", Log.ERROR, x);
    }
    System.out.flush();
    
    Log.log("Repo tree is:",Log.INFO);
    try{XmlUtil.writeRefTree(repo,System.out,DirectoryEntry.XAS_CODEC);
    } catch(Exception x) {
      Log.log("Except on dump", Log.ERROR, x);
    }
    System.out.flush();
    */
    // DEBUG END
    try {
      if( !Config.DEBUG_DIRMODFLAG )
        currentLive.setModflagReset();
      __toFs = false;
      RefTrees.apply(currentLive, this);
      Log.log("Scanned tree node instances: "+currentLive.__nodes,Log.INFO);
      /*Log.log("After-apply link tree is:",Log.INFO);
      try{XmlUtil.writeRefTree(this,System.err,DirectoryEntry.XAS_CODEC);
      } catch(Exception x) {}
      System.err.flush();
      */
      __toFs = true;
    } catch (NodeNotFoundException ex) {
      Log.log("Broken node ids",Log.FATALERROR,ex);
    }

    linkModFiles.clear();
    linkModFiles.addAll(currentLive.getLinkModFiles());

    // Bump up version number for all dirty UIDs
    /** 041216: Changed support disabled
    if( changed != null ) {
      try {
        UID[] dirty=changed.getUpdatedFiles();
        Log.log("Dirty UIDs=",Log.INFO,changed.getUpdatedFiles());
        for( int i=0;i<dirty.length;i++) {
          try {
            SyxawFile f = new SyxawFile(dirty[i]);
            String id =  f.getDent();
            DirectoryEntry e = (DirectoryEntry) getNode(id).getContent();
            UID uid = UID.createFromBase64( e.getUid() );
            if (ignoreBefore.containsKey(uid)
                && ( (Long) ignoreBefore.get(uid)).longValue() > f.getMetaModtime()) {
              Log.log("Ignoring dirty UID " + uid + " margin (msec) " +
                      ( ( ( (Long) ignoreBefore.get(uid)).longValue() -
                         f.getMetaModtime())), Log.INFO);
              continue;
            }
            f.setLocalModified(); // Don't forget this amidst all!
            e = new DirectoryEntry.DirectoryEntryImpl(e.getType(), e.getId(),
                e.getLocationId(), e.getName(), e.getNextId(), e.getUid(),
                getCommitVersion(e.getVersion(),true));
            update(id,e);
          } catch (Exception x) {
            Log.log("Dirty bump excepted with ",Log.WARNING,x);
          }
        }
        changed.reset();
      } catch( RemoteException x) {
        Log.log("Change-d excepted",Log.FATALERROR,x);
      }
    }*/
  }

  public boolean hasChanges() {
    if( fullTree.hasChanges() ) {
      Log.log("Changes found.",Log.INFO);
      if( fc.syxaw.fs.Config.DEBUG_DUMPDELTA ) {
        Log.log("Dump of repo, current facetedtree follows",Log.INFO);
        try {
          XmlUtil.writeRefTree(repo, System.err,
                               new StoredDirectoryTree.NodeXasCodec());
          XmlUtil.writeRefTree(this, System.err, DirectoryEntry.XAS_CODEC);
        } catch (IOException ex) {
          Log.log("Dump bombed", Log.ERROR, ex);
        }
      }
    }
    return fullTree.hasChanges();
  }

  /* Could actually be private, but we use it for debug from SyxawFile */
  AbstractSyxawFile getFile(Key id) throws NodeNotFoundException {
    RefTreeNode n = getNode(id);
    if( n == null )
      throw new NodeNotFoundException(id);
    return getFile(n,( (DirectoryEntry) n.getContent()).getName());
  }

  // NOTE: Will not automagically relocate on collision
  private AbstractSyxawFile allocFile(Key parentId, String name, boolean create,
                                boolean isDir )
      throws NodeNotFoundException {
    AbstractSyxawFile parent = getFile(parentId);
    AbstractSyxawFile alloced = (AbstractSyxawFile)
                                parent.newInstance(parent,name);
    IOException createEx=null;
    boolean success=false;
    while( !success ) {
      if (create) {
        if (isDir && alloced.mkdir()) {
          success=true;
        } else
          try {
            success = alloced.createNewFile();
          } catch( IOException x) {
            createEx=x;
          }
      } else
        success = !alloced.exists();
      if( !success ) {
        if( create && !alloced.exists() )
          // Non-clash problem, give up
          Log.log("Can't create "+alloced,Log.FATALERROR,createEx);
        StringKey cfid = alloced.getDent();
        AbstractSyxawFile movedFile =
                (AbstractSyxawFile) parent.newInstance(parent,
            cfid+"-"+name);
        while( movedFile.exists() )
          movedFile=(AbstractSyxawFile) parent.newInstance(parent,
            cfid+"-"+name+"-"+
            Integer.toHexString((int) Math.random()*Integer.MAX_VALUE));
        if( !alloced.renameTo(movedFile) )
          Log.log("Failed to alias "+alloced+"->"+movedFile,Log.FATALERROR);
        Log.log("Aliased name of "+cfid+" from "+name+" to "+
                movedFile.getName(),Log.INFO);
        aliases.put(cfid,movedFile.getName());
      }
      // Get fresh object, since old one may point to wrong file
      alloced = (AbstractSyxawFile) parent.newInstance(parent,name);
    }
    return alloced;
  }


  protected void resetDirModFlags(AbstractSyxawFile root,boolean linkFacet) {
    if( !root.isDirectory() )
      return;
    try {
      if (root.isDataModified(linkFacet)) {
        String[] entries = root.list();
        for (int i = 0; i < entries.length; i++)
          resetDirModFlags(
                  (AbstractSyxawFile) root.newInstance(root, entries[i]),
                           linkFacet);
        if (linkFacet)
          root.setLink(null, Boolean.FALSE, Boolean.FALSE, true, true);
        else
          root.setLocal(null, Boolean.FALSE, Boolean.FALSE);
      }
    } catch( FileNotFoundException ex) {
      Log.log("File disappeared "+root,Log.ERROR);
    }
  }
  private LiveTree getLiveTree(int initialDepth, boolean linkFacet) {
    try {
      return new LiveTree(rootDir,initialDepth, linkFacet);
    } catch (FileNotFoundException ex) {
      Log.log("Fs root has disappeared?",Log.FATALERROR);
      return null;
    }
  }

  // Directory tree ops on file system

  protected AbstractSyxawFile insert(Key parentId, Key newId,
                             DirectoryEntry content, boolean linkFacet) throws
      NodeNotFoundException {
    //!A assert newId instanceof StringKey;
    AbstractSyxawFile newEntry = null;
    try {
      if (content.getType() == DirectoryEntry.DIR) {
        newEntry = allocFile(parentId,content.getName(),true,true);
        Log.log("Inserted new dir "+newEntry,Log.DEBUG);
      } else if (linkFacet && content.getType() == DirectoryEntry.FILE ) {
        newEntry = allocFile(parentId,content.getName(),true,false);
        Log.log("Inserted new file "+newEntry,Log.DEBUG);
      } else {
        newEntry = allocFile(parentId,content.getName(),false,false);
        if (!newEntry.createNewFile(UID.createFromBase64(content.getUid())))
          throw new IOException("Can't link name "+newEntry+" to UID "+content.getUid());
        Log.log("New file by UID, uid="+content.getUid()+
                " name="+newEntry,Log.DEBUG);
      }
      newEntry.setDent((StringKey) newId);
      if (linkFacet) {
        // insert by download, note order of flag-setting: the latter facet will
        // trigger modflags for the former facet!
        //Log.log("Insert by DOWNLOAD",Log.INFO);
        newEntry.setLocal(new Integer( Constants.NO_VERSION ),
                         new Boolean(true), new Boolean(true));
        newEntry.setLink(content.getUid(), new Integer( Constants.ZERO_VERSION ),
                         //new Integer(content.getVersion()),
                         new Boolean(false), new Boolean(false), true, true);

      } else {
        // insert by upload, note order of flag-setting: the latter facet will
        // trigger modflags for the former facet!
        //Log.log("Insert by UPLOAD",Log.INFO,new Throwable());
        newEntry.setLink(new Integer( Constants.NO_VERSION ),
                         //new Integer(content.getVersion()),
                         new Boolean(true), new Boolean(true), true, true);
        newEntry.setLocal(new Integer(Constants.ZERO_VERSION),
                          new Boolean(false), new Boolean(false));

      }
    } catch (IOException ex) {
      Log.log("Insert of " + newEntry + " failed.", Log.ERROR, ex);
      throw new NodeNotFoundException(parentId);
    }
    return newEntry;
  }

  protected boolean deleteFile( Key id ) throws NodeNotFoundException {
    AbstractSyxawFile delRoot = getFile(id);
    Log.log("Deleting tree "+delRoot,Log.DEBUG);
    try {
      Util.delTree(delRoot);
    } catch (IOException ex) {
      Log.log("Error deleting tree "+delRoot,Log.ERROR,ex);
      return false;
    }
    return true;
  }

  protected boolean update(Key id, String oldName,
                           DirectoryEntry content,
                           Set linkVersionChanged, boolean linkFacet) throws
      NodeNotFoundException {
    AbstractSyxawFile entry = getFile(id);
    if (content.getType() != DirectoryEntry.TREE) {
      // Only do stuff to dirs and files!
      if (!oldName.equals(content.getName())) {
        Log.log("Updating name of " + entry + " (orig. "+oldName+") to " +
                content.getName(),Log.INFO);
        /* FIXME-Dessy: This did not work, 
         it re-renamed already-renamed child files to null-filename.
         The code in updateIds in dessy.storage.FileSystem and 
         in renameTo in dessy.storage.SyxawFile seems to fix this, for now. 
        */ 
        AbstractSyxawFile oldEntry = entry;
        entry = allocFile(getParent(id),content.getName(),false,false);
        if (!oldEntry.renameTo(entry) ) {
          Log.log("Error updating name " + oldEntry + "->"+
                  entry, Log.ERROR);
          return false;
        }
      }
      if (linkFacet) {
        try {
          if (!Util.equals(entry.getLink(), content.getUid())) {
            // This happens on branch switching,
            // and when setting link of a local insert
            // Note that we do not set true ver; this is not set until dependent sync
            // Establish new link; note that verNo update to liveTree
            // won't happen trough this (instead its when the object itself is synced)
            Boolean setFlag =
                    fc.syxaw.fs.SynchronizationEngine.getInstance().
                    FIXMEPbaseIsCurrentLocalFacet ?
                    new Boolean(false) : null;
            if (!entry.setLink(content.getUid(),
                               new Integer(Constants.FIXMEP_RELINK_VERSION),
                               setFlag, setFlag, true, true))
              throw new FileNotFoundException();
            else if (linkVersionChanged != null &&
                     content.getType() == DirectoryEntry.FILE) {
              Log.log("Download due to re-link " + entry.getUid(), Log.INFO);
              linkVersionChanged.add(entry.getUid());
            }
          } else { // end if new link
            if (!meta && linkVersionChanged != null &&
                entry.getLinkDataVersion() < content.getVersion()) {//FIXME-versions:
              Log.log("Download due to new remote ver " + entry.getUid(),
                      Log.INFO);
              linkVersionChanged.add(entry.getUid());
            }
            if (meta && linkVersionChanged != null &&
                entry.getLinkMetaVersion() < content.getVersion()) {//FIXME-versions:
              Log.log("Download due to new remote ver " + entry.getUid(),
                      Log.INFO);
              linkVersionChanged.add(entry.getUid());
            }
          }
        } catch (FileNotFoundException x) {
          // should be recoverable to some extent
          Log.log("File update failed " + entry, Log.ERROR, x);
        }
      } // if link facet
    } // not TREE
    return true;
  }

  protected boolean moveFile(Key id, Key newParentId) throws
      NodeNotFoundException {
    AbstractSyxawFile entry=getFile(id);
    AbstractSyxawFile target = allocFile(newParentId,entry.getName(),false,false);
    Log.log("Moving "+entry+" -> "+target,Log.INFO);
    if( !entry.renameTo( target ) ) {
      Log.log("Error moving "+entry+"->"+target,Log.ERROR);
      return false;
    }
    return true;
  }

  private Map aliases=new HashMap();

  private AbstractSyxawFile getFile(RefTreeNode n, String name) {
    RefTreeNode p = n.getParent();
    if( p == null )
      return rootDir;
    String alias = (String) aliases.get(n.getId());
    if( alias != null )
      Log.log("Using alias "+alias+" for id "+n.getId(),Log.DEBUG);
    return (AbstractSyxawFile) rootDir.newInstance(getFile(p,
                          ((DirectoryEntry) p.getContent()).getName()),
                         alias != null ? alias : name);
  }
  
  public IdAddressableRefTree getNullBaseTree() {
    return RefTrees.getAddressableTree(new RefTreeImpl(new RefTreeNodeImpl(
        null, VersionedDirectoryTree.ROOT_ID,
        new DirectoryEntry.DirectoryEntryImpl(DirectoryEntry.TREE,
            VersionedDirectoryTree.ROOT_ID, GUID.getCurrentLocation(), null,
            "", null, Constants.FIRST_VERSION))));

  }

  protected abstract int getCommitVersion(int version, boolean modified);

  /**
   * The current file system as a reftree. Note that current Syxaw tree and the
   * current file system trees need to be synchronized in cases where there is
   * external (not induced by Syxaw synchronization) activity on the file
   * system.
   */

  protected class LiveTree implements RefTree {

    int __nodes=0; // Tree node counter, only used for debugging;
                   // Correct val only after first traversal
    Set modToLink = new HashSet(); // Set of UIDs that are modded wrt the link facet
    boolean resetModFlags=false;
    DentNode root;
    boolean linkFacet;
    Cache childListCache = new LruCache(Cache.TEMP_MED,64);
    
    protected LiveTree( AbstractSyxawFile root,int initialDepth,
                       boolean linkFacet) throws FileNotFoundException {
      this.linkFacet = linkFacet;
      this.root = new DentNode(null,root,initialDepth);
    }

    public Set getLinkModFiles() {
      Set retval= modToLink;
      modToLink = null; // Callable once! new HashSet();
      return retval;
    }

    public RefTreeNode getRoot() {
      return root;
    }

    // If set, tree traversal resets modflags
    // After this, the tree isn't traversable anymore in its original form!
    public void setModflagReset() {
      resetModFlags = true;
    }

    public class DentNode implements RefTreeNode, Comparable {

      AbstractSyxawFile f;
      DentNode parent;
      DirectoryEntry content;
      int expandCount;
      StringKey id=null;

      // ec = 0 produces a <ref:tree>
      public DentNode(DentNode parent, AbstractSyxawFile f, int aExpandCount) throws
          FileNotFoundException {
        __nodes++; // Count nodes
        this.expandCount = aExpandCount;
        this.f = f;
        this.parent = parent;
        boolean modified =
          (ServerConfig.CALC_DIRMOD && f.isDirectory()) ||
          f.isDataModified(linkFacet) ||
            f.isMetadataModified(linkFacet);
        int type = parent == null ? DirectoryEntry.TREE :
            (f.isDirectory() ? DirectoryEntry.DIR : DirectoryEntry.FILE);
        if( modified ) {
          expandCount=Math.max(2,expandCount); // Expand  (at least) this level and next level
          if( resetModFlags && type != DirectoryEntry.FILE ) {
            if (linkFacet)
              f.setLink(null, new Boolean(false), new Boolean(false), true, true);
            else
              f.setLocal(null, new Boolean(false), new Boolean(false));
          }
        }
        if( type == DirectoryEntry.FILE &&
            (f.isDataModified(true) || f.isMetadataModified(true) ) ) {
          Log.log("Link-modified object "+f.getUid(),Log.DEBUG);
          modToLink.add(f.getUid());
        }

        id = f.getDent();
        if( id == null ) {
          id = type == DirectoryEntry.TREE ? 
              VersionedDirectoryTree.ROOT_ID : repo.newId();
          f.setDent(id);
        }

        if( expandCount == 0) {
          //Log.log("===DTREF: "+f,Log.WARNING);
          content = null;
          return;
        }
        FullMetadata md = f.getFullMetadata();
        // Alloc dents on demand

        switch( type ) {
          case DirectoryEntry.FILE: {
            String uid = f.getUid().toBase64();
            content = new DirectoryEntry.DirectoryEntryImpl(type,
                id, uid, // FIXME, KLUDGE050202; See description at top of file
                f.getName(), null,
                linkFacet ? Util.nullToEmpty(f.getLink()) :
                uid,
                FacetedTree.this.getCommitVersion(
                linkFacet ? md.getLinkDataVersion() : // FIXME-versions:
                md.getDataVersion(),modified));
          } break;
          case DirectoryEntry.DIR: {
            content = new DirectoryEntry.DirectoryEntryImpl(type,
                id, null,
                f.getName(), null, null, Constants.NO_VERSION);
          } break;
          case DirectoryEntry.TREE: {
            content = new DirectoryEntry.DirectoryEntryImpl(type,
                id, GUID.getCurrentLocation(),
                f.getName(), "#nextid#", null, Constants.NO_VERSION);
          } break;

        }
      }

      public AbstractSyxawFile getFile() {
        return f;
      }

      public Iterator getChildIterator() {
        if( expandCount == 1) // node may be changed, but child list is not
          return RefTrees.IDENTICAL_CHILDLIST;
        return ensureChildList().iterator();
      }

      public Object getContent() {
        return content;
      }

      public Key getId() {
        return id;
      }

      public RefTreeNode getParent() {
        return parent;
      }

      public boolean isNodeRef() {
        return false;
      }

      public boolean isReference() {
        return expandCount == 0;
      }

      public boolean isTreeRef() {
        return expandCount == 0;
      }

      protected List ensureChildList() {
        if( isTreeRef() || !f.isDirectory() ) {
          return Collections.EMPTY_LIST;
        }
        List children = (List) childListCache.get(f.getPath());
        if( children != null )
          return children;
        String names[]=f.list();

        DentNode[] childArr = new DentNode[names.length];
        for( int i=0; i<names.length;i++) {
          try {
            childArr[i] =new DentNode(this,
              (AbstractSyxawFile) f.newInstance(f, names[i]),
                                      this.expandCount-1);
          } catch (FileNotFoundException x ) {
            Log.log("File mysteriously disappeared: " + f + "/" + names[i],
                    Log.WARNING, x);
            Log.log("This is a known bug on Symbian+J9.",Log.INFO);
            // Recover needs to fix the null ptr that we got into childArr[i]
            Log.log("Recovery from disappearing files unimplemented.",
                    Log.FATALERROR);

          }
        }
        Arrays.sort(childArr); // Sort by id
        children = Arrays.asList(childArr);
        childListCache.put(f.getPath(),children);
        return children;
      }

      public int compareTo(Object o) {
        return Util.STRINGS_BY_LENGTH.compare(this.getId(),
                                              ( (DentNode) o).getId());
      }

      public Reference getReference() {
        return isTreeRef() ? new TreeReference(getId()) : null;
      }
    }
  }
  
  /** Stream that applies XML directory trees. The stream
   * accepts directory trees as XML and knows how to apply the tree to the
   * local file system. The stream may encode either the link or
   * local facet dirtrees.
   */
  
  protected abstract class TreeOutputStream extends DirectoryTreeOutputStream {
    
    protected Diff d;
    protected RefTree t;
    protected int baseVersion = Constants.NO_VERSION;
  
    /** Get reftree of stream.
     *
     * @param basetree reftree that is referenced by the streamed tree.
     * @return stream reftree.
     */
    public RefTree getTree(IdAddressableRefTree basetree) {
      if( !(basetree instanceof FacetedTree) )
        Log.log("Expected base tree to be an instance of "+FacetedTree.class,
            Log.ASSERTFAILED);
      ensureTree((FacetedTree) basetree);
      return t;
    }
  
    public TreeOutputStream() {
      
    }
  
    /** Apply the streamed tree.
     *
     * @throws NodeNotFoundException if a node is missing.
     */
    // Thoughts: the uploaded tree should be applied to liveTree, using the
    // repo currentTree as index. Use of cT as index is possible only if
    // the liveTree == cT (the current assumption for sync-in-progress)

    // SERVER: The upload should then commit() the liveTree to the repo in order
    // to get a version number to send back to the sync initiator.

    // CLIENT: we have Tm + a remote version
    // We then apply Tm to the liveTree (notice symmetry) with SERVER,
    // and commit() it using the version given by the server

    // the linkCommit(data,verNo) in current SyxawFile is thus equal to
    // getLinkOS() + write OS (->update localTree) + commitLink(linkVer)

    abstract public void apply() throws NodeNotFoundException;
    
    protected void stream(InputStream in) throws IOException {
      if( baseVersion == Constants.NO_VERSION )
        t = XmlUtil.readRefTree(in, DirectoryEntry.XAS_CODEC);
      else {
        // Decode delta
        Log.log("Decoding dir delta to version "+baseVersion,Log.INFO);
        ItemSource r = XmlUtil.getDataSource(in);
        d = Diff.readDiff(r, 
            TreeModels.xmlr1Model().swapCodec(
            DirectoryEntry.XAS_CODEC), getRoot().getId());
      }
    }
  
    protected void ensureTree(FacetedTree basetree) {
     //System.out.println("[ensureTree] baseVersion=" + baseVersion);
     //System.out.flush();
      try {
        if (t != null)
          return;
        t = d.decode(basetree.getTree(baseVersion));
        // DEBUG
        if( fc.syxaw.fs.Config.DEBUG_DUMPDELTA ) {
          Log.log("Decoded tree:", Log.INFO);
          XmlUtil.writeRefTree(t, System.err, DirectoryEntry.XAS_CODEC);
          System.out.flush();
        }
        // END DEBUG
      } catch (IOException x) {
        Log.log("Unexpected IOException",Log.ASSERTFAILED,x);
      }
    }
  
    public long getDeltaSize() {
      return -1L;
    }
  
    public void setBaseVersion(int version) {
      baseVersion = version;
    }

    public int getBaseversion() {
      return baseVersion;
    }
  }
}
// arch-tag: ae9e1e9606a909728ce556e93e83db35 *-
