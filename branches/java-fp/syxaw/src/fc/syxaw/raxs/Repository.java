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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import fc.raxs.DeltaStream;
import fc.raxs.RandomAccessXmlStore;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.FullMetadataImpl;
import fc.syxaw.fs.SyxawFile;
import fc.syxaw.fs.VersionHistory;
import fc.util.IOUtil;
import fc.util.log.Log;

public class Repository implements fc.syxaw.fs.Repository {

  RandomAccessXmlStore raxs;
  
  public Repository(RandomAccessXmlStore raxs) {
    this.raxs = raxs;
  }

  public FullMetadata commit(SyxawFile f, InputStream data,
      FullMetadata meta, boolean link, int version) throws IOException {
   return commit (f, data, meta, link, version, true, true);
}
  public FullMetadata commit(SyxawFile f, InputStream data,
      FullMetadata meta, boolean link, int version, boolean gotData, boolean gotMetadata) throws IOException {
    //!A assert version == Constants.NO_VERSION : "caller breaking interface contract";
    //Log.debug("Commit called with data="+data+" from ",new Throwable());
    FullMetadataImpl fmd = FullMetadataImpl.createFrom(meta);
    if( data != null ) {
      DeltaOutputStream out = new DeltaOutputStream( f, raxs,
          data instanceof DeltaStream ? ((DeltaStream) data).getBaseversion() :
            Constants.NO_VERSION, false);
      IOUtil.copyStream(data, out);
      out.close(); // Also waits for consumer
      int nver = raxs.getCurrentVersion();
      Log.info("Committed version "+nver);
      fmd.setDataVersion(nver);
      fmd.setLength(raxs.getConfiguration().getStoreFile().length());
    }
    if( meta != null ) {
      Log.warning("Not committing any metadata");
    }
    return fmd;
  }

  public VersionHistory getVersionHistory(SyxawFile f) {
    return new RaxsHistory( raxs.getVersionHistory() );
  }

  public int getNextVersion(SyxawFile f) throws FileNotFoundException {
    return raxs.getNextVersion();
  }
  
}

// arch-tag: 2fd66a0a-6f89-4122-a671-dc255f1413e5
