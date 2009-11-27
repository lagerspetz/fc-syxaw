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
import java.util.LinkedList;
import java.util.Set;

import fc.syxaw.fs.SyxawFile;
import fc.syxaw.util.XmlUtil;
import fc.util.io.DelayedInputStream;
import fc.util.log.Log;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.StringKey;

public class XmlrId extends Xmlr {

  public InputStream getInputStream(SyxawFile f, String id,
                                    boolean linkFacet) throws
          FileNotFoundException {
    return new XmlrIdInputStream(linkFacet ?
                                 f.getLinkInputStream() : f.getInputStream(),
                                 StringKey.createKey( id ) );
  }

  private class XmlrIdInputStream extends DelayedInputStream {

    InputStream in;
    final Key id;

    public XmlrIdInputStream(InputStream in, Key id ) {
      super();
      this.in = in;
      this.id = id;
    }

    protected void stream(OutputStream out) throws IOException {
      RefTree tna = // Possibility to test in instance-of RefTreeStream
          XmlUtil.readRefTree(in,contentCodec);
      final IdAddressableRefTree t = RefTrees.getAddressableTree(tna);
      if( !t.contains(id) )
        throw new MalformedQueryException("No such id "+id);
      try {
        Set /*!5 <Key> */ fullyExpaned = new HashSet /*!5 <Key> */();
        LinkedList /*!5 <Key> */ eq = new LinkedList /*!5 <Key> */();
        eq.add(id);
        while (eq.size() > 0) {
          Key root = (Key) eq.removeFirst();
          fullyExpaned.add(root);
          for (Iterator i = t.childIterator(root); i.hasNext(); ) {
            eq.addLast((Key) i.next());
          }
        }
        RefTree qt = queryTree(t, fullyExpaned);
        XmlUtil.writeRefTree(qt, out, contentCodec );
      } catch (NodeNotFoundException ex) {
        Log.log("Node disappeared "+ex.getId(),Log.FATALERROR);
      }
    }

    // We don't want to send any delayed producer execeptions here anymore,
    // just close in
    public void close() throws IOException {
      in.close();
    }

  }
}

// arch-tag: da345254-248d-4d19-b7ec-f7960eec7ad5
