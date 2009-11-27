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

// $Id: VersionedDirectoryTree.java,v 1.1 2005/06/08 13:14:28 ctl Exp $
// Was previously in storage/xfs as
// Id: VersionedDirectoryTree.java,v 1.36 2005/02/03 10:40:27 ctl Exp

package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import fc.raxs.NoSuchVersionException;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.GUID;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.hierfs.DirectoryEntry;
import fc.syxaw.proto.Version;
import fc.syxaw.util.Util;
import fc.syxaw.util.XmlUtil;
import fc.util.IOUtil;
import fc.util.NonListableSet;
import fc.util.log.Log;
import fc.xml.xas.ItemSource;
import fc.xml.xas.XmlOutput;
import fc.xml.xmlr.ChangeTree;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTreeNodeImpl;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.StringKey;
import fc.xml.xmlr.model.TreeModel;
import fc.xml.xmlr.model.TreeModels;
import fc.xml.xmlr.tdm.Diff;


/** A versioned directory tree. The versioned directory tree consists
 * of a persistent dirctory tree ({@link StoredDirectoryTree}) and a
 * repository of reverse deltas to past versions w.r.t the current tree.
 * <p>The structure on disk of the tree is:
<pre>
&nbsp;<i>root-of-store</i>
&nbsp; |-- current              <i>The current tree (StoredDirectoryTree)</i>
&nbsp; |   |-- ...
&nbsp; |   .
&nbsp; |
&nbsp; `-- repository           <i>Deltas to past revision</i>
&nbsp;     |-- 0000000001.xml   <i>n.xml contains the delta n+1->n</i>
&nbsp;     |-- 0000000002.xml
&nbsp;     .
</pre>
 * For those interested in XMLR, this class is a nice example of its
 * applications.
 */

public class VersionedDirectoryTree implements IdAddressableRefTree {

  private static final Random rnd = new Random(System.currentTimeMillis() ^
                                               GUID.getCurrentLocation().
                                               hashCode());
  /** Id of root. */
  public static final StringKey ROOT_ID = StringKey.createKey( "0" );

  protected final File STORE_ROOT;
  protected final File CURRENT_STORE;
  protected final File REPOSITORY;

  private int nextVersion; // Set by init()
  private List versions = new LinkedList();
  private final StoredDirectoryTree currentTree;
  private final RefTree currentAsRefTree;

  // FIXME-20061016-1-1
  private final TreeModel dirContentCodec = TreeModels.xmlr1Model().swapCodec(
      new StoredDirectoryTree.NodeXasCodec());

  protected final VersionHistory history = new VersionHistoryImpl();

  private VersionedDirectoryTree(File root,
                                 StoredDirectoryTree.ChangeMonitor cm,
                                 boolean fullinit) {
    STORE_ROOT=root;
    CURRENT_STORE=new File(STORE_ROOT,"current");
    REPOSITORY=new File(STORE_ROOT,"repository");
    if( fullinit ) {
      currentTree = new StoredDirectoryTree(CURRENT_STORE,cm);
      currentAsRefTree = RefTrees.getRefTree(currentTree);
      init();
    } else {
      currentTree =null;
      currentAsRefTree = null;
    }
  }

  /** Crete a new versioned directory tree.
   *
   * @param root directory to store the tree
   */
  public VersionedDirectoryTree(File root,
                                StoredDirectoryTree.ChangeMonitor cm) {
    this(root,cm,true);
  }

  /** Apply and commit a new tree. The new tree typically contains
   * references to the current tree.
   *
   * @param newTree new tree
   * @throws NodeNotFoundException if a node is missing
   * @throws IOException if an I/O error occurs
   */
  // Sideeffects: Passing a changeTree is somewhat unintuitive. Commit will
  // not reset() the changetree.
  public void commit( RefTree newTree ) throws NodeNotFoundException,
      IOException {
    ChangeTree delta = newTree instanceof ChangeTree ? (ChangeTree) newTree:
        new ChangeTree(currentTree);
    if(delta!=newTree ) {
      long baseTime = System.currentTimeMillis();
      RefTrees.apply(newTree,delta);
      Log.log("Delta build took "+ (double)
              (System.currentTimeMillis()-baseTime)/1000.0+" sec",Log.INFO);
    }
    final IdAddressableRefTree deltaRefTree = delta.getChangeTree();
    Util.statTree(deltaRefTree);
    /*
    {
       PrintWriter pw = new PrintWriter(System.err);
       DefaultXmlSerializer ser = new DefaultXmlSerializer();
       ser.setOutput(pw);
       XmlWriter writer=new XmlWriter(ser );

       {
         ser.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
         writer.addEvent(Event.createNamespacePrefix(ReferenceEvent.REF_NS,
             "ref"));
       }
       Log.log("Treedump of current:",Log.INFO);
       XasSerialization.writeTree(currentTree, writer);
       ser.flush();
       Log.log("Treedump of delta:",Log.INFO);
       XasSerialization.writeTree(deltaRefTree, writer);
       ser.flush();
       //Log.log("Treedump of current-refs-delta:",Log.INFO);
       //XasSerialization.writeTree(currentRefsNew, writer);
       //ser.flush();
     }

    */
    Set[] usedRefs = RefTrees.normalize(currentTree,new RefTree[] {
                                        deltaRefTree,
                                        currentAsRefTree});

    Log.log("Refs in new to current "+usedRefs[0],Log.INFO);

    Set allowedContent = new NonListableSet() {
      IdAddressableRefTree newTree =
          RefTrees.getAddressableTree(deltaRefTree);
      public boolean contains(Object id) {
        RefTreeNode n = newTree.getNode((Key) id);
        // NOTE: Errorneous results if we ask contains inside an allowed ref'd subtree
        // but expandNodes should never do this.
        return n==null ? false : n.isReference();
        ///if( n == null ) Log.log("Slow query",Log.WARNING); //Debug code
        ///return n==null ? currentTree.getNode((String) id)!=null : n.isReference();
      }
    };

    long __baseTime = System.currentTimeMillis();

    RefTree currentRefsNew = RefTrees.expandRefs(currentAsRefTree,usedRefs[0],
                                                 allowedContent,
                                                 currentTree);
    Log.log("Reverse-delta expansion took "+ (double)
            (System.currentTimeMillis()-__baseTime)/1000.0+" sec",Log.INFO);

    // DEBUG CODE
    /*{
      Log.log("Treedump of current:",Log.INFO);
      XmlUtil.writeRefTree(currentTree, System.err, DirectoryEntry.XAS_CODEC);
      Log.log("Treedump of delta:",Log.INFO);
      XmlUtil.writeRefTree(deltaRefTree, System.err, DirectoryEntry.XAS_CODEC);
      Log.log("Treedump of current-refs-delta:",Log.INFO);
      XmlUtil.writeRefTree(currentRefsNew, System.err, DirectoryEntry.XAS_CODEC);

    }*/
    // Store the current-refs-new tree
    File deltaFile = getDeltaFile(getCurrentVersion());
    /*XmlUtil.writeRefTree(currentRefsNew,new File(deltaFile.toString()+".ref"),
                         XmlUtil.simpleBeanWriter());*/
    FileOutputStream deltaStream = new FileOutputStream(deltaFile);
    try {
      //OutputStream out = new FileOutputStream(deltaFile);
      //fc.fp.util.xmlr.tdm.Diff.diff(newTree,currentRefsNew,out);
      Diff reverseDiff = Diff.encode(deltaRefTree,currentRefsNew);

      XmlOutput xo = XmlUtil.getXmlSerializer(deltaStream);
      // This needs to be moved somewhere
      //xo.append(StartDocument.instance());
      // FIXME-20061016-1: Fix Prefixes
/*      w.addEvent(Event.createNamespacePrefix(ReferenceEvent.REF_NS,
                                                  "ref"));*/

      //XasSerialization.
      reverseDiff.writeDiff(xo,dirContentCodec);

      xo.flush();
      /*XmlUtil.writeRefTree(,
                           deltaFile,
                           new Diff.DiffContentWriter( XmlUtil.simpleBeanWriter()));*/

    } finally {
      deltaStream.close();
    }
    /*{ // DEBUG : Test that diff may be read back
       FileInputStream in = new FileInputStream(deltaFile);
       XmlReader r = XmlUtil.getXmlReader(in);

       // This needs to be moved somewhere...
       if (r.advance().getType() != Event.START_DOCUMENT) {
 //      Log.log("Expected to be at startdoc", Log.ERROR);
         throw new IOException("Expected to be at startdoc");
       }

       Diff d =Diff.readDiff(r,dirContentCodec);
       XmlWriter w = XmlUtil.getXmlWriter(System.out);
       d.writeDiff(w,XmlUtil.simpleBeanWriter());
       w.flush();
       in.close();
     }*/
    /*{ // DEBUG -- enable to dump full file as well
      File fullFile = new File(REPOSITORY,
                                "full-"+String.valueOf( getNextVersion() )+".xml");
       XmlUtil.writeRefTree(currentTree,fullFile,XmlUtil.simpleBeanWriter());

    }*/
    // Commit!
    try {
      RefTrees.apply(deltaRefTree, currentTree);
    } catch( Exception x ) {
      Log.log("Partial commit. Current revision is broken. Trying to revert...",Log.ERROR,x);
      try {
        RefTrees.apply(currentRefsNew, currentTree);
      } catch( Exception x2 ) {
        Log.log("Revert failed. Current revision is busted.\n"+
                "Syxaw may recover on restart",Log.FATALERROR,x2);
      }
      if( !deltaFile.delete() )
        Log.log("Revert failed - can't delete already commited delta",Log.FATALERROR);
      Log.log("Revert succeeded; Repository is unchanged",Log.INFO);
      throw new IOException("Commit failed");
    }
    setCurrentVersion(getNextVersion()); // Increase version number
    //NO-NO. We DO NOT change the passed tree! delta.reset(); // In case people want to apply more edits
  }

  protected File getDeltaFile(int version) {
    return new File(REPOSITORY, Util.format(String.valueOf(version), -9,
                                '0') + ".xml");
  }

  /** Get tree by version.
   *
   * @param version version to retrieve,
   * {@link fc.syxaw.fs.Constants#CURRENT_VERSION} may be to get the
   * current tree.
   * @return the requested version of the tree
   * @throws IOException if an I/O error occurs.
   */

  public ChangeTree getTree(int version) throws IOException {
    /*
    try {
    throw new Exception("[VersionedDirectoryTree] getTree called with version=" + version);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.flush();
    }*/
    if( version == Constants.CURRENT_VERSION )
      return new ChangeTree(this); // Current tree requested
    if( version > getCurrentVersion() || version == Constants.NO_VERSION )
      throw new NoSuchVersionException(version);
    ChangeTree tree = new
        ChangeTree(currentTree);
    int v = Constants.NO_VERSION;
    try {
      for (v = getPreviousVersion(getCurrentVersion()); v >= version;
           v = getPreviousVersion(v)) {
        // load olders-refs-tree
        File deltaFile = getDeltaFile(v);
        Diff reverseDelta = null;
        FileInputStream din = new FileInputStream(deltaFile);
        try {
          // This needs to be moved somewhere...
          ItemSource r = XmlUtil.getDataSource(din);
          //XasUtil.copy(r, XasDebug.itemDump());
          //Log.log("END",Log.FATALERROR);
          reverseDelta = Diff.readDiff(r, dirContentCodec,
                                       tree.getRoot().getId());
        } finally {
          din.close();
        }
        /*// DEBUG: dump decoded delta
        {
          RefTree r = reverseDelta.decode(tree);
          XmlUtil.writeRefTree(r,System.out,XmlUtil.simpleBeanWriter());
        }*/

        RefTrees.apply(reverseDelta.decode(tree), tree);
/*        Log.log("------------------------------------------->Applied",Log.INFO);
        Util.print(reverseDelta,System.err);
        Log.log("Result:",Log.INFO);
        Util.print(tree,System.err);*/
      }
    } catch (NodeNotFoundException ex) {
      Log.log("Broken delta references unknown node "+
              ex.getMessage()+"in ver"+v,Log.ERROR);
      throw new IOException("Broken data in rev "+v);
    }
    return tree; //.getChangeTree();
  }

  /** Get reftree by version. Gets a reftree for version
   * <code>version</code> that refs nodes in version <code>refsVersion</code>.
   * Implementations should strive to return a tree that uses references
   * as much as possible, so that the returned tree is small.
   *
   * @param version version to retrieve,
   * {@link fc.syxaw.fs.Constants#CURRENT_VERSION} may be to get the
   * current tree.
   * @param refsVersion version to reference,
   * {@link fc.syxaw.fs.Constants#CURRENT_VERSION} may be used to
   * reference the current tree.
   * {@link fc.syxaw.fs.Constants#NO_VERSION} may be used if there
   * may be no references in the returned tree.
   * @return the requested version of the tree as a reftree referencing
   *  <code>refsVersion</code>.
   * @throws IOException if an I/O error occurs.
   */

  public RefTree getRefTree(int version, int refsVersion) throws IOException {
    if( refsVersion == Constants.NO_VERSION )
      return this; // Refs nothing
    if (refsVersion == Constants.CURRENT_VERSION &&
        version == Constants.CURRENT_VERSION)
      return RefTrees.getRefTree(this); // current-refs-current
    int x = version, y = refsVersion;
    RefTree cRy = getCurrentRefsOld(y);
    RefTree xRc = getTree(x).getChangeTree();
    try {
      // DEBUG 041216
      /*
      Log.log("xRc is: x=" + x, Log.INFO);
      try {
        XmlUtil.writeRefTree(xRc, System.err, DirectoryEntry.XAS_CODEC);
      } catch (Exception x2) {}
      System.err.flush();

      Log.log("cRy is: y=" + y, Log.INFO);
      try {
        XmlUtil.writeRefTree(cRy, System.err, DirectoryEntry.XAS_CODEC);
      } catch (Exception x2) {}
      System.err.flush();*/

      return RefTrees.combine(xRc,cRy,this);
    } catch (NodeNotFoundException ex) {
      Log.log("Broken repository, id=" + ex.getId(), Log.FATALERROR);
    }
    return null;
  }

  protected RefTree getCurrentRefsOld(int oldVersion) throws IOException {
    final IdAddressableRefTree oRc = getTree(oldVersion).getChangeTree();
    // Make reverse-delta
    RefTree cRo = null;
    try {
      Set[] allowedRefs =
          RefTrees.normalize(currentTree, new RefTree[] {oRc,currentAsRefTree});

      Set allowedContent = new NonListableSet() {
        public boolean contains(Object id) {
          RefTreeNode n = oRc.getNode((Key) id);
          // NOTE: Errorneous results if we ask contains inside an allowed ref'd subtree
          // but expandNodes should never do this.
          return n==null ? false : n.isReference();
          ///if( n == null ) Log.log("Slow query",Log.WARNING); //Debug code
          ///return n==null ? currentTree.getNode((String) id)!=null : n.isReference();
        }
      };
      cRo = RefTrees.expandRefs(currentAsRefTree, allowedRefs[0],
                                        allowedContent, currentTree);
    } catch (NodeNotFoundException ex) {
      Log.log("Broken repository, id="+ex.getId(),Log.FATALERROR);
    }
    return cRo;
  }

  /** Allocate new node id. Uses a probabilistic algorithm which
   * returns a random 32-bit hex string.
   *
   * @return new node id
   */

  public StringKey newId() {
    return 
     StringKey.createKey(
         Long.toHexString(rnd.nextInt(2000000000)) ); // NOTE: id range for prettier XML while testing
  }

  // Current version in repo + tree = next
  /** Get current version of the tree.
   * @return current version
   */
  public int getCurrentVersion() {
    return nextVersion;
  }

  public int getNextVersion() {
    return getCurrentVersion() < Constants.FIRST_VERSION ?
            Constants.FIRST_VERSION: getCurrentVersion()+1;
  }

  /** Get the version preceeding a version.
   *
   * @param version version
   * @return version preceeding <code>version</code>, or
   * <code>Constants.NO_VERSION</code> if none.
   */
  public int getPreviousVersion(int version) {
    return version == Constants.FIRST_VERSION ? Constants.NO_VERSION : version -1;
  }


  /** Get version history of this tree.
   *
   * @return VersionHistory of the tree
   */
  public VersionHistory getVersionHistory() {
    return history;
  }

  protected void setCurrentVersion(int newVersion) {
    versions.add(new Integer(newVersion));
    nextVersion = newVersion;
  }

  // IdAdressableRefTree stuff

  public Iterator childIterator(Key id) throws NodeNotFoundException {
    return currentTree.childIterator(id);
  }

  public boolean contains(Key id) {
    return currentTree.contains(id);
  }

  public RefTreeNode getNode(Key id) {
    return currentTree.getNode(id);
  }

  public Key getParent(Key id) throws NodeNotFoundException  {
    return currentTree.getParent(id);
  }

  public RefTreeNode getRoot() {
    return currentTree.getRoot();
  }

  // Init

  protected void init() {
    // Read version history
    String[] deltas = REPOSITORY.list();
    Arrays.sort(deltas,Util.STRINGS_BY_LENGTH);
    int maxVer = -1;
    for( int i=0;i<deltas.length;i++) {
      int dotpos = deltas[i].indexOf('.');
      int ver = -1;
      try {
        if (dotpos != -1) ver = Integer.parseInt(deltas[i].substring(0,dotpos));
      } catch (NumberFormatException x) {
        Log.log("Ignoring file "+deltas[i]+" in repository",Log.WARNING);
        ; // Deliberately ignored
      }
      if( ver != -1 ) {
        versions.add(new Integer(ver));
        maxVer = ver > maxVer ? ver : maxVer;
      }
    }
    if( maxVer == -1 )
      nextVersion = Constants.FIRST_VERSION - 1; // First version
    else {
      // BUGFIX 20060920-2: Init did not recall current version on restart
      nextVersion = maxVer+1;
      versions.add(new Integer(maxVer));
    }
    Log.log("Init done; version list = "+versions,Log.INFO);
  }

  // Version history

  class VersionHistoryImpl implements VersionHistory {

    public int getCurrentVersion() {
      return VersionedDirectoryTree.this.getCurrentVersion();
    }

    public InputStream getData(int version) {
      // Null impl, facets handle this part
      return null;
    }

    public FullMetadata getMetadata(int mdVersion) {
      // Null impl, facets handle this part
      return null;
    }

    public int getPreviousData(int version) {
      return version > Constants.FIRST_VERSION ? version -1 : Constants.NO_VERSION;
    }

    public int getPreviousMetadata(int mdversion) {
      return mdversion > Constants.FIRST_VERSION ? mdversion -1 : Constants.NO_VERSION;
    }

    private int[] getVersions() {
      int[] verlist = new int[versions.size()];
      int pos = 0;
      for( Iterator i = versions.iterator();i.hasNext();) {
        verlist[pos++] = ((Integer) i.next()).intValue();
      }
      return verlist;
    }

    public boolean versionsEqual(int v1, int v2, boolean meta) {
      return v1 != Constants.NO_VERSION && v1 == v2;
    }

    public List getFullVersions(String branch) {
      List vers = new LinkedList();
      int[] vn = getVersions();
      for( int i=0;i<vn.length;i++)
        vers.add(new Version(vn[i],Version.TRUNK,vn[i]));
      return vers;
    }

    public boolean onBranch() {
      return false; // Local vhist is never on a branch
    }
  }

  /** Versioned directory tree maintenance routines. */
  public static class Maintenance {

    /** Remove a versioned directory tree.
     * @param root directory the tree is stored in
     */
    public static void clean(File root) {
      try {
        if( !root.exists() )
          return;
        if( !root.isDirectory() )
          Log.log("Repo location (now " + root + ") must be a directory",
                  Log.FATALERROR);
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
      VersionedDirectoryTree t = new VersionedDirectoryTree(root,null,false);
      if( !t.STORE_ROOT.exists() && !t.STORE_ROOT.mkdir() )
        Log.log("Can't create "+t.STORE_ROOT,Log.FATALERROR);
      if( !t.CURRENT_STORE.exists() && !t.CURRENT_STORE.mkdir() )
        Log.log("Can't create "+t.CURRENT_STORE,Log.FATALERROR);
      if( !t.REPOSITORY.exists() && !t.REPOSITORY.mkdir() )
        Log.log("Can't create "+t.REPOSITORY,Log.FATALERROR);
      StoredDirectoryTree.Maintenance.init(t.CURRENT_STORE,
                                    new RefTreeNodeImpl(null, ROOT_ID,
          new FIXMEDirNodeContent(ROOT_ID, null,DirectoryEntry.TREE)
          ));

    }
  }

  /** Codec for the root node. Should reuse some existing code. Previously used
   * a class in TestVersionedDirectoryTree, but dependence on test classes
   * is unacceptable.
   */


  public static class FIXMEDirNodeContent implements StoredDirectoryTree.Node {

    private StringKey id;
    private String name;
    private int type;


    public FIXMEDirNodeContent(StringKey aId, String aName, int aType) {
      id = aId;
      name = aName;
      type = aType;
    }

    public void setId(StringKey id) {
      this.id = id;
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setType(int type) {
      this.type = type;
    }

    public Key getId() {
      return id;
    }

    public String getName() {
      if( type == DirectoryEntry.TREE ) // The tree has no name attr!
        return null;
      return name;
    }

    public int getType() {
      return type;
    }

    public boolean equals(Object o) {
      return o instanceof DirectoryEntry && (
          Util.equals(((DirectoryEntry) o).getId(),getId()) &&
          Util.equals(((DirectoryEntry) o).getName(),getName()) &&
          ((DirectoryEntry) o).getType()==getType()
          );
    }

     public String getLocationId() {
       // BUGFIX 2005-Aug09-2: Init correct LID from start
       return GUID.getCurrentLocation();
     }

     public String getNextId() {
       return "#nextid#";
     }

     public String getUid() {
       return "#uid#-"+getId();
     }

     public int getVersion() {
       return 0;
     }

    public String getLinkNextId() {
      return "#nextid#";
    }

    public String getLinkUid() {
      return "#luid#";
    }

    public int getLinkVersion() {
      return 1;
    }

    public StringKey getLinkId() {
      return id;
    }

  }

}
// arch-tag: bd980797f630ec48b0162da4c33027d4 *-
