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

package fc.syxaw.tests;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

import junit.framework.Assert;

import fc.syxaw.api.SyxawFile;
import fc.syxaw.util.Util;
import fc.util.StringUtil;
import fc.util.log.Log;

/** Tests for directory synchronization mechanism. The tets includes
 * some inner classes useful for directory tree manipulation. */

public class DirectorySync extends NFSDTestBase {

  public DirectorySync(String name) {
    super(name,true);
  }


  /** Test mount, sync and unmount.
   * <ol>
   * <li>Create a local directory <code>/href</code>.</li>
   * <li>Create a remote directory <code>/anchor</code>,
   * linked to <code>/href</code>. Add a file in <code>/anchor</code></li>
   * <li>Sync <code>/href</code> and verify result</li>
   * <li>Unmount <code>/href</code></li>
   * </ol>
   * */
  public void testSimpleMountUmount() throws IOException {
    // NOTE: test requires -noac mount option (on both mounts)
    final String FILE = "file00.dat";
    final long FILE_LEN = 1234l, SEED = 314l;
    // Create a pair of linked dirtrees LOCAL_DIR--->REMOTE_DIR
    File rd = Config.TEST_REMOTEROOT_FILE;
    // Put a file in remote dir
    File rf = new File(rd, FILE);
    writeFile(rf,FILE_LEN,SEED);

    // Mount
    SyxawFile rsd = new SyxawFile(rd);
    String link = "localhost/";
    Assert.assertNotNull("Remote dir has null id", link);
    Log.log("File on remote =" + link, Log.INFO);
    File ld = Config.TEST_LOCALROOT_FILE;
    SyxawFile lsd = new SyxawFile(ld);
    try {
      lsd.mount(link);
    } catch (IOException x ) {
      Log.log("Mount failed ",Log.ERROR,x);
      Assert.fail("mount() failed");
    }
    Log.log("Successful mount()",Log.INFO);

    // Try to sync
    try {
      lsd.sync(true, true); // all, not only metadata
    } catch (IOException x ) {
      Log.log("Sync failed ",Log.ERROR,x);
      Assert.fail("sync() failed");
    }

    Log.log("Successful sync()",Log.INFO);
    // Verify sync result
    File lf = new File(ld, FILE);
    Assert.assertTrue("Remote file did not appear after sync()",lf.exists());
    Assert.assertTrue("Wrong number of files file in mounted dir",
                        ld.list(NO_DOT_SYXAW).length ==1);
    readFile(lf,FILE_LEN,SEED);
    Log.log("Verified sync result",Log.INFO);

    // Then umount()
    try {
      lsd.umount();
    } catch (IOException x ) {
      Log.log("umount() failed ",Log.ERROR,x);
      Assert.fail("umount() failed");
    }
    Log.log("Successful umount()",Log.INFO);

    // Verify that remote dir is intact
    // Verify sync result
    Assert.assertTrue("Remote file disappeared", rf.exists());
    Assert.assertTrue("Wrong number of files file in linked-to dir",
                      rd.list(NO_DOT_SYXAW).length == 1);
    readFile(rf, FILE_LEN, SEED);
    Log.log("Verified integrity of remote dir", Log.INFO);
  }

  /** Directory sync test with concurrent modfications
   * to the directory tree. Performs simple concurrent updates
   * to linked directory trees, and verify that they are correctly
   * merged when synchronized.
   */


  public void testSimpleTwoWaySync() throws IOException {

    //final String LOCAL_DIR = "", REMOTE_DIR = "";

    FileScript remoteScript1 = new FileScript( new FileOp[] {
       new FileOp("i,f00-remote"), new FileOp("I,d00-remote"),
       new FileOp("i,d00-remote/f01-remote")
    });

    FileScript localScript1 = new FileScript( new FileOp[] {
       new FileOp("i,f00-local"), new FileOp("I,d00-local"),
       new FileOp("i,d00-local/f01-local")
    });

    TestDir mergeResult1 = new TestDir(Config.TEST_LOCALROOT_FILE.getName()
                                       , new Node[] {
       new TestDir("d00-local", new Node[] {new TestFile("f01-local")}),
       new TestDir("d00-remote", new Node[] {new TestFile("f01-remote")}),
       new TestFile("f00-local"),
       new TestFile("f00-remote")
    });

    // Create a pair of linked dirtrees LOCAL_DIR--->REMOTE_DIR
    File rd = Config.TEST_REMOTEROOT_FILE;
//    Assert.assertTrue("Can't create dir on remote", rd.mkdir());

    // Mount
    SyxawFile rsd = new SyxawFile(rd);
    String link = "localhost/";
    Assert.assertNotNull("Remote dir has null id", link);
    Log.log("File on remote =" + link, Log.INFO);
    File ld = Config.TEST_LOCALROOT_FILE;
    SyxawFile lsd = new SyxawFile(ld);
    try {
      lsd.mount(link);
    } catch (IOException x ) {
      Log.log("Mount failed ",Log.ERROR,x);
      Assert.fail("mount() failed");
    }
    Log.log("Successful mount()",Log.INFO);

    // Do some file ops

    Assert.assertTrue(remoteScript1.exec(rd));
    Assert.assertTrue(localScript1.exec(ld));

    // Try syncing
    try {
      lsd.sync(true, true); // sync all, not only metadata
    }
    catch (IOException x) {
      Log.log("Sync failed ", Log.ERROR, x);
      Assert.fail("sync() failed");
    }
    Log.log("Successful sync()",Log.INFO);

    // Verify sync result (local)

    Assert.assertTrue("Local tree incorrect",verifyTree(ld,mergeResult1));
    Log.log("Verified local tree",Log.INFO);

    // Verify sync result (remote)
    mergeResult1.setName(Config.TEST_REMOTEROOT_FILE.getName());
    Assert.assertTrue("Remote tree incorrect",verifyTree(rd,mergeResult1));

    Log.log("Verified remote tree",Log.INFO);

  }

  /** Node in a directory tree. Designed for easy serialization of trees as XML
   *  Beans */
  public abstract static class Node implements Serializable,Comparable {
    public abstract Node[] getChildren();
    public abstract String getName();
    public abstract void setName(String n);
    protected Node parent = null;

    public static Node getNode( Node root, String path ) {
      return getNode(root, StringUtil.split(path,'/'),0);
    }

    public Node parent() {
      return parent;
    }

    public boolean treeEquals( Object o ) {
      /*if( !super.equals(o) )
        return false;*/
      Node[] ochildren = ((TestFile) o).getChildren();
      Node[] children = getChildren();
      if( ochildren == children )
        return true;
      if( ochildren.length != children.length)
        return false;
      for( int i=0; i<children.length;i++) {
        if( !children[i].treeEquals(ochildren[i]) )
          return false;
      }
      return true;
    }

    public static void initParents(Node r) {
      Node ch[] = r.getChildren();
      for( int i=0;ch!=null && i<ch.length;i++) {
        ch[i].parent = r;
        initParents(ch[i]);
      }
    }

    public String path() {
        if (parent == null)
          return "";
        return (parent.parent == null ? "" : parent.path() + "/") + getName();
    }

    protected static Node getNode( Node root, String[] path, int ix ) {
      if( ix == path.length )
        return root;
      String step = path[ix];
      if( Util.isEmpty(step) )
        return getNode( root, path, ix+1 ); // Skip empty steps = ////
      File fstep = new File(step); // We compare "File"s due to fs case issues
      Node[] chlist = root.getChildren();
      for( int ic=0;chlist != null && ic<chlist.length;ic++ ) {
        if ( (new File(chlist[ic].getName())).equals(fstep))
          return getNode(chlist[ic],path,ix+1);
      }
      return null;
    }

  }

  /** Node that has a name. */
  public static class TestFile extends Node {

    String name;

    public TestFile() {

    }

    public String getName() {
      return name;
    }

    public void setName(String aName) {
      name =aName;
    }

    public TestFile(String aName) {
      name = aName;
    }

    public Node[] getChildren() {
      return null;
    }

    public int compareTo(Object object) {
      return name.compareTo(((TestFile) object).name);
    }

    public boolean equals( Object o ) {
      return ( o instanceof TestFile ) &&
         Util.equals( ((TestFile) o).name,name);
    }

  }

  /** Node that has a name and children. */
  public static class TestDir extends TestFile {

    Node[] children = new Node[0];

    public TestDir() {

    }

    public TestDir(String aName) {
      super(aName);
    }

    public TestDir(String aName, Node[] aChildren) {
      super(aName);
      children = aChildren;
    }

    public Node[] getChildren() {
      return children;
    }

    public void setChildren(Node[] aChildren) {
      children=aChildren;
    }

    public void addChild( Node n ) {
      for(int i=0;children!=null && i<children.length;i++) {
        if((new File(children[i].getName())).equals(new File(n.getName())))
          Log.log("Duplicate entry",Log.FATALERROR);
      }
      Node[] nchildren = new Node[children.length+1];
      System.arraycopy(children,0,nchildren,0,children.length);
      nchildren[children.length]=n;
      n.parent=this;
      children=nchildren;
    }

    public void removeChild( Node n ) {
      for( int i=0;i<children.length;i++) {
        if( children[i]==n) {
          Node[] nchildren = new Node[children.length-1];
          System.arraycopy(children,0,nchildren,0,i);
          System.arraycopy(children,i+1,nchildren,i,children.length-(i+1));
          children=nchildren;
          n.parent = null;
          return;
        }
      }
      Log.log("Child not found!",Log.FATALERROR,n);
    }

  }

  /** Read tree off disk. */
  public TestFile readTree(File root ) {
    //TestFile f = null;
    if( root.isDirectory() ) {
      String[] list=root.list(NO_DOT_SYXAW);
      Node[] ch = new Node[list.length];
      for( int i=0;i<list.length;i++)
        ch[i]=readTree(new File(root,list[i]));
      Arrays.sort(ch);
      return new TestDir(root.getName(),ch);
    } else
      return new TestFile(root.getName());
  }

  /** Read tree off disk. */
  public TestFile readTree(fc.syxaw.hierfs.SyxawFile root ) {
    if( root.isDirectory() ) {
      String[] list=root.list();
      Node[] ch = new Node[list.length];
      for( int i=0;i<list.length;i++)
        ch[i]=readTree(root.newInstance(root,list[i]));
      Arrays.sort(ch);
      return new TestDir(root.getName(),ch);
    } else
      return new TestFile(root.getName());
  }

  /** Verify that a directory tree is identical to a tree on disk. */
  public boolean verifyTree(File root, Node rootn ) throws IOException {
    Node root2 = readTree(root);
    if( !root2.treeEquals(rootn) ) {
      System.err.println("verifyTree(): Differing trees. On disk=");
      Util.writeObjectAsXML(System.err,root2);
      System.err.println("Correct tree is:");
      Util.writeObjectAsXML(System.err,rootn);
      return false;
    } else
      return true;
  }

  /** A file system operation. Valid operation strings are:
   * <dl>
   * <dt>i,<i>f</i></dt><dd>Insert file <i>f</i></dd>
   * <dt>I,<i>d</i></dt><dd>Create dir <i>d</i></dd>
   * <dt>d,<i>f</i></dt><dd>Delete subtree rooted at <i>f</i></dd>
   * <dt>m,<i>s</i>,<i>d</i></dt>
   * <dd>Rename <i>s</i> to <i>d</i></dd>
   *</dl>
   * */

  public static class FileOp implements Serializable {
    String op;
    public FileOp() {}
    public FileOp( String aOp) {
      op=aOp;
    }

    public String getOperation() {
      return op;
    }

    public void setOperation(String aOp) {
      op = aOp;
    }

    /** Execute operation.
     * @param root root of file names
     * @throws IOException
     */
    public void exec(File root) throws IOException {
      String[] params = StringUtil.split(op,',');
      String name = params[1];
      switch( params[0].charAt(0) ) {
        case 'i':
          if( !(new File(root,name)).createNewFile() )
            throw new IOException("file insert: "+name);
          Log.log("INS "+name,Log.INFO);
          break;
        case 'I':
          if( !(new File(root,name)).mkdir() )
            throw new IOException("dir insert: "+name);
          Log.log("MKDIR "+name,Log.INFO);
          break;
        case 'd':
          if( !delTree(new File(root,name),(File) null) )
            throw new IOException("deltree: "+name);
          Log.log("DEL "+name,Log.INFO);
          break;
        // Possibly 'u'=update
        case 'm':
          if( !(new File(root,name)).renameTo(new File(root,params[2])) )
            throw new IOException("rename: "+name+"->"+params[2]);
          Log.log("MV "+name+"->"+params[2],Log.INFO);
          break;
        default:
          throw new IOException("Unknown op="+params[0]);
      }
    }

    /** Execute operation.
     * @param root root of file names
     * @throws IOException
     */
    public void exec(fc.syxaw.hierfs.SyxawFile root) throws IOException {
      String[] params = StringUtil.split(op,',');
      String name = params[1];
      switch (params[0].charAt(0)) {
        case 'i':
          if (! root.newInstance(root, name).createNewFile())
            throw new IOException("file insert: " + name);
          Log.log("INS " + name, Log.INFO);
          break;
        case 'I':
          if (! root.newInstance(root, name).mkdir())
            throw new IOException("dir insert: " + name);
          Log.log("MKDIR " + name, Log.INFO);
          break;
        case 'd':
          if (!delTrees(root.newInstance(root, name)))
            throw new IOException("deltree: " + name);
          Log.log("DEL " + name, Log.INFO);
          break;
          // Possibly 'u'=update
        case 'm':
          if (! root.newInstance(root, name).renameTo(root.newInstance(root, params[2])))
            throw new IOException("rename: " + name + "->" + params[2]);
          Log.log("MV " + name + "->" + params[2], Log.INFO);
          break;
        default:
          throw new IOException("Unknown op=" + params[0]);
      }
    }

    protected static boolean delTrees(fc.syxaw.hierfs.SyxawFile f) {
      if (f.isDirectory()) {
        String[] entries = f.list();
        boolean ok = true;
        for (int i = 0; i < entries.length && ok; i++)
          ok = ok & delTrees( f.newInstance(f, entries[i]));
        if (!ok)
          return false;
      }
      if (true) {
        boolean delOK = f.delete();
        if(!delOK) Log.log("Can't delete "+f,Log.WARNING);
        return delOK;
      } else
        return true;
    }

  }

  /** File system edit script. A file system edit script is an
   * orderd list of file operations.*/
  public static class FileScript implements Serializable {

    FileOp[] script=null;

    public FileScript() {

    }

    public FileScript(FileOp[] ops){
      script=ops;
    }

    public void setOperations(FileOp[] ops) {
      script = ops;
    }

    public FileOp[] getOperations() {
      return script;
    }

    public String toString() {
      StringBuffer sb = new StringBuffer();
      for(int i=0;i<script.length;i++)
        sb.append(""+(i+1)+": "+script[i].op+"\n");
      return sb.toString();
    }
    /** Execute script.
     * @param root root of file names
     */
    public boolean exec(File root) {
      return exec(root,0,script.length);
    }

    public boolean exec(File root,int off, int len) {
      int i=0;
      try {
        for (i = off; i < off+len; i++)
          script[i].exec(root);
        return true;
      } catch( IOException x ) {
        Log.log("Error at step "+i+": "+x.getMessage(),Log.ERROR);
      }
      return false;
    }

    /** Execute script.
     * @param root root of file names
     */
    public boolean exec(fc.syxaw.hierfs.SyxawFile root) {
      return exec(root,0,script.length);
    }

    public boolean exec(fc.syxaw.hierfs.SyxawFile root, int off, int len) {
      int i = 0;
      try {
        for (i = off; i < off+len; i++)
          script[i].exec(root);
        return true;
      }
      catch (IOException x) {
        Log.log("Error at step " + i + ": " + x.getMessage(), Log.ERROR);
      }
      return false;
    }

  }

}
// arch-tag: 2bd13e6fdea37020f9f8bc80cfb1770f *-
