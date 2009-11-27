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

// $Id: ExternalMerger.java,v 1.2 2004/11/19 16:14:47 ctl Exp $
// History:
// Moved from fc.fp.syxaw.fs at ExternalMerger.java,v 1.1
package fc.syxaw.api.impl;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import fc.syxaw.fs.BLOBStorage;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.ObjectMerger;
import fc.syxaw.fs.SyxawFile;
import fc.syxaw.merge.DefaultObjectMerger;
import fc.syxaw.util.Util;
import fc.util.log.Log;

/** Class bridging Syxaw internal and API mergers. This class acts as a
 * translator between the more complex {@link fc.syxaw.fs.ObjectMerger}
 * internal merging interface and the {@link fc.syxaw.api.Syxaw.Merger}
 * class in the Syxaw API.
 */

public class ExternalMerger extends DefaultObjectMerger  {

  // BUGFIX-20061018-10: private SyxawFile f shadowed ancestor field
  private fc.syxaw.api.Syxaw.Merger em;
  private int baseVersion;

  /** Construct a merger.
   *
   * @param f SyxawFile the merger is for
   * @param currentVersion current version of f
   * @param apiMerger Syxaw API merger to use for merging data
   */
  public ExternalMerger(SyxawFile f, int currentVersion,
                        fc.syxaw.api.Syxaw.Merger apiMerger) {
    super(f,f.getLinkVersionHistory(false),currentVersion); // FIXME-versions
    this.em = apiMerger;
  }

  public int mergeData(boolean currentChanged, boolean downloadChanged,
                       int downloadVersion, BLOBStorage downloadData,
                       int dlVerRef) throws ObjectMerger.Conflict, IOException {
    InputStream mergeIn = null;
    InputStream baseIn = null;
    InputStream localIn = null;
    InputStream remoteIn = null;
    OutputStream fout = null;
    try {
      if (baseVersion != Constants.NO_VERSION)
        baseIn = f.getLinkVersionHistory(false).getData(baseVersion);
      if (dlVerRef != Constants.NO_VERSION)
        remoteIn = f.getLinkVersionHistory(false).getData(dlVerRef);
      else
        remoteIn = downloadData.getInputStream();
      try {
        mergeIn =
            em.mergeData(currentChanged, downloadChanged, baseIn, localIn,
                         remoteIn);
      } catch (fc.syxaw.api.Syxaw.MergeConflict ex) {
        throw new ObjectMerger.Conflict(ex.getMessage());
      }
      // Apply data
      fout = f.getLinkOutputStream(false);
      Util.copyStream(mergeIn, fout);
    } catch (FileNotFoundException ex1) {
      Log.log("Nonexisting file "+f,Log.ERROR);
    } finally {
      if( fout != null )
        try { fout.close(); } catch (IOException x)
        {Log.log("Close failed",Log.FATALERROR);}
      if( baseIn != null )
        try { baseIn.close(); } catch (IOException x)
        {Log.log("Close failed",Log.FATALERROR);}
      if( localIn != null )
        try { localIn.close(); } catch (IOException x)
        {Log.log("Close failed",Log.FATALERROR);}
      if( remoteIn != null )
        try { remoteIn.close(); } catch (IOException x)
        {Log.log("Close failed",Log.FATALERROR);}
      if( mergeIn != null )
        try { mergeIn.close(); } catch (IOException x)
        {Log.log("Close failed",Log.FATALERROR);}
      if( downloadData != null )
        downloadData.delete();
    }
    return Constants.NO_VERSION;
  }

}
// arch-tag: 9de0cf0e89377a59eaa49285ff9337f5 *-
