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

// $Id: XMLMerger.java,v 1.16 2004/11/18 13:50:53 ctl Exp $
package fc.syxaw.merge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import fc.syxaw.api.Metadata;
import fc.syxaw.fs.BLOBStorage;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.ObjectMerger;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.hierfs.SyxawFile;
import fc.syxaw.util.XmlUtil;
import fc.util.log.Log;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.tdm.Merge;
import fc.xml.xmlr.xas.UniformXasCodec;

/** Generic XML reconciliation algorithm. Implements the {@link ObjectMerger}
 * interface for the <code>3dm</code> XML three-way merging and differencing tool,
 * see <a href="http://www.cs.hit.fi/u/ctl/3dm">the 3dm website</a>.
 * <p>When a conflict occurs, the <code>3dm</code> conflict log is passed in the
 * {@link fc.syxaw.fs.ObjectMerger.Conflict}
 */

public class XMLMerger extends DefaultObjectMerger implements Merge.NodeMerger {

  public XMLMerger(SyxawFile f, VersionHistory h, int currentVersion) {
    super(f,h,currentVersion);
  }

  public int mergeData(boolean currentChanged, boolean downloadChanged,
                       final int downloadVersion, final BLOBStorage downloadData,
                       final int dlVerRef)
      throws IOException, ObjectMerger.Conflict {
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
      return super.mergeData(currentChanged, downloadChanged, downloadVersion, downloadData, dlVerRef);
    }
    try {
      // 3-way merge needed. Parse all required trees
      // tb = old common version
      baseIn = history.getData(currentVersion);
      if (baseIn == null)
        throw new ObjectMerger.Conflict(
            "Base not in version store: missing version is " +
            currentVersion);
      IdAddressableRefTree tb = RefTrees.getAddressableTree(
          XmlUtil.readRefTree(baseIn, UniformXasCodec.ITEM_CODEC ));

      // t1 = local tree
      in = f.getInputStream();
      IdAddressableRefTree t1 = RefTrees.getAddressableTree(
          XmlUtil.readRefTree(in, UniformXasCodec.ITEM_CODEC ));

      // t1 = downloaded tree

      remoteIn =
          dlVerRef == Constants.NO_VERSION ?
          downloadData.getInputStream() :
          history.getData(dlVerRef);
      if (remoteIn == null)
        throw new ObjectMerger.Conflict(
            "Version referenced not remoteIn version " +
            "store: missing version is " + currentVersion);

      IdAddressableRefTree t2 = RefTrees.getAddressableTree(
          XmlUtil.readRefTree(remoteIn, UniformXasCodec.ITEM_CODEC ));

      mergeds = f.createStorage("merge", true);
      os = null;

      ByteArrayOutputStream clos = new ByteArrayOutputStream();
      os = mergeds.getOutputStream(false);
      /*Log.log("Dumping trees: base, t1, t2",Log.INFO);
      XmlUtil.writeRefTree(tb, System.err, XasSerialization.getGenericCodec());
      XmlUtil.writeRefTree(t1, System.err, XasSerialization.getGenericCodec());
      XmlUtil.writeRefTree(t2, System.err, XasSerialization.getGenericCodec());*/

      Merge.merge(tb,t1,t2,
                  XmlUtil.getDocumentSerializer(os),
                  XmlUtil.getDocumentSerializer(clos),
                  UniformXasCodec.ITEM_CODEC,this);
      os.close();
      clos.close();
      os = null;
      f.rebindLinkStorage(mergeds);
      mergeds = null;
      if (clos.size() > 0) {
        throw new ObjectMerger.Conflict("Merge conflict.",new ByteArrayInputStream(clos.
            toByteArray()));
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

  public int mergeMetadata(boolean currentChanged, boolean downloadChanged,
                           int downloadVersion, Metadata downloadMeta,
                           int dlVerRef) throws
      IOException, ObjectMerger.Conflict {
    // Just do default meta processing
    return super.mergeMetadata(currentChanged, downloadChanged, downloadVersion,
                               downloadMeta, dlVerRef);
  }

  public RefTreeNode merge(RefTreeNode base, RefTreeNode n1, RefTreeNode n2) {
    return null; // Currently, always return conflict on concurrent tag changes
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
      return new XMLMerger(file, file.getLinkVersionHistory(false),
                                 file.getLinkDataVersion());//FIXME-versions:

    }

  }
}
// arch-tag: 7eb18389b4c3c0b7f5d03c63ed422b90 *-
