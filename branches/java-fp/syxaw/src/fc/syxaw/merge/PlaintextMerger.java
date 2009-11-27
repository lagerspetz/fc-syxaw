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

package fc.syxaw.merge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.Patch;
import fc.syxaw.fs.BLOBStorage;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.ObjectMerger;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.hierfs.SyxawFile;
import fc.util.IOUtil;
import fc.util.log.Log;

public class PlaintextMerger extends DefaultObjectMerger {

  public static final String MIME_TYPE = "text/plain";
  
  public PlaintextMerger(SyxawFile f, VersionHistory h, int currentVersion) {
    super(f,h,currentVersion);
  }

  @Override
  public int mergeData(boolean currentChanged, boolean downloadChanged,
      int downloadVersion, BLOBStorage downloadData, int dlVerRef)
      throws IOException, Conflict {

    InputStream baseIn = null;
    InputStream in = null;
    InputStream remoteIn = null;
    BLOBStorage mergeds = null;
    OutputStream os = null;

    // NOTE: if current Version = NO_VERSION, we were never sync'd with remote
    // and cannot merge, since no common base exists!
    if( !(downloadChanged && currentChanged 
        && currentVersion != Constants.NO_VERSION ) ) {
      // current unchanged, no need to merge
      return super.mergeData(currentChanged, downloadChanged, downloadVersion,
          downloadData, dlVerRef);
    }
    try {
      // 3-way merge needed. 
      baseIn = history.getData(currentVersion);
      if (baseIn == null)
        throw new ObjectMerger.Conflict(
            "Base not in version store: missing version is " +
            currentVersion);
      // in = local tree
      in = f.getInputStream();
      // remoteIn = downloaded tree
      remoteIn =
          dlVerRef == Constants.NO_VERSION ?
          downloadData.getInputStream() :
          history.getData(dlVerRef);
      if (remoteIn == null)
        throw new ObjectMerger.Conflict(
            "Version referenced not remoteIn version " +
            "store: missing version is " + currentVersion);
      mergeds = f.createStorage("merge", true);
      os = null;
      ByteArrayOutputStream clos = new ByteArrayOutputStream();
      os = mergeds.getOutputStream(false);
      textMerge(baseIn, in, remoteIn, os, clos);
      os.close();
      clos.close();
      os = null;
      f.rebindLinkStorage(mergeds);
      mergeds = null;
      if (clos.size() > 0) {
        throw new ObjectMerger.Conflict("Merge conflict.",
            new ByteArrayInputStream(clos.toByteArray()));
      }
    } finally {
      if( baseIn != null )
        baseIn.close();
      if( in != null )
        in.close();
      if( remoteIn != null )
        remoteIn.close();
      if( os != null )
        os.close();
      if( mergeds != null )
        mergeds.delete();
    }
    return Constants.NO_VERSION; // Created as of yet unassigned version
  }

  private void textMerge(InputStream baseIn, InputStream oneIn,
      InputStream twoIn, OutputStream out, ByteArrayOutputStream conflicts) 
    throws IOException {
    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
    IOUtil.copyStream(baseIn, bOut);
    ByteArrayOutputStream oneOut = new ByteArrayOutputStream();
    IOUtil.copyStream(oneIn, oneOut);
    ByteArrayOutputStream twoOut = new ByteArrayOutputStream();
    IOUtil.copyStream(twoIn, twoOut);
    String base = bOut.toString("UTF-8");
    String one = oneOut.toString("UTF-8");
    String two = twoOut.toString("UTF-8");
    Log.info("Plaintext merge, base=", base);
    Log.info("one=", one);
    Log.info("two=", two);
    diff_match_patch dmp = new diff_match_patch();
    LinkedList<Diff> diffs = dmp.diff_main(base, one);
    LinkedList<Patch> patches = dmp.patch_make(diffs);
    Object[] patchResult = dmp.patch_apply(patches, two);
    String merge = (String) patchResult[0];
    Log.info("merge=", merge);
    boolean[] stats = (boolean[]) patchResult[1];
    // Check status
    boolean success = true;
    for( boolean patchOk : stats ) {
      success &= patchOk;
    }
    Log.info("success="+success);
    if (success && merge != null) {
      out.write(merge.getBytes("UTF-8"));
    } else {
      conflicts.write("Could not merge texts".getBytes("UTF-8"));
    }
  }

  public static class MergerFactory implements ObjectMerger.MergerFactory {
    public ObjectMerger newMerger(fc.syxaw.fs.SyxawFile f,
                                  boolean linkFacet) throws
            FileNotFoundException {
      if (!linkFacet) {
        Log.log("This release of Syxaw only support merging of the link facet",
                Log.ASSERTFAILED);
      }
      SyxawFile file = (SyxawFile) f;
      return new PlaintextMerger(file, file.getLinkVersionHistory(false),
                                 file.getLinkDataVersion());//FIXME-versions:

    }

  }

}
// arch-tag: 8171d99f-935f-4ebb-b02f-18e4bc9acde3
//
