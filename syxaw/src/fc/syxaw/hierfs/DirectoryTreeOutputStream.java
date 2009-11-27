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

package fc.syxaw.hierfs;

import fc.raxs.DeltaStream;
import fc.util.io.DelayedOutputStream;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.RefTree;

/** Stream that applies XML directory trees. The stream
 * accepts directory trees as XML and knows how to apply the tree to the
 * local file system. The stream may encode either the link or
 * local facet dirtrees.
 */

public abstract class DirectoryTreeOutputStream extends DelayedOutputStream 
    implements DeltaStream {
  
  /** Get stream contents as reftree. The references in the written stream
   * are resolved against the given base tree.
   *
   * @param basetree reftree that is referenced by the streamed tree.
   * @return stream reftree.
   */
  public abstract RefTree getTree(IdAddressableRefTree basetree);

}
// arch-tag: 04aaf1e1-ec37-456c-8649-e5846d33ab03