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

// $Id: LocalTree.java,v 1.1 2005/06/08 13:14:27 ctl Exp $
// Was previously in storage/xfs as
// Id: LocalTree.java,v 1.30 2005/06/08 12:46:12 ctl Exp

package fc.syxaw.storage.hfsbase;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import fc.syxaw.fs.Constants;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.hierfs.DirectoryEntry;
import fc.util.log.Log;
import fc.xml.xmlr.AbstractMutableRefTree;
import fc.xml.xmlr.ChangeTree;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.StringKey;

/** The local facet of the current Syxaw directory tree. */
public class LocalTree extends FacetedTree {

  //NO_CHANGED private ChangeDaemon changed=null;

  /** Create a new local tree. The linktree is the link facet of the
   * <code>repo</code> tree.
   * Includes inner
   * classes for syncing the local facet with the "live" file system.
   *
   * @param root file system root
   * @param repo current Syxaw directory tree
   */
  public LocalTree( AbstractSyxawFile root, VersionedDirectoryTree repo /*NOCHANGED,
                   ChangeDaemon changed*/) {
    super(root,repo);
    fullTree = new ChangeTree(repo);
    //NOCHANGED this.changed = changed;
  }

  // DTS code

  public void commit(int version) throws IOException {
    if (version != Constants.NO_VERSION)
      throw new IllegalArgumentException("Version number not allowed.");
    try {
      repo.commit(fullTree);
      fullTree.reset(); // All changes checked in!
      resetDirModFlags(rootDir,false);
    } catch (NodeNotFoundException ex) {
      throw new IOException("Missing node " + ex.getId());
    }
  }

  public int getCurrentVersion() {
    return repo.getCurrentVersion();
  }

  public int getNextVersion() {
    return repo.getNextVersion();
  }

  public Set getObjectsNeedingSync() {
    Log.log("Returning empty set",Log.WARNING); // should not be needed
    return Collections.EMPTY_SET;
  }

  public RefTree getRefTree(int version, int refsVersion) throws IOException {
    RefTree t = repo.getRefTree(version, refsVersion);
    if( version == Constants.CURRENT_VERSION ) {
      // Include changes buffered in fullTree
      t = RefTrees.getMutableTree(t);
      RefTree clinkRrepo=fullTree.getChangeTree();
      try {
        t=RefTrees.combine(clinkRrepo,t,this);
      } catch (NodeNotFoundException x) {
        Log.log("Inconsistent trees", Log.ASSERTFAILED, x);
      }
    }
    return t;
  }

  public IdAddressableRefTree getTree(int version) throws IOException {
    if( version == Constants.CURRENT_VERSION )
      return fullTree;
    return repo.getTree(version);
  }

  // Mutable reftree iface

  public RefTreeNode getNode(Key id) {
    return fullTree.getNode(id);
  }

  public void delete(Key id) throws NodeNotFoundException {
/*    SyxawFile delRoot = getFile(id);
    Log.log("Deleting tree "+delRoot,Log.INFO);*/
    fullTree.delete(id);
/*    if (!delete(delRoot))
      throw new NodeNotFoundException(id);*/
  }

  // Hook here to clean UIDs from indexdb. This is highly specific to the
  // current implementation, which (without the change-d) won't catch
  // local file deletes. 8.6.2005: dunno if this is true anymore

  public Key insert(Key parentId, long pos, Key newId, Object c) throws
      NodeNotFoundException {
    DirectoryEntry content = (DirectoryEntry) c;
//    /*SyxawFile newEntry =*/ insert(parentId, newId, content,false);

    StoredDirectoryTree.Node defaults =
        new StoredDirectoryTree.NodeImpl(DirectoryEntry.NONE, null, null, null, null,
                                         null,
                                         Constants.NO_VERSION,
                                         Constants.NO_VERSION, null, null, null);
    StoredDirectoryTree.Node n = expand( content, defaults);
    return fullTree.insert(parentId, pos, newId, n);
  }

  public Key move(Key nodeId, Key parentId, long pos) throws
      NodeNotFoundException {
/*    SyxawFile entry = getFile(nodeId);
    SyxawFile targetDir = getFile(parentId);
    Log.log("Moving "+entry+" to "+targetDir,Log.INFO);*/
    return fullTree.move(nodeId,parentId,pos);
/*    if( !move(entry, targetDir) )
      throw new NodeNotFoundException(nodeId);*/
  }

  public boolean update(Key nodeId, Object c) throws
      NodeNotFoundException {
    DirectoryEntry content = (DirectoryEntry) c;
    RefTreeNode fn = fullTree.getNode(nodeId);
    if( fn == null )
      throw new NodeNotFoundException(nodeId);
/*    if( !update( content, getFile(nodeId)) )
      throw new NodeNotFoundException(nodeId);*/
    StoredDirectoryTree.Node current = (StoredDirectoryTree.Node) fn.getContent();
    StoredDirectoryTree.Node updated = expand(content,current);
    return fullTree.update(nodeId,updated);
  }

  public RefTreeNode getRoot() {
    return fullTree.getRoot();
  }

  public VersionHistory getVersionHistory() {
    return repo.getVersionHistory();
  }

  /** Get delta output stream for this tree.
   *
   * @param autoapply ste to <code>true</code> if the tree written to the
   * returned stream should be automatically applied to the live file system
   * and the local tree.
   * @return TreeOutputStream
   */
  public TreeOutputStream getDeltaOutputStream(boolean autoapply) {
    return new TreeOutputStream(autoapply);
  }

  // Expand dirent to linked stuff (fields go to the linkXXX variants)
  protected StoredDirectoryTree.Node expand(DirectoryEntry n,
                                            StoredDirectoryTree.Node m) {
    return new StoredDirectoryTree.NodeImpl(n.getType(),
                                            (StringKey) n.getId(),
                                            // BUGFIX, mirrors BUGFIX 2005-Aug09
                                            m.getLocationId(), n.getName(),
                                            n.getNextId(), n.getUid(),
                                            n.getVersion(),
                                            m.getLinkVersion(), m.getLinkUid(),
                                            m.getLinkNextId(), 
                                            m.getLinkId()
                                            );
  }

  protected int getCommitVersion(int version, boolean modified) {
    if( version == Constants.NO_VERSION )
      return Constants.FIRST_VERSION;
    else if( modified )
      return version < Constants.FIRST_VERSION ? Constants.FIRST_VERSION :
        version+1;
    else
      return version;
  }

  /** The XFS file system as a mutable reftree. Since the generic hierfs
   * file system does not provide lookup of objects by node id, the
   * index of the outer <code>LocalTree</code> is used to lookup
   * files and convert these to paths. All operations on this tree
   * are propagated to the outer <code>LocalTree</code> instance as
   * well.
   * <p>The use of an external index requires that the outer
   *  <code>LocalTree</code> and the native file system are in sync when
   * this is manipulated.
   */

  protected class MutableLiveTree extends AbstractMutableRefTree {


    protected MutableLiveTree()  {
    }

    public RefTreeNode getRoot() {
      return LocalTree.this.getRoot();
    }

    //---Mutable rt iface --

    public Iterator childIterator(Key id) throws NodeNotFoundException{
      return LocalTree.this.childIterator(id);
    }

    public boolean contains(Key id) {
      return LocalTree.this.contains(id);
    }

    public RefTreeNode getNode(Key id) {
      return LocalTree.this.getNode(id);
    }

    public Key getParent(Key id) throws NodeNotFoundException {
      return LocalTree.this.getParent(id);
    }

    public void delete(Key id) throws NodeNotFoundException {
      //!A assert id instanceof StringKey;
      if (!LocalTree.this.deleteFile(id))
        throw new NodeNotFoundException(id);
      LocalTree.this.delete(id);
    }

    public Key insert(Key parentId, long pos, Key newId, Object c) throws
        NodeNotFoundException {
      DirectoryEntry content = (DirectoryEntry) c;
      LocalTree.this.insert(parentId, newId, content,false);
      return LocalTree.this.insert(parentId,pos,newId,content);
    }


    public Key move(Key nodeId, Key parentId, long pos) throws NodeNotFoundException {
      if( !LocalTree.this.moveFile(nodeId, parentId) )
        throw new NodeNotFoundException(nodeId);
      return LocalTree.this.move(nodeId,parentId,pos);
    }


    public boolean update(Key nodeId, Object c) throws NodeNotFoundException {
      DirectoryEntry content = (DirectoryEntry) c;
      if( !LocalTree.this.update( nodeId,
                                  ( (DirectoryEntry) getNode(nodeId).getContent()).
                                  getName(),
                                  content, null, false) )
        throw new NodeNotFoundException(nodeId);
      return LocalTree.this.update(nodeId,content);
    }
  }

  protected boolean deleteFile(Key id) throws NodeNotFoundException {
  //  cleanUIDs(id);
    return super.deleteFile(id);
  }

  public void FIXMEPsetLinkToLocalMapping(boolean state) {
    throw new UnsupportedOperationException(
            "Localtree can't (and should never) handle any linkvers"); // Should never be called
  }
  
  public class TreeOutputStream extends FacetedTree.TreeOutputStream {
    
    protected boolean autoApply;
    
    public TreeOutputStream(boolean autoApply) {
      super();
      this.autoApply = autoApply;
    }

    public void apply() throws NodeNotFoundException {
      Log.log("Applying uploaded tree to local facet", Log.INFO);
      // DEBUG
      /*try{XmlUtil.writeRefTree(t,System.err,DirectoryEntry.XAS_CODEC);
      } catch(Exception x) {}
      System.err.flush();*/
      // DEBUG ENDS
  
      // Thoughts: the uploaded tree should be applied to liveTree, using the
      // repo currentTree as index. Use of cT as index is possible only if
      // the liveTree == cT (the current assumption for sync-in-progreess)
  
      // SERVER: The upload should then commit() the liveTree to the repo in order
      // to get a version number to send back to the sync initiator.
  
      // CLIENT: we have Tm + a remote version
      // We then apply Tm to the liveTree (notice symmtery) with SERVER,
      // and commit() it using the version given by the server
  
      // the linkcommit(data,verno) in current SyxawFile is thus equal to
      // getLinkOS() + write OS (->update localTree) + commitLink(linkVer)
  
      ensureTree(LocalTree.this);
      RefTrees.apply(t, new MutableLiveTree());
    }

    /*!5 @Override */
    protected void stream(InputStream in) throws IOException {
      super.stream(in);
      if(autoApply) {
        ensureTree(LocalTree.this);
        try {
          apply();
        } catch (NodeNotFoundException ex) {
          throw new IOException("Broken uploaded tree: missing node " +
                                ex.getId());
        }
      }
    }
  }

}
// arch-tag: e44bbe4ab79fd52b8f60fa4b28ab9799 *-
