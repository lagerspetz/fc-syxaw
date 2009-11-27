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

// $Id: StorageProvider.java,v 1.6 2005/01/27 15:17:18 ctl Exp $
package fc.syxaw.fs;

/** Interface for Syxaw storage providers. Implementations of storage for
* SyxawFiles need to implements this interface. All instances of
* SyxawFiles inside syxaw are obtained through an object implementing
* this interface, allowing developers to easily replace the underlying file
* system with another, a databse, or something else.
*
*/

public interface StorageProvider {

  
  public UID getNextUid();
  
  /** Get file by UID.
   *
   * @param g UID of file
   * @return file
   */
  public SyxawFile getFile(UID g);

  //public SyxawFile getFile(UID g, String query);

  /** Get file by hierarchical name.
   *
   * @param parent parent file
   * @param child name of child file
   * @return file
   */
  public fc.syxaw.hierfs.SyxawFile getFile(SyxawFile parent, String child);

  /** Get file by hierarchical name.
   *
   * @param pathName pathname of file
   * @return SyxawFile
   */
  public fc.syxaw.hierfs.SyxawFile getFile(String pathName);

  /** Get toolkit for the storage provider.
   *
   * @return toolkit
   */
  public fc.syxaw.tool.Toolkit getToolkit();
  
  public fc.syxaw.fs.FullMetadataImpl createMetadata();
}
// arch-tag: 30627e154bf778751baabd06d6674134 *-
