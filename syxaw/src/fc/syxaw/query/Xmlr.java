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

package fc.syxaw.query;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import fc.syxaw.fs.SyxawFile;
import fc.syxaw.util.XmlUtil;
import fc.util.NonListableSet;
import fc.util.io.DelayedOutputStream;
import fc.util.log.Log;
import fc.xml.xas.ItemList;
import fc.xml.xas.ItemSource;
import fc.xml.xas.TransformSource;
import fc.xml.xas.XasUtil;
import fc.xml.xas.transform.DataItems;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.MutableRefTree;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.TreeModel;
import fc.xml.xmlr.model.TreeModels;
import fc.xml.xmlr.xas.UniformXasCodec;
import fc.xml.xmlr.xas.XasSerialization;

public abstract class Xmlr implements QueryProcessor {

  protected static final TreeModel contentCodec =
    TreeModels.xmlr1Model().swapCodec(UniformXasCodec.ITEM_CODEC);

  public abstract InputStream getInputStream(SyxawFile f, String id,
                                    boolean linkFacet) throws
          FileNotFoundException;

  public OutputStream getOutputStream(SyxawFile f, String query,
                                      boolean linkFacet) throws
          FileNotFoundException {
    return new XmlrOutputStream(f, linkFacet, query);
  }

  /*public long getLength(SyxawFile f, String query, boolean linkFacet) throws
          FileNotFoundException {
    return Constants.NO_SIZE;
  }*/

  // Generic XMLR writer. Doe to the nature of XMLR, the actual query
  // is irrelevant. (Perhaps it should be synatx-checked, though...)

  protected static class XmlrOutputStream extends DelayedOutputStream {

    final fc.syxaw.fs.SyxawFile f;
    final boolean linkFacet;

    public XmlrOutputStream(fc.syxaw.fs.SyxawFile f, boolean linkFacet,
                              String dummyQuery ) {
      super();
      this.f = f;
      this.linkFacet = linkFacet;
    }

    protected void stream(InputStream in) throws IOException {
      OutputStream bout = null;
      InputStream bin = null;
      try {
        bin = linkFacet ? f.getLinkInputStream() : f.getInputStream();
        DataItems dt = new DataItems();
        ItemSource das = new TransformSource(XmlUtil.getXmlParser(bin),dt);
        IdAddressableRefTree tna = RefTrees.getAddressableTree(
              // Possibility to test in instanceof RefTreeStream
              XasSerialization.readTree(das, contentCodec));

        bin.close();
        bin = null;

        MutableRefTree t = RefTrees.getMutableTree(tna);
        RefTree update = XmlUtil.readRefTree(in, contentCodec );
        try {
          RefTrees.apply(update, t);
        } catch (NodeNotFoundException ex) {
          throw new MalformedQueryException("No such node in file: " + ex.getId());
        }
        Log.log("Successfully applied update to reftree",Log.INFO);
        bout = linkFacet ? f.getLinkOutputStream(false) :
               f.getOutputStream(false);
        ItemList preamble = new ItemList();
        XasUtil.copy(dt.getPreamble().source(),preamble);
        XasUtil.copy(XasSerialization.DOC_HEADER_UTF8.source(),preamble);
        //Log.log("Preamble is "+preamble,Log.INFO);
        XasSerialization.writeTree(t, 
            XmlUtil.getDocumentSerializer(out), contentCodec, 
            XasUtil.itemSource( preamble ), 
            XasUtil.itemSource( XasSerialization.DOC_TRAILER_UTF8 ) );
      } finally {
        try {
          if (bin != null)
            bin.close();
        } finally {
          if (bout != null)
            bout.close();
        }
      }
    }
  }

  protected RefTree queryTree(IdAddressableRefTree t, final Set fullyExpanded )
    throws QueryException {
    // Calculate set of ancestors of mathed nodes; these also needs to be
    // in the expansion set
    try {
      final Set ancestors = new HashSet();
      for (Iterator i = fullyExpanded.iterator(); i.hasNext(); ) {
        Key id = (Key) i.next();
        for (Key pid = t.getParent(id); pid != null &&
                          !fullyExpanded.contains(pid) &&
                          !ancestors.contains(pid); pid = t.getParent(pid)) {
          ancestors.add(pid);
        }
      }
      Log.log("Ancestors set is "+ancestors,Log.INFO);
      Set allowedRefs = new NonListableSet() {
        public boolean contains(Object o) {
          return !ancestors.contains(o) && !fullyExpanded.contains(o);
        }
      };
      Set allowedContentRefs = new NonListableSet() {
        public boolean contains(Object o) {
          return ancestors.contains(o) || !fullyExpanded.contains(o);
        }
      };
      RefTree rt = RefTrees.getRefTree(t);
      return RefTrees.expandRefs(rt, allowedRefs, allowedContentRefs, t);
    } catch (NodeNotFoundException ex) {
      Log.log("Id disappeared: "+ex.getId(),Log.FATALERROR,ex);
    }
    return null;
  }
}

// arch-tag: b5e1d3a2-b4b7-4756-a83d-c0a72374749
