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

// $Id: DirectoryMerger.java,v 1.46 2005/06/08 13:03:28 ctl Exp $
package fc.syxaw.hierfs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import fc.syxaw.api.Metadata;
import fc.syxaw.fs.BLOBStorage;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FileTransfer;
import fc.syxaw.fs.GUID;
import fc.syxaw.fs.ObjectMerger;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.merge.XMLMerger;
import fc.syxaw.proto.ObjectMergerEx;
import fc.syxaw.proto.Version;
import fc.syxaw.protocol.CreateResponse;
import fc.syxaw.tdm.MergeUtil;
import fc.syxaw.util.Util;
import fc.syxaw.util.XmlUtil;
import fc.util.NonListableSet;
import fc.util.io.DelayedInputStream;
import fc.util.log.Log;
import fc.xml.xmlr.ChangeTree;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTreeImpl;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTreeNodeImpl;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.KeyIdentificationModel;
import fc.xml.xmlr.model.StringKey;
import fc.xml.xmlr.model.XasCodec;
import fc.xml.xmlr.tdm.Merge;
import fc.xml.xmlr.xas.PeekableItemSource;

/** Reconciliation algorithm for Syxaw directory trees. The reconciliation
 * algorithm is built upon the <code>3dm</code> generic XML reconciliation
 * algorithm. The following extensions have been made to the <code>3dm</code>
 * algorithm:<ul>
 * <li>custom code for merging of the <code>nextid</code> attribute of the
 * root element</li>
 * <li>allocate remote GUIDs for locally inserted objects using the
 * <code>CREATE</code> synchronization protocol call</li>
 * <li>check for duplicate names in the merged tree</li>
 * </ul>
 * The algorithm currently may generate false conflicts, due to reordering
 * of files inside a direcory. This happens because <code>3dm</code> does
 * not yet support unordered trees.
 */

public class DirectoryMerger extends XMLMerger implements ObjectMerger,
   Merge.NodeMerger, ObjectMergerEx {

  protected static final RefTree MERGE_IS_CURRENT = new RefTreeImpl(null);
  public static final XasCodec MUTABLE_NODE_CODEC = new NodeXasCodec();

  protected RefTree mergedTree = null;

  protected DirectoryTreeStore dts;
  protected Version baseVersion=null; //Constants.NO_VERSION;
  protected Set extraDepend = null;
  //
  public DirectoryMerger(SyxawFile f, VersionHistory h,
                         DirectoryTreeStore dts, Version baseVersion,
                         Set extraDepend) {
    super(f, h, Constants.NO_VERSION); // FIXME-W currentVer not needed!
    this.baseVersion = baseVersion;
    this.dts = dts;
    this.extraDepend = extraDepend;
  }

  /** Returns the set of files that were detected to have been changed
   * (locally or remotely) when the directory tree was reconciled.
   */

  public Set getObjectsNeedingSync() {
    Set depend = dts.getObjectsNeedingSync();
    if( extraDepend != null )
      depend.addAll(extraDepend);
    return depend;
  }

  // If reftree, the tree passed here must ref baseVersion
  // refCurrentLocal Version

  public int mergeData(boolean currentChanged, boolean downloadChanged,
                       int downloadVersion, BLOBStorage downloadData,
                       int dlVerRef) throws IOException, ObjectMerger.Conflict {

    if( baseVersion.getNumber() != Constants.NO_VERSION &&
        baseVersion.getLocalVersion() == Constants.NO_VERSION )
      Log.log("Missing map to local for basever "+baseVersion,Log.ASSERTFAILED);

    IdAddressableRefTree baseTreeRA = null;
    RefTree newTree = null; // Retrieve only if change from base
    RefTree currentTree = null; // Retrieve only if change from base
    Map /*!5 <Key,String> */ insertNodes = 
      new HashMap /*!5 <Key,String> */(); // setof (nodeid,remote-uid) for new objects
    int initialRemoteVer = Constants.NO_VERSION;

    if( dlVerRef == Constants.NO_VERSION || dlVerRef != baseVersion.getNumber() ) {
      // Build downloaded dirtree
      newTree = getNewTree(dlVerRef, downloadData);
    }

    if (currentChanged) {
      dts.FIXMEPsetLinkToLocalMapping(false);
      baseTreeRA = baseVersion.getNumber() == Constants.NO_VERSION ?
          dts.getNullBaseTree() : dts.getTree(baseVersion.getLocalVersion());
      dts.FIXMEPsetLinkToLocalMapping(true);

      // Read currentRbaseVersion, and make list of new objects
      currentTree = dts.getRefTree(Constants.CURRENT_VERSION,baseVersion.getNumber()); // get(a,b) rets aRb
      findInsertNodes( currentTree.getRoot(), insertNodes, baseTreeRA );
      initialRemoteVer = allocateNewObjects(
          new GUID(f.getLink()).getLocation(), insertNodes);
      // Set ids
      ChangeTree updatedCurrent = new ChangeTree(RefTrees.getAddressableTree(
          currentTree));
      try {
        for (Iterator i = insertNodes.entrySet().iterator(); i.hasNext(); ) {
          Map.Entry e = (Map.Entry) i.next();
          Key nid = (Key) e.getKey();
          RefTreeNode n = updatedCurrent.getNode(nid);
          Node newCont = new Node( (DirectoryEntry) n.getContent());
          newCont.setUid((String) e.getValue());
          newCont.setVersion(initialRemoteVer);
          updatedCurrent.update(nid, newCont);
        }
      } catch(NodeNotFoundException ex) {
        Log.log("Mysteriously disappeared node",Log.ASSERTFAILED,ex);
      }
      currentTree = updatedCurrent;
    }

    if( newTree != null && currentTree != null ) {
      Log.log("Directory merge...",Log.INFO);
      try {
        // All trees ref base; now we need to ensure that there are no refs to
        // nodes inside refs -> normalize the trees
        RefTree[] mergeTrees = new RefTree[] {
            RefTrees.getRefTree(baseTreeRA), newTree, currentTree};
        Set[] usedRefs = RefTrees.normalize(baseTreeRA, mergeTrees );
        // Set of allowed content refs
        // FIXME-W: we only need to disallow content != basetree content,
        // current impl is overly pessimistic!
        NonListableSet allowedContentRef = new NonListableSet() {
          public boolean contains(Object o) {
            return false;
          }
        };
        // Expand all trees
        for( int i = 0;i<mergeTrees.length; i++ ) {
          //DEBUG
          /*Log.log("Expanding:" +(new String[] {"base", "remote", "local"})[i],
                  Log.INFO);
          try {  XmlUtil.writeRefTree(mergeTrees[i], System.err,
                                 DirectoryEntry.XAS_CODEC);
          } catch (Exception x) {}
          System.err.flush();*/
          //ENDS

          mergeTrees[i] = RefTrees.expandRefs(mergeTrees[i], usedRefs[i],
                                              allowedContentRef, baseTreeRA);
        }
        mergedTree = mergeDirTrees(mergeTrees[0], mergeTrees[1],
                                           mergeTrees[2],
                                           initialRemoteVer);
      } catch (NodeNotFoundException ex) {
        throw new IOException("Broken refs, most likely downloaded diff. id=" +
                              ex.getId());
      }
    } else if ( newTree != null ) {
      mergedTree=newTree;
    } else {
      mergedTree= insertNodes.size() > 0 ? currentTree : MERGE_IS_CURRENT;
    }
    // FIXME: Apply merged tree here already to get correct delta for upload
    // NOTE that commitLink() does the same update. The fundamental thing that
    // should probably be changed is that we need merge-apply-upload-commit
    // instead of merge-upload-commit+apply (i.e. two-phase commit needed)
    if( mergedTree != MERGE_IS_CURRENT) {
      try {
        Debug.dumpTree(mergedTree, "Merged tree blow, applying now...", System.err);
        RefTrees.apply(mergedTree, dts);
      } catch (NodeNotFoundException ex) {
        // E.g. the merge result tries to resurrect deleted data
        throw new ObjectMerger.Conflict("Invalid merge result; Missing node"+ex.getId());
      }
    }
    return currentChanged ? Constants.NO_VERSION : downloadVersion;
  }


  /** Retuns a cached input stream to the merged directory tree */
  public InputStream getMergedData() throws IOException {
    // return cached merge result
    // outputting mergedTree as a reftree should be  OK, as it refs base =
    // common to both
    // The question if, how do we intergate this nicely with diff encoding?
    // it seems like diff encoding is done too late in the pipe currently, as
    // we do it on generic streams, not data objects.
    // Seems like a SyxawFile shoud be able to return the diff() to an arbitary
    // VersionHistory -> getMergedData should probably also take a versionhist
    return new DirRefTreeInputStream(mergedTree==MERGE_IS_CURRENT ?
                                     RefTrees.getRefTree(dts) : mergedTree );
  }


  // Map of (nodeId, null)
  protected void findInsertNodes(RefTreeNode r, Map nodes,
          IdAddressableRefTree base) {
    if(!r.isReference() &&
       ((DirectoryEntry) r.getContent()).getType() == DirectoryEntry.FILE &&
       !base.contains(r.getId()) ) {
      DirectoryEntry c = (DirectoryEntry) r.getContent();
      // FIXME-P: A totally kludgy hack to take care of the case when
      // A file inserted on the branch needs to be inserted to server after
      // switch. Proper file management is simply not coded in this prototype!
      // See notes about Constant.FIXME_RELINK_VERSION
      if( !Util.isEmpty(c.getUid()) ) {
        String fname = "//" + buildPath(r);
        Log.log("Re-insert of "+c.getUid()+", file "+fname,Log.INFO);
        fc.syxaw.fs.Syxaw.getFile(fname).setLink(
                new Integer(Constants.NO_VERSION),
                new Boolean(true), new Boolean(true), true, true);
      }
      nodes.put(r.getId(), null);
    }
     for( Iterator i = r.getChildIterator();i.hasNext();)
       findInsertNodes((RefTreeNode) i.next(),nodes,base );

  }

  private String buildPath( RefTreeNode n ) {
    return (n.getParent().getParent() == null ? "" : buildPath(n.getParent())+
                    File.separator) +
                    ((DirectoryEntry) n.getContent()).getName();
  }

  // return newRbase
  protected RefTree getNewTree(int dlVerRef, BLOBStorage downloadData) throws
      IOException {
    if (dlVerRef != Constants.NO_VERSION) {
      try {
        dts.FIXMEPsetLinkToLocalMapping(false);
        return dts.getRefTree(dlVerRef, baseVersion.getLocalVersion());
      } finally {
        dts.FIXMEPsetLinkToLocalMapping(true);
      }
    }
    OutputStream out = downloadData.getOutputStream(true);
    if (! (out instanceof DirectoryTreeOutputStream))
      Log.log("Expected a DirectoryTreeOutputStream", Log.ASSERTFAILED);
    RefTree t = ( (DirectoryTreeOutputStream) out).getTree(dts); 
    if (t == null)
      throw new IOException("Unparseable/missing new dirtree");
    return t;
  }

  /// insertNodes = map of (Node,(String) objectid) = objectids for all
  // new objects

  protected RefTree mergeDirTrees(RefTree baseTree,
                                  RefTree newTree,
                                  RefTree currentTree,
                                  int initialRemoteVer )
   throws IOException, ObjectMerger.Conflict {
    /// ******* MERGING **********************
    /// Tasks: 1) make sure nextid = max of new and current
    ///        2) make sure locally inserted objects get a link
    ///        3) 3dm merge structure
    ///        4) duplicate name check..
    ///        5) etc.



    // remove comment of this block to dump trees
    /*
    System.out.println("Dumping trees in directorymerger:");
    Debug.dumpTree(baseTree, "base", System.out);
    Debug.dumpTree(newTree, "remote", System.out);
    Debug.dumpTree(currentTree, "local", System.out);
    Debug.mtCounter++;
    System.out.println("Finished dumping trees in directorymerger.");
    System.out.flush();
    */
    // end dumptree code
    // Build base tree

    // * * * RUN 3dm * * *

    ByteArrayOutputStream clogs = new ByteArrayOutputStream();
    
    RefTree tm = Merge.merge(RefTrees.getAddressableTree(baseTree),
                             RefTrees.getAddressableTree(newTree),
                             RefTrees.getAddressableTree(currentTree),
                             XmlUtil.getXmlSerializer(clogs),
                             MUTABLE_NODE_CODEC,this);
    if (clogs.size() > 0) {
      Log.log("Merge conflict.", Log.WARNING);
      throw new ObjectMerger.Conflict("Merge conflict for " + f.getUid(),
                                      new ByteArrayInputStream(clogs.
          toByteArray()));
    }

    // * * * Resolve any naming conflicts * * *
    resolveNamingConflicts(tm.getRoot());

    sanityCheck(tm.getRoot(), new HashSet());
    //Debug.dumpTree(tm, "merged", System.err);
    return tm;
  }

  private void resolveNamingConflicts(RefTreeNode root ) {
    Set nameSet = new java.util.HashSet();
    for( Iterator i = root.getChildIterator();i.hasNext();) {
      RefTreeNode rtn = (RefTreeNode) i.next();
      if( rtn.isReference() )
        continue; // Safe AFAIK. Why?
                  // refnodes is a set of consistent names in base
                  // a renamed entry cannot conflict with this set, as
                  // in that case 1) localver or 2) the downloaded ver
                  // would contain a naming conflict, wich is not true.
      Node n = (Node) rtn.getContent();
      int trial = 0;
      String _origName = n.getName();
      while( nameSet.contains(n.getName()) ) {
        n.setName((trial==0 ? "conflict-":"")+n.getName()+
                  (trial > 0 ? "-"+trial: ""));
        trial++;
      }
      nameSet.add(n.getName());
      if( !_origName.equals(n.getName()) )
        Log.log("Resolved naming conflict "+_origName+"->"+n.getName(),Log.INFO);
      resolveNamingConflicts(rtn);
    }
  }

  // NOTE:  a little bit wasty to iterate through the tree for both name conflicts
  // and new inserts; could do this in one phase!
  private void setNewObjectIds(RefTreeNode root, Map insertNodes,
                               int initialRemoteVersion) {
    if(insertNodes.get(root.getId())!=null) {
      ((Node) root.getContent()).setUid((String) insertNodes.get(root.getId()));
      ((Node) root.getContent()).setVersion(initialRemoteVersion);
      // FIXME-W should i remove the node from insertNodes?
      // Then I could check that the set is empty after the call =
      // all allocated id's were used
//      Log.log("Inserted node is no longer file in merged tree: "+
//              cn.getFsPath()+", id="+cn.getId(),Log.WARNING);
    }
    for( Iterator i = root.getChildIterator();i.hasNext();) {
      setNewObjectIds((RefTreeNode) i.next(),insertNodes,initialRemoteVersion);
    }
  }

  // Fill in map (nodeId, allocObjectId)
  private int allocateNewObjects(String targetLocation,
                                 Map insertFiles) throws IOException {
    if (insertFiles.size() > 0) {
      // Need to alloc object ids on server
      CreateResponse r = FileTransfer.getInstance().createObjects(targetLocation,
          insertFiles.size());
      String[] objects = r.getObjects();
      int opos = 0;
      for (Iterator i = insertFiles.entrySet().iterator(); i.hasNext(); ) {
        Map.Entry e = (Map.Entry) i.next();
        e.setValue(objects[opos++]);
      }
      return r.getInitialVersion();
    }
    return Constants.NO_VERSION;
  }

  private void sanityCheck(RefTreeNode n, Set ids) throws ObjectMerger.Conflict {
    if( !ids.add(n.getId()) )
      throw new ObjectMerger.Conflict(
          "Merged dirtree has duplicate ids, first is " + n.getId());
    for( Iterator i=n.getChildIterator();i.hasNext();)
      sanityCheck((RefTreeNode) i.next(),ids);
  }

  public int mergeMetadata(boolean currentChanged, boolean downloadChanged,
                           int downloadVersion, Metadata downloadMeta,
                           int dlVerRef) throws IOException {
    int retval = -1, FIXMEPSavedCurrent=currentVersion;
    try {
      // NOTE: Base class uses linear (old-style) versions; here we kludge
      // the linkToLocalmapping + currentVersion to get the desired effect
      FIXMEPSavedCurrent = currentVersion;
      dts.FIXMEPsetLinkToLocalMapping(false);
      currentVersion = baseVersion.getLocalVersion();
      retval = super.mergeMetadata(currentChanged, downloadChanged,
                                   downloadVersion,
                                   downloadMeta, dlVerRef);
    } finally {
      dts.FIXMEPsetLinkToLocalMapping(true);
      currentVersion = FIXMEPSavedCurrent;
    }
    return retval;
  }

  public RefTreeNode merge(RefTreeNode baseN, RefTreeNode n1N, RefTreeNode n2N) {
    DirectoryEntry base = (DirectoryEntry) baseN.getContent();
    DirectoryEntry c1 =(DirectoryEntry) n1N.getContent();
    DirectoryEntry c2 =(DirectoryEntry) n2N.getContent();
    if( base == null || c1 == null || c2 == null) {
      Log.log("Node merge involving expanded and unexpanded nodes",Log.ASSERTFAILED);
    }
    //Log.log("Merging ",Log.DEBUG, new Object[] {base,c1,c2});
    DirectoryMerger.Node merged = null;
    try {
      merged =
          new DirectoryMerger.Node(
          MergeUtil.merge(base.getType(), c1.getType(), c2.getType()),
          (StringKey) base.getId(),
          base.getLocationId(), // Always pick local??
          (String) MergeUtil.merge(base.getName(), c1.getName(), c2.getName()),
          base.getNextId(), // Not used
          (String) MergeUtil.merge(base.getUid(), c1.getUid(), c2.getUid()),
          Math.max(c1.getVersion(), c2.getVersion())
          /*MergeUtil.merge(base.getVersion(), c1.getVersion(), c2.getVersion())*/);
      //Log.log("Merged cnt is ",Log.DEBUG,merged);
    } catch (MergeUtil.Conflict c ) {
      Log.log("Conflict merging",Log.INFO, new Object[] {base,c1,c2});
    }
    return new RefTreeNodeImpl(null, base.getId(), merged);
  }

  /** Input stream with an attached directory reftree. Allows passing reftrees
   * between "reftree-aware" components of the synchronization engine without
   * having to instantiate the actual stream.
   */

  public static class DirRefTreeInputStream extends DelayedInputStream {

    private RefTree t;

    /** Create a new stream.
     *
     * @param t reftree that the stream encodes.
     */
    public DirRefTreeInputStream(RefTree t) {
      this.t = t;
    }

    /** Get tree of the stream.
     *
     * @return reftree of the stream
     */
    public RefTree getTree() {
      return t;
    }

    protected void stream(OutputStream out) throws IOException {
      XmlUtil.writeRefTree(t, out, DirectoryEntry.XAS_CODEC);
    }

    protected void streamDone() {
      if( in == null )
        Log.log("Got away without streaming textual data!",Log.INFO);
    }

  }


  private static class Node extends DirectoryEntry.DirectoryEntryImpl implements
      RefTrees.IdentifiableContent {

    public Node(DirectoryEntry ent) {
      this(ent.getType(), (StringKey) ent.getId(), ent.getLocationId(),
           ent.getName(), ent.getNextId(), ent.getUid(),
           ent.getVersion());
    }

    public Node(int aType, StringKey aId, String aLocationId, String aName,
                String aNextId, String aUid, int aVersion) {
      super(aType, aId, aLocationId, aName, aNextId, aUid, aVersion);
    }

    public void setNextId(String aNextId) {
      nextId = aNextId;
    }

    public void setName(String aName) {
      name = aName;
    }

    public void setVersion(int aVersion) {
      version = aVersion;
    }

    public void setUid(String aUid) {
      uid = aUid;
    }

  }


  private static class NodeXasCodec extends DirectoryEntry.XasCodec {

    // Rewrap content to Node class, so we can modify it
    /*!5 @Override */
    public Object decode(PeekableItemSource is, KeyIdentificationModel kim) throws IOException {
      DirectoryEntry ent = (DirectoryEntry) super.decode(is, kim);
      if (ent != null)
        ent = new Node(ent);
      return ent;
    }
  }

  private static class Debug {
    static int mtCounter = 0;
    private static void dumpTree(RefTree t, String hint, OutputStream out) {
      if( !fc.syxaw.fs.Config.DEBUG_DUMPDELTA ) 
          return;
      boolean gotStream = out != null;
      try {
        if (!gotStream) {
          File f = new File(GUID.getCurrentLocation() + "-" + mtCounter + "-" +
                            hint + ".xml");
          out = new FileOutputStream(f);
          Log.log("Dumping merge to " + f, Log.INFO);
        }
        Log.log("Dumping tree "+hint,Log.INFO);
        try {
          XmlUtil.writeRefTree(t, out, DirectoryEntry.XAS_CODEC );
        } catch ( Exception x) {
          Log.log("Strict codec FAILED, trying non-strict ",Log.WARNING);
          XmlUtil.writeRefTree(t, out, new DirectoryEntry.XasCodec(false));
        }
        if (!gotStream)
          out.close();
        else
          out.flush();
      } catch (IOException x) {
        Log.log("Tree dump failed", Log.ERROR);
      }
    }
  }


  public void setBaseVersion(Version v) {
    currentVersion = v.getLocalVersion();
    baseVersion = v;
  }

  public Version getBaseVersion() {
    return baseVersion;
  }

}
// arch-tag: 93b2438903ea300c8f5cccaede0cc16a *-
