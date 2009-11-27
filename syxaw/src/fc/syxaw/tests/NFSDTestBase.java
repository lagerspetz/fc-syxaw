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

// $Id: NFSDTestBase.java,v 1.10 2004/12/03 09:31:59 ctl Exp $
package fc.syxaw.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import junit.framework.Assert;
import junit.framework.TestCase;

import fc.syxaw.util.Util;
import fc.util.log.Log;

// Alls tests derived from this expects an NFS mount. You MUST mount noac
// (no attribute caching), otherwise file ops made trough the syxaw API may not
// be visible to the NFS mount => tests will fail errorneously
// example: file is created by SyxawFile.createLink (accesses image dir directly)
//          the file is not immediately visible to NFS if ac is enabled =>
//          FileNotFound error whenaccessing trough NFS

/** Base class for tests expecting Syxaw to be mounted as a file system accessible
 * to the OS.
 * Currently, Syxaw is mounted as a NFS file system.
 * <p>You <b>must</b> mount NFS using the <code>noac</code> (no attribute caching)
 * oprion, otherwise file operations made through internal Syxaw classes may not
 * be visible to the NFS mount => tests will fail errorneously.
 * <p>The NFS mounts are assumed to reside in the directories given by
 * {@link Config#TEST_LOCALROOT} and {@link Config#TEST_REMOTEROOT}. The directories
 * must be empty in order to run the tests. After the test has been run,
 * the directories are emptied automatically.
 */


public abstract class NFSDTestBase extends TestCase {

  protected static final long CHUNK = 32768l; // Max read-write chunk in bytes

  protected boolean needsRemote = false;

  /** Create NFS test.
   *
   * @param name name of test
   * @param aNeedsRemote <code>true</code> means test needs remote device
   * mounted through NFS. The local device must always be mountes trough NFS.
   */
  public NFSDTestBase(String name, boolean aNeedsRemote) {
    super(name);
    needsRemote = aNeedsRemote;
  }

  protected void setUp() throws java.lang.Exception {
    /*File f = new File(TestFsRunner.TESTCONFIG_FILE);
    Assert.assertTrue("Cannot find test configuration " + f.getAbsoluteFile(),
                      f.exists());
    Util.loadConfiguration(f.toString());*/
    setUp( Config.TEST_LOCALROOT_FILE );
    if( needsRemote )
      setUp( Config.TEST_REMOTEROOT_FILE );
  }

  public static FilenameFilter NO_DOT_SYXAW =  new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return !name.startsWith(".");
      }

    };


  protected void setUp(File mountDir) throws java.lang.Exception {
    super.setUp();
    Assert.assertNotNull("FS mount dir not accessible: ",
                         mountDir);
    // Here we actually exercise the fs a bit...
    String[] nfsList = mountDir.list(NO_DOT_SYXAW);
    Assert.assertTrue("Expecting to run test on clean fs",
                      nfsList != null && nfsList.length == 0);
    // Do some magic (= query special backdoor file)
    // to see if this is really a syxaw fs
    /*
    Assert.assertTrue("Mount does not appear to be a Syxaw fs",
                      (new File(mountDir,
                                fc.syxaw.storage.hierfs.Config.
                                CMDFILE_PREFIX + "ping")).exists());*/
  }


  public void tearDown() throws java.lang.Exception {
    Assert.assertTrue("Post-test local fs cleanup failed",delTree(
        Config.TEST_LOCALROOT_FILE,Config.TEST_LOCALROOT_FILE));
    if( needsRemote )
      Assert.assertTrue("Post-test remote fs cleanup failed",delTree(
          Config.TEST_REMOTEROOT_FILE,Config.TEST_REMOTEROOT_FILE));

    super.tearDown();
  }

  protected long writeFile( File f, long length, long seed ) throws IOException{
    Random r = new Random(seed);
    FileOutputStream out = new FileOutputStream(f);
    long time = System.currentTimeMillis();
    for( long left=length;left>0;) {
      long chunk = Math.min(left,CHUNK);
      byte[] data = new byte[(int) chunk];
      r.nextBytes(data);
      out.write(data);
      left -= chunk;
    }
    out.close();
    return System.currentTimeMillis()-time;
  }

  protected long writeFile( File f, InputStream in ) throws IOException{
    FileOutputStream out = new FileOutputStream(f);
    long time = System.currentTimeMillis();
    try {
      Util.copyStream(in,out);
    } finally {
      out.close();
      in.close();
    }
    return System.currentTimeMillis()-time;
  }

  protected long readFile( File f, long length, long seed ) throws IOException{
    Random r = new Random(seed);
    FileInputStream in = new FileInputStream(f);
    long time = System.currentTimeMillis();
    byte[] data = new byte[(int) CHUNK];
    int chunk = 0;
    do {
      chunk = in.read(data);
      if( chunk > 0 ) {
        byte[] verify = new byte[chunk];
        r.nextBytes(verify);
        for( int i=0;i<verify.length;i++)
          Assert.assertEquals("Data corruption",verify[i],data[i]);
        length -= (long) chunk;
      }
    } while( chunk > 0 );
    Assert.assertTrue("File length changed",length == 0 );
    in.close();
    return System.currentTimeMillis()-time;
  }

  protected long readFile( File f, InputStream verify ) throws IOException{
    FileInputStream in = new FileInputStream(f);
    long time = System.currentTimeMillis();
    try {
      Util.copyStream(in,System.err);
    } finally {
      verify.close();
      in.close();
    }
    return System.currentTimeMillis()-time;
  }

  protected long dumpFile( File f, String explain ) throws IOException{
    FileInputStream in = new FileInputStream(f);
    long time = System.currentTimeMillis();
    Log.log("File dump: "+explain+" (file="+f+")",Log.INFO);
    try {
      Util.copyStream(in,System.err);
    } finally {
      in.close();
    }
    return System.currentTimeMillis()-time;
  }

  /** Delete tree.
   * @param f root of deletion. All files below and including <code>f</code>
   * will be deleted.
   * @param exclude file to exclude from deletion. Can only be set to
   * f in practice.
   */

  protected static boolean delTree(File f, File exclude) {
    // NOTE1: rm -rf sometimes seems to 'skip' files
    // Possible reason is incoherence between NFS cached directories and
    // actual directories. Note that the some of the sync API functions
    // may touch the image dir directly => not visible to NFS without
    // NFS RPCs, which at least the Linux NFS client seems to do asynchronously
    // NOTE2: I've read some posts on this behaviour on other NFSds as well
    // Anyway, this is the best theory I've got. Simply retrying seems to
    // work fine..
    boolean success=false;
    for(int i=0;i<10 && !(success|=doDelTree(f,exclude));i++) {
      Log.log("NFS delTree didn't suceed, retrying "+i,Log.WARNING);
      try { Thread.sleep(500); } catch (InterruptedException x ) {}
    }
    return success;
  }


  protected static boolean doDelTree(File f, File exclude) {
    if (f.isDirectory()) {
      String[] entries = f.list(NO_DOT_SYXAW);
      boolean ok = true;
      for (int i = 0; i < entries.length && ok; i++)
        ok = ok & doDelTree(new File(f, entries[i]), exclude);
      if (!ok)
        return false;
    }
    if (!f.equals(exclude)) {
      boolean delOK = f.delete();
      if(!delOK) Log.log("Cant delete "+f,Log.WARNING);
      return delOK;
    } else
      return true;
  }

}
// arch-tag: ad5f61ab9e4b901e1917022db834d00a *-
