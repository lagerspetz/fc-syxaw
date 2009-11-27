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

package fc.syxaw.raxs;

import java.io.IOException;
import java.io.InputStream;

import fc.raxs.DeltaStream;
import fc.raxs.NoSuchVersionException;
import fc.raxs.RandomAccessXmlStore;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.SyxawFile;
import fc.util.io.DelayedOutputStream;
import fc.util.log.Log;
import fc.xml.xas.ItemSource;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.XmlrDebug;
import fc.xml.xmlr.model.TreeModel;
import fc.xml.xmlr.tdm.Diff;
import fc.xml.xmlr.xas.XasSerialization;

public class DeltaOutputStream extends DelayedOutputStream implements DeltaStream {

  RandomAccessXmlStore raxs;
  SyxawFile f;
  
  protected int baseVersion = Constants.NO_VERSION;

  public DeltaOutputStream( SyxawFile f,
      RandomAccessXmlStore raxs, int baseVersion, boolean append) {
    if( append )
      throw new IllegalArgumentException("You cannot append a stream to a RAXS store");
    this.raxs = raxs;
    this.baseVersion = baseVersion;
    this.f = f;
  }

  /*!5 @Override */
  protected void stream(InputStream data) throws IOException {
    //try {
    int baseVer = baseVersion;
    // New data to commit
    ItemSource is = raxs.getParser(data);
    RefTree nt = null;
    TreeModel tm = raxs.getModel();
    Log.info("Tree model is ",tm);
    if( baseVer != Constants.NO_VERSION ) {
      IdAddressableRefTree base = raxs.getTree(baseVer);
      Diff d = Diff.readDiff(is, tm, base.getRoot().getId());
      nt=d.decode(base);
    } else
      nt = XasSerialization.readTree(is, tm);
    Log.info("Tree to commit is");
    XmlrDebug.dumpTree(nt);
    int nver=raxs.commit(nt);
    if( nver == Constants.NO_VERSION )
      throw new IOException("Commit failed.");
    Log.info("Wrote version "+nver);
    // Flag changes
    f.setLink(null, Boolean.TRUE, Boolean.TRUE, true, true);
    f.setLocal(null, Boolean.TRUE, Boolean.TRUE);
    /*} catch( Throwable t ) {
      Log.error("Waiting 10s due to exception",t);
      Debug.sleep(10000);
    }*/
  }

  public void setBaseVersion(int version) throws NoSuchVersionException {
    baseVersion = version;
  }

  public long getDeltaSize() {
    return -1l;
  }

  public int getBaseversion() {
    return baseVersion;
  }

}
// arch-tag: 3a16b6ba-75a4-435c-bc8e-e9379b89e0fc
