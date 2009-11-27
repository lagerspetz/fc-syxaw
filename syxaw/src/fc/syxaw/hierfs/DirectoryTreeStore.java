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

// $Id: DirectoryTreeStore.java,v 1.2 2004/12/01 16:00:01 ctl Exp $
// History:
// previously in Id: DirectoryTree.java,v 1.26 2004/09/16 14:57:16 ctl Exp
package fc.syxaw.hierfs;

import java.io.IOException;
import java.util.Set;

import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.MutableRefTree;
import fc.xml.xmlr.RefTree;

/** Interface for a versioned directory tree store. A directory tree store is
 * a versioned reftree whose nodes are {@link DirectoryEntry DirectoryEntries}. */
public interface DirectoryTreeStore extends MutableRefTree {

  /** Get tree by version.
   *
   * @param version version to retrieve,
   * {@link fc.syxaw.fs.Constants#CURRENT_VERSION} may be to get the
   * current tree.
   * @return the requested version of the tree
   * @throws IOException if an I/O error occurs.
   */
  public IdAddressableRefTree getTree(int version) throws IOException;

  /** Get reftree by version. Gets a reftree for version
   * <code>version</code> that refs nodes in version <code>refsVersion</code>.
   * Implementations should strive to return a tree that uses references
   * as much as possible, so that the returned tree is small.
   *
   * @param version version to retrieve,
   * {@link fc.syxaw.fs.Constants#CURRENT_VERSION} may be to get the
   * current tree.
   * @param refsVersion version to reference,
   * {@link fc.syxaw.fs.Constants#CURRENT_VERSION} may be used to
   * reference the current tree.
   * {@link fc.syxaw.fs.Constants#NO_VERSION} may be used if there
   * may be no references in the returned tree.
   * @return the requested version of the tree as a reftree referencing
   *  <code>refsVersion</code>.
   * @throws IOException if an I/O error occurs.
   */
  public RefTree getRefTree(int version,int refsVersion) throws IOException;

  /** Commit the current state of the store.
   *
   * @param version version number to assign the committed state
   * @throws IOException if an I/O error occurs.
   */

  //DISABLE IN PROTO public void commit(int version) throws IOException;

  /** Get dependent objects needing sync. The set of dependent objects is based
   * on the uncommitted edits performed on the store.
   *
   * @return Set set of {@link fc.syxaw.fs.UID} of objects needing
   * to be synchronized.
   */
  public Set getObjectsNeedingSync();

  // Enable/disable the link-to-local version number mapping
  // (useful for link facets only). If disabled, all version numbers
  // passed to the instance are interpreted as local facet versions, rather
  // than link facet versions. This is useful for making hacks that make
  // the new version model work with old version model code, in cases
  // when the version instance has a valid localVersion, since then
  // we may retrieve by localVersion rather than by a full (link) version

  public void FIXMEPsetLinkToLocalMapping(boolean state);

  /** Get tree to be used as base for merging when no base tree is available.
   * 
   * @return null base tree
   */
  public abstract IdAddressableRefTree getNullBaseTree();

}
// arch-tag: f2d596dfb86bb588d3d75043f28e7286 *-
