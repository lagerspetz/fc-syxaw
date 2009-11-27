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

// $Id: LinkTree.java,v 1.1 2005/06/08 13:14:27 ctl Exp $
// Was previously in storage/xfs as
// Id: LinkTree.java,v 1.25 2005/06/08 12:46:12 ctl Exp

package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.FullMetadataImpl;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.hierfs.DirectoryEntry;
import fc.syxaw.hierfs.DirectoryMerger;
import fc.syxaw.proto.Version;
import fc.syxaw.util.Util;
import fc.util.log.Log;
import fc.xml.xmlr.ChangeTree;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.IdAddressableRefTreeImpl;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.MutableRefTree;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTreeImpl;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.Reference;
import fc.xml.xmlr.model.StringKey;

/** The link facet of the current Syxaw directory tree.
 *
 */

public class LinkTree extends FacetedTree {

  private int __modcount=0;
  /** Key of the link-to-local version map in the Syxaw registry. */
  public static final StringKey VERMAP_KEY = 
    StringKey.createKey( Registry.ROOT_KEY , ".vermap" );


  private Set linkVersionChanged = new HashSet(); // Set of uids, for which the
                                                  // linkver has changed
  // Map between local and link id's
  // Currently these are the same (we use optimistic 64-bit id's which should
  // be virtually collison-free)
  private final RefTrees.IdMap linkToLocal = RefTrees.getIdentityIdMap();

  protected final FullMetadata DIR_METADATA = new FullMetadataImpl(); // Invariant metadats for dirs

  protected SortedMap versionMap = new TreeMap(); // link -> local

  // Set of link versions
  // FIXME-P: make persistent, visibility; map linkver -> Version(lv,bfranch,localver)
  public static Set FIXMEPFullVersionSet =
         Collections.synchronizedSet(new HashSet());

  protected VersionHistory treeHistory = new LinkedTreeVersionHistory();
  protected MutableRefTree vmap = null;
  protected RefTreeNode vmapRoot = null;

  /** Create a new linktree. The linktree is the link facet of the
   * <code>repo</code> tree.
   * @param root root directory
   * @param repo DirectoryTree repository storing full directory trees
   * @param storeRoot file system root; used to map nodes in the directory tree
   * to paths.
   * @param reg MutableRefTree registry to store linktree local data to
   * (e.g. the map between link and local version numbers).
   */
  public LinkTree(AbstractSyxawFile root, VersionedDirectoryTree repo, File storeRoot,
                  MutableRefTree reg) {
    super(root,repo);
    fullTree = new ChangeTree(repo);
    vmap = reg;

    if( vmap.getNode(VERMAP_KEY) == null ) {
      try {
        vmap.insert(vmap.getRoot().getId(), VERMAP_KEY,
                    new Registry.KeyValue(VERMAP_KEY, null));
      } catch (NodeNotFoundException ex) {
        Log.log("Can't init vermap root "+VERMAP_KEY,Log.FATALERROR);
      }
    }
    vmapRoot = vmap.getNode(VERMAP_KEY);
    for( Iterator  i = vmapRoot.getChildIterator();i.hasNext();) {
      RefTreeNode n = (RefTreeNode) i.next();
      Registry.KeyValue linklocal = (Registry.KeyValue) n.getContent();
      versionMap.put(
          new Integer(linklocal.getKey().toString().substring(VERMAP_KEY.toString().length()+1)),
          new Integer(linklocal.getValue()));
      // BUGFIX 20060920-1: Restore branchmap (trunk versions only!), so that
      // non-branch syncing works after restart
      FIXMEPFullVersionSet.add(new Version(
          (new Integer(linklocal.getKey().toString().substring(
        	  VERMAP_KEY.toString().length()+1))).intValue(),
          Version.TRUNK,
          (new Integer(linklocal.getValue())).intValue() ));
    }
  }

  public VersionHistory getVersionHistory() {
    return treeHistory;
  }
  // DirTreeStore

  public int getCurrentVersion() {
    int ver = Constants.NO_VERSION;
    if (versionMap.size() > 0)
      ver = ( (Integer) versionMap.lastKey()).intValue();
    return ver;
  }

  public IdAddressableRefTree getTree(int version) throws IOException {
    // Translate link to local ver
    int localVer = getLocalVersion(version);
    if( localVer == Constants.NO_VERSION )
      return null;

    final IdAddressableRefTree t = localVer == Constants.CURRENT_VERSION ?
        fullTree : repo.getTree(localVer);

    return new IdAddressableRefTreeImpl() {
      public RefTreeNode getNode(Key linkid) {
        RefTreeNode n = null;
        try {
          n = t.getNode(linkToLocal.getDestId(linkid));
        } catch (NodeNotFoundException ex) {
          Log.log("Linkmap error",Log.ASSERTFAILED);
        }
        return n ==null ? n : new LinkNode(n);
      }

      public RefTreeNode getRoot() {
        return new LinkNode(t.getRoot());
      }
    };
  }

  public RefTree getRefTree(int version, int refsVersion) throws IOException {
    int localTargetVer = getLocalVersion(version);
    RefTree t = repo.getRefTree(
        localTargetVer, getLocalVersion(refsVersion));
    if( localTargetVer == Constants.CURRENT_VERSION ) {
      // Include changes buffered in fullTree
      RefTree clinkRrepo=fullTree.getChangeTree();
      try {
        t=RefTrees.combine(clinkRrepo,t,repo);
      } catch (NodeNotFoundException x) {
        Log.log("Inconsistent trees",Log.ASSERTFAILED,x);
      }
     //DEBUG 041216
     /*
      Log.log("Fulltree getCHangeTree is:", Log.INFO);
      try {
        XmlUtil.writeRefTree(fullTree.getChangeTree(), System.err,
                             DirectoryEntry.XAS_CODEC);
      } catch (Exception x) {}
      System.err.flush();

      Log.log("Link CURRENT_VERrefX is:", Log.INFO);
      try {
        XmlUtil.writeRefTree(t, System.err,
                             XmlUtil.ANY_CONTENT_WRITER);
      } catch (Exception x) {
        Log.log("DUMP FAILED", Log.ERROR, x);
      }
      System.err.flush();
    *///END DEBUG
    }
    return new RefTreeImpl( new LinkNode( t.getRoot() ));
  }

  public void commit(Version version ) throws IOException {
    if( version.getNumber() == Constants.NO_VERSION )
      throw new IllegalArgumentException("Version number required.");
    if( versionMap.containsKey(new Integer(version.getNumber())) )
      Log.log("That remote version already exists as; " +
              versionMap.get(new Integer(version.getNumber())) +
              " remapping to " + repo.getCurrentVersion() + 1, Log.INFO);
       //throw new IOException("Version already exists");
    try {
      repo.commit(fullTree);
      mapVersion(version,repo.getCurrentVersion());
      fullTree.reset(); // All changes checked in!
      resetDirModFlags(rootDir,true);
      __modcount=0;
    } catch (NodeNotFoundException ex) {
      throw new IOException("Missing node " + ex.getId());
    }
  }

  // setof UID
  // FIXME-W !!!!!! The call clears the set maintained in the class!!!!!!!!!!!!
  //
  public Set getObjectsNeedingSync() {
    Set ret = linkVersionChanged;
    linkVersionChanged = new HashSet(); // reset
    return ret;
  }

  /** Allocate new link id
   *
   * @return new id
   */
  public StringKey newLinkId() {
    try {
      return (StringKey) linkToLocal.getSrcId(repo.newId());
    } catch (NodeNotFoundException x) {
      Log.log("Linkmap error",Log.ASSERTFAILED);
      return null;
    }
  }

  // Dirs as streams

  // MutableRefTree iface

  public RefTreeNode getNode(Key id) {
    RefTreeNode n = null;
    try {
      n = fullTree.getNode(linkToLocal.getDestId(id));
    } catch (NodeNotFoundException ex) {
      Log.log("Linkmap error",Log.ASSERTFAILED);
    }
    return n ==null ? n : new LinkNode(n);
  }

  public RefTreeNode getRoot() {
    return new LinkNode(fullTree.getRoot());
  }

  public void delete(Key id) throws NodeNotFoundException {
    __modcount++;
    Key destId = linkToLocal.getDestId(id);
    if (__toFs && !deleteFile(destId))
      throw new NodeNotFoundException(id);
    fullTree.delete(destId);
  }

  public Key move(Key nodeId, Key parentId, long pos) throws
      NodeNotFoundException {
    __modcount++;
    Key dNodeId = linkToLocal.getDestId(nodeId);
    Key dParentId = linkToLocal.getDestId(parentId);
    if( __toFs && !moveFile(dNodeId, dParentId) )
      throw new NodeNotFoundException(nodeId);
    return fullTree.move(dNodeId,dParentId,pos);
  }

  public Key insert(Key parentId, long pos, Key newId, Object c) throws
      NodeNotFoundException {
    __modcount++;
    DirectoryEntry content = (DirectoryEntry) c;
    AbstractSyxawFile newEntry =
       __toFs ? insert(parentId, newId, content,true) : null;
    if( newEntry != null && content.getType() == DirectoryEntry.FILE )
      linkVersionChanged.add(newEntry.getUid()); // New entry that needs sync
    StoredDirectoryTree.Node defaults =
        new StoredDirectoryTree.NodeImpl(LinkNodeContent.NONE, null, null, null, "",
                                         newEntry != null ?
                                         newEntry.getUid().toBase64() :
            content.getLocationId(), //KLUDGE050202, descr in FacetedTree.java
                                         Constants.FIRST_VERSION,
                                      //Optimistically claim we have the first localver already
                                         Constants.NO_VERSION, null, null,
                                         (StringKey) newId);

    StoredDirectoryTree.Node i = expand(content,defaults);
    fullTree.insert(linkToLocal.getDestId(parentId), pos,
                    linkToLocal.getDestId(newId), i);
    return newId;
  }

  public boolean update(Key nodeId, Object c) throws
      NodeNotFoundException {
    __modcount++;
    DirectoryEntry content = (DirectoryEntry) c;
    Key localId = linkToLocal.getDestId(nodeId);
    RefTreeNode fn = fullTree.getNode(localId);
    if( fn == null )
      throw new NodeNotFoundException(nodeId);
    StoredDirectoryTree.Node current = (StoredDirectoryTree.Node) fn.getContent();
    if (__toFs &&
        !update(nodeId, current.getName(), content, linkVersionChanged, true))
      throw new NodeNotFoundException(nodeId);
    StoredDirectoryTree.Node updated = expand(content,current);
    return fullTree.update(localId,updated);
  }

  protected int getLocalVersion( int linkVer ) {
    if( linkVer == Constants.CURRENT_VERSION )
      return linkVer;
    if( !FIXMEPmapvers )
      return linkVer;
    Integer localVer = ( (Integer) versionMap.get(new Integer(linkVer)));
    return localVer == null ? Constants.NO_VERSION : localVer.intValue();
  }

  public void getLocalVersion(Version v) {
    v.setLocalVersion(Constants.NO_VERSION);
    if( v.getNumber() == Constants.NO_VERSION )
      return;
    if( v.getNumber() == Constants.ZERO_VERSION ) {
      v.setLocalVersion(Constants.ZERO_VERSION);
    }
    for (Iterator i = LinkTree.FIXMEPFullVersionSet.iterator();
                      i.hasNext() && v.getLocalVersion() == Constants.NO_VERSION; ) {
      Version candidate = (Version) i.next();
      if( Util.equals(candidate.getBranch(),v.getBranch()) &&
          candidate.getNumber() == v.getNumber() )
        v.setLocalVersion(candidate.getLocalVersion());
    }
    if( v.getLocalVersion() == Constants.NO_VERSION )
      Log.log("Couldn't find localver for "+v,Log.WARNING);
  }
 /* protected SyxawFile getFile(String id) {
    try {
      return getFile(getNode(linkToLocal.getDestId(id)));
    } catch (NodeNotFoundException ex) {
      Log.log("Linkmap failed",Log.ASSERT_FAILED);
      return null;
    }
  }*/

  // Expand dirent to linked stuff (fields go to the linkXXX variants)
  protected StoredDirectoryTree.Node expand(DirectoryEntry n,
                                            StoredDirectoryTree.Node m) {
    try {
      return new StoredDirectoryTree.NodeImpl(n.getType(),
                                        (StringKey) linkToLocal.getDestId(n.getId()),
      // BUGFIX 2005-Aug 09: use m for locationId instead of n
      // Otherwise LID will oscillate between the remote and local lids on
      // subsequent syncs! (The proper way may be to use linklid and locallid)
                                              m.getLocationId(), n.getName(),
                                              m.getNextId(), m.getUid(),
                                              m.getVersion(),
                                              n.getVersion(), n.getUid(),
                                              n.getNextId(), 
                                              (StringKey) n.getId()
                                              );
    } catch ( NodeNotFoundException x ) {
      Log.log("Linkmap error",Log.ASSERTFAILED);
      return null;
    }
  }

  protected void mapVersion(Version linkVer, int localVer ) {

    String link = String.valueOf(linkVer.getNumber());
    String local = String.valueOf(localVer);
    Registry.KeyValue linklocal = new Registry.KeyValue(
        StringKey.createKey(VERMAP_KEY,link),local);
    try {
      if( vmap.contains(linklocal.getKey()) ) {
        // Warning about updating map??
        vmap.update(linklocal.getKey(), linklocal);
      } else
        vmap.insert(VERMAP_KEY, linklocal.getKey(), linklocal);
    } catch (NodeNotFoundException ex) {
      Log.log("Can't map version",Log.FATALERROR,ex);
    }
    linkVer.setLocalVersion(localVer);
    versionMap.put(new Integer(linkVer.getNumber()),new Integer(localVer));
    FIXMEPFullVersionSet.add(linkVer);
  }

  // Contract localnode to linknode
  protected DirectoryEntry contract(StoredDirectoryTree.Node n) {
    try {
      return new LinkNodeContent(n.getType(),
                      (StringKey) linkToLocal.getSrcId(n.getId()),
                      n.getLocationId(),
                      n.getName(), n.getLinkNextId(),
                      n.getLinkUid(), n.getLinkVersion());
    } catch (NodeNotFoundException ex) {
      Log.log("Linkmap failure",Log.ASSERTFAILED);
      return null;
    }
  }

  static class LinkNodeContent implements DirectoryEntry {
    private int type;
    private StringKey id;
    private String locationId;
    private String name;
    private String nextId;
    private String uid;
    private int version;

    public void setType(int aType) {
      type = aType;
    }

    public int getType() {
      return type;
    }

    public void setId(StringKey aId) {
      id = aId;
    }

    public Key getId() {
      return id;
    }

    public void setLocationId(String aLocationId) {
      locationId = aLocationId;
    }

    public String getLocationId() {
      return locationId;
    }

    public void setName(String aName) {
      name = aName;
    }

    public String getName() {
      return name;
    }

    public void setNextId(String aNextId) {
      nextId = aNextId;
    }

    public String getNextId() {
      return nextId;
    }

    public void setUid(String aUid) {
      uid = aUid;
    }

    public String getUid() {
      return uid;
    }

    public void setVersion(int aVersion) {
      version = aVersion;
    }

    public int getVersion() {
      return version;
    }

    public LinkNodeContent(int aType, StringKey aId, String aLocationId, String aName,
                String aNextId, String aUid, int aVersion) {
      type = aType;
      id = aId;
      locationId = aLocationId;
      name = aName;
      nextId = aNextId;
      uid = aUid;
      version = aVersion;
    }
  }

  // The class Proxies access to content in fulltree

  class LinkNode implements RefTreeNode {

    RefTreeNode n;

    // Proxies local nodes, shows up as linknodes
    public LinkNode(RefTreeNode fullNode) {
      n = fullNode;
    }

    public Iterator getChildIterator() {
      final Iterator niter = n.getChildIterator();

      return new Iterator() {
        public void remove() {
          niter.remove();
        }

        public boolean hasNext() {
          return niter.hasNext();
        }

        public Object next() {
          LinkNode p = new LinkNode( (RefTreeNode) niter.next());
          return p;
        }
      };

    }

    public Object getContent() {
      StoredDirectoryTree.Node content = (StoredDirectoryTree.Node) n.
          getContent();
      return content == null ? null : LinkTree.this.contract( content );
    }

    public Key getId() {
      try {
        return linkToLocal.getSrcId(n.getId());
      } catch (NodeNotFoundException ex) {
        Log.log("Linkmap failed",Log.ASSERTFAILED);
        return null;
      }
    }

    public RefTreeNode getParent() {
      return n.getParent() != null ? new LinkNode( n.getParent() ) : null;
    }

    public boolean isNodeRef() {
      return n.isNodeRef();
    }

    public boolean isReference() {
      return n.isReference();
    }

    public boolean isTreeRef() {
      return n.isTreeRef();
    }

    public Reference getReference() {
      return n.getReference();
    }

  }

  protected class LinkedTreeVersionHistory implements VersionHistory {

    public int getCurrentVersion() {
      return LinkTree.this.getCurrentVersion();
    }

    public InputStream getData(int version) {
      try {
        RefTree t = getTree(version);
        return t != null ? new DirectoryMerger.DirRefTreeInputStream(t) : null;
      } catch (IOException x) {
        Log.log("No version/retrieval error", Log.INFO);
      }
      return null;
    }

    public FullMetadata getMetadata(int mdVersion) {
      return getLocalVersion(mdVersion) != Constants.NO_VERSION ?
          DIR_METADATA : null;
    }

    public int getPreviousData(int version) {
      int ver = Constants.NO_VERSION;
      if (versionMap.size() > 1) // If possible, two steps back in the map
        ver = ( (Integer) versionMap.headMap(versionMap.lastKey()).lastKey()).
            intValue();
      return ver;
    }

    public int getPreviousMetadata(int mdversion) {
      return getPreviousData(mdversion);
    }

    /*public int[] getVersions() {
      int[] vers = new int[versionMap.size()];
      int pos = 0;
      for (Iterator i = versionMap.keySet().iterator(); i.hasNext(); ) {
        vers[pos++] = ( (Integer) i.next()).intValue();
      }
      return vers;
    }*/

    public boolean versionsEqual(int v1, int v2, boolean meta) {
//      Log.log("==Vereq "+v1+","+v2+"="+(v1 == v2),Log.INFO,new Exception());
      return v1 != Constants.NO_VERSION && v1 == v2;
    }

    public List getFullVersions(String branch) {
      if ( branch != null && branch.equals(Version.CURRENT_BRANCH) )
        try {
          branch = LinkTree.this.rootDir.getFullMetadata().getBranch();
        } catch ( IOException ex ) {
          Log.log("Root dir disappeared",Log.FATALERROR,ex);
        }
      List vers = new LinkedList();
      if( branch != null ) {
        for (Iterator i = LinkTree.FIXMEPFullVersionSet.iterator();
                          i.hasNext(); ) {
          Version candidate = (Version) i.next();
          if (branch == null || Util.equals(candidate.getBranch(), branch))
            vers.add(candidate);
        }
      } else {
        // Build based on localvers: combine prefix from branchmap with
        // List of local versions; the prefix is the closest with
        // localver < ver
        List versions = repo.getVersionHistory().getFullVersions(null);
        for( Iterator i = versions.iterator();i.hasNext();) {
          int ver = ((Version) i.next()).getNumber();
          // Scan for matching linkver older or equal to this
          // FIXMEP-W: This is a n^2 algorithm used due to simplicity, make linear!
          int mindist = Integer.MAX_VALUE;
          Version closest = null;
          for (Iterator j = LinkTree.FIXMEPFullVersionSet.iterator();
                            j.hasNext(); ) {

            Version candidate = (Version) j.next();
            int dist = ver - candidate.getLocalVersion();
            if( dist >= 0 && dist < mindist ) {
              closest = candidate;
              mindist = dist;
            }
          }
          if( closest != null ) {
            // BUGFIX-050913-3 This is taken care of by the next forloop
            //if( mindist == 0)
            //  vers.add(closest); // Add base for the one matching base
            vers.add(new Version(ver, closest.getHereBranch(),ver));
          } //else
            //vers.add(new Version(ver,Version.TRUNK,ver));
        }
        // Add all known from the branchmap. We need to do this if there
        // Are several vers in FIXMEPbranchMap that maps to the same localver
        for (Iterator j = LinkTree.FIXMEPFullVersionSet.iterator();
                          j.hasNext(); )
          vers.add((Version) j.next());
      }
      Collections.sort(vers);
      return vers;
    }

    public boolean onBranch() {
      try {
        return !Version.TRUNK.equals(
                LinkTree.this.rootDir.getFullMetadata().getBranch());
      } catch (IOException ex) {
        Log.log("Root dir disappeared",Log.FATALERROR,ex);
      }
      return false;
    }
  }

  protected int getCommitVersion(int version, boolean modified) {
    //FIXME: GUESS next version that will be given by server, if it turns
    // out to be wrong, we just need another sync cycle to agree on the verno
    return (modified && version > Constants.ZERO_VERSION)
            ? version +1 : version;
  }

  /** Get tainted UIDs. The set of modified UIDs contains all UIDs detected as
   * modified by the last {@link FacetedTree#ensureLocalAndLiveInSync(java.util.Map, boolean)}.
   * Used to know
   * which objects have been locally modified when synchronizing.
   *
   * @return Set of modified UIDs.
   */
  public Set getTaintedUIDs() {
    return linkModFiles;
  }

  /** Linktree maintenance. */
  public static class Maintenance {

    /** Initialize linktree.
     *
     * @param root linktree root directory
     */
    public static void init(File root) {
    }

  }

  public class TreeOutputStream extends FacetedTree.TreeOutputStream {

    public TreeOutputStream() {
      super();
    }

    public void apply() throws NodeNotFoundException {
      Log.log("Applying tree-as-XML to link facet", Log.INFO);
      FacetedTree target = LinkTree.this;
      ensureTree(target);
      RefTrees.apply(t,target);
    }
    
  }

  // flag to indicate linkTolocalMapping state; see FIXMEPsetLinkToLocalMapping
  // in DirectoryTreeStore
  private boolean FIXMEPmapvers = true;

  public void FIXMEPsetLinkToLocalMapping(boolean state) {
    FIXMEPmapvers = state;
  }
}
// arch-tag: e6b51ac50d835651dfd1b33e985eba22 *-
