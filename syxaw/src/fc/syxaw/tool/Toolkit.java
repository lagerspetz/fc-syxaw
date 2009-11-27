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

// $Id: Toolkit.java,v 1.2 2005/04/28 14:00:53 ctl Exp $
// This file was previosuly Id: FsTools.java,v 1.5 2004/11/18 08:58:50 ctl Exp
package fc.syxaw.tool;

import fc.syxaw.fs.ObjectProvider;

/** Abstract class for file system tools. The class contains methods for
 * performing file system maintenance, such as initializing and checking
 * consistency. */

public abstract class Toolkit {

  /** Initialize file system. Initializes a Syxaw file system. Should
   * work even if the file system is broken.
   * @param verbose if <code>true</code>, the method should be quite
   * verbose, i.e. log more messages than usual to the system log.
   */
  public abstract void init( boolean verbose );


  /** Check and repair file system. Checks a Syxaw file system for
   * inconsistencies, and repairs the inconsistencies automatically if possible.
   * @param verbose if <code>true</code>, the method should be quite
   * verbose, i.e. log more messages than usual to the system log.
   */
  public abstract int check( boolean repair, boolean verbose); // Returns no of fixes

  /** Deinitialize a Syxaw file system. Clean away all Syxaw-related information
   * from the file system.
   * @param verbose if <code>true</code>, the method should be quite
   * verbose, i.e. log more messages than usual to the system log.
   */
  public abstract void clean( boolean verbose );

  /** Checkpoint file system. It should be possible to restore the state
   * of the file system to the checkpoint state to some degree. Implementations
   * vary in different storage providers.
   */

  public abstract void checkpoint();

  /** Get merger factory class name.
   *
   * @param mimeType MIME Type to get merger for
   * @param linkFacet <code>true</code> to get link facet merger
   * @return name of a the merger factory class, that implements
   * {@link fc.syxaw.fs.ObjectMerger.MergerFactory}
   */

  public abstract String getMerger( String mimeType, boolean linkFacet );

  /** Set merger for a MIME type. Installs a merging algorithm for the given
   * MIME type. The <code>mergerfactory</code> parameter is the fully qualified class
   * name of an {@link fc.syxaw.fs.ObjectMerger.MergerFactory}
   * @param mimeType MIME Type to install merger for
   * @param mergerfactory Fully qualified class name of the merger.
   */
  public abstract void setMerger( String mimeType, String mergerfactory );
  
  public abstract void installObjectProvider(String mimeType, ObjectProvider op);
  

}
// arch-tag: a42020fb9c0949bcbf9046fc76e5de7e *-
