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

// $Id: SyxawFile.java,v 1.17 2005/02/01 09:09:31 ctl Exp $
package fc.syxaw.hierfs;

import java.io.IOException;
import fc.syxaw.api.ISyxawFile;
import fc.syxaw.fs.UID;

/** SyxawFile with hierarchical names. Extends the base class with support for
 * a directory hierarchy, made up of of an unordered tree of directory entries.
 * Each entry has a name, which is unique among its siblings, and optional
 * link and local facet ids, which are unique inside the tree.
 * <p>Each entry <i>e</i> in the tree is addressed by its pathname, which
 * consists of the names
 * of the entries on the path from the root to the entry <i>e</i>, interpunctuated
 * with {@link fc.syxaw.api.ISyxawFile#separator the Syxaw pathname sparator}. */

public abstract class SyxawFile extends fc.syxaw.fs.SyxawFile
    implements ISyxawFile{

  protected SyxawFile() {
    super();
  }

  /** Create file object. See corresponding constructor in
   * <code>java.io.File</code>. */
  public SyxawFile(SyxawFile parent, String child) {
  }

  /** Create a new file object. */
  public abstract SyxawFile newInstance(String name);

  /** Create a new file object.
   * @see #SyxawFile(SyxawFile,String) */
  public abstract SyxawFile newInstance(SyxawFile parent, String child);

  /** Create a file object for a child of this file. Works as
   * {@link #newInstance(SyxawFile,String)} with <code>parent=this</code>.
   */
  public abstract SyxawFile getChildFile( String child );


  /** See documentation for method in <code>java.io.File</code> */
  public abstract String getName();

  /** See documentation for method in <code>java.io.File</code> */
  public abstract SyxawFile getParentFile();

  /** See documentation for method in <code>java.io.File</code> */
  public abstract boolean isDirectory();

  /** See documentation for method in <code>java.io.File</code> */
  public abstract boolean isFile();

  /** See documentation for method in <code>java.io.File</code> */
  public abstract String[] list();

  /** See documentation for method in <code>java.io.File</code> */
  public abstract boolean mkdir();

  /** See documentation for method in <code>java.io.File</code> */
  public abstract boolean renameTo(SyxawFile dest);

  /** Creates a new file with a given UID. */
  public abstract boolean createNewFile(UID u) throws IOException;

}
// arch-tag: 4a0aea11e1f033b8793bc70ae39b3b15 *-
