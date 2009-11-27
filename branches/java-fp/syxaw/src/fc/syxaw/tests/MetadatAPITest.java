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

// $Id: MetadatAPITest.java,v 1.6 2003/10/13 13:30:49 ctl Exp $
package fc.syxaw.tests;

import java.io.File;
import java.io.IOException;

import junit.framework.Assert;

import fc.syxaw.api.Metadata;
import fc.syxaw.api.MetadataImpl;
import fc.syxaw.api.SyxawFile;

/** Test the Syxaw metadata API. */
public class MetadatAPITest extends NFSDTestBase {

  public MetadatAPITest(String name) {
    super(name,false);
  }

  /** Tests the metdata API.
   * <ol>
   * <li>Create a file.</li>
   * <li>Verify file has an guid.</li>
   * <li>Set and verify the <code>type</code> attribute.</li>
   * </ol>
   */


  public void testMetadata() throws IOException {
    final String TEST_TYPE="testtype";
    File f = new File( Config.TEST_LOCALROOT_FILE, "testdir" );
    Assert.assertTrue("Can't create testdir",f.mkdir());
    f = new File( Config.TEST_LOCALROOT_FILE, "testdir/metatest.xml" );
    f.createNewFile();
    SyxawFile sf = new SyxawFile(f);
    String id = sf.getId();
    Assert.assertNotNull("File has no id",id);
    MetadataImpl md = MetadataImpl.createFrom( sf.getMetadata() );
    Assert.assertNotNull("File has no md",md);
    md.setType(TEST_TYPE);
    sf.setMetadata(md);
    Metadata md2 = sf.getMetadata();
    Assert.assertEquals("Incorrect type",TEST_TYPE,md2.getType());
  }

}// arch-tag: 652a1adb4c4c804485d558b5abd0f5f8 *-
