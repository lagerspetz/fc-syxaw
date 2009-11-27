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

// $Id: ISyxawFile.java,v 1.11 2004/11/19 09:31:08 ctl Exp $
package fc.syxaw.api;

import java.io.IOException;

/** Operations on files residing on a Syxaw file system. This interface provides
 * access to file operations that are special to files residing on Syxaw
 * file systems, such as e.g. synchronizing a file. <p>
 * All regular file operations, such as delete or rename, are still available
 * through the {@link java.io.File} class. The {@link fc.syxaw.api.SyxawFile}
 * class implements this interface, as well as provides a brige between
 * <code>java.io.File</code> and <code>fc.syxaw.api.ISyxawFile</code>.
 *
 * <p>Note: unlike <code>java.io.File</code>, error situations are reported as exceptions,
 * not by returning special values.
 *
 * @see fc.syxaw.api.SyxawFile
 */

// Implementation notes:
//
// NOTE1: I will NOT copy methods from java.io.File here to keep the interface uncluttered
//
// NOTE2: Currently RMI stuff complicates adding methods to this interface
// (RemoteExceptions, extends Remote)
// This can be eliminated with a tool such as RMIAutogenerate, also CentiJ?,
// java.lang.reflect.Proxy stuff. Will do this if there is time...

// NOTE3: An issue I pondered fore some length was if failures should be reported
// as false/null value (like java.io.File functions ) or through exceptions.
// I think most of these methods are called in such a manner that you expect them
// to succeed => throwing exceptions was chosen. At least this makes the usage of
// these methods cleaner inside the Syxaw code itself

// NOTE4: On adding methods
//        - Add to ISyxawFile
//        - RMI: Add to RemoteSyxawFile interface (with RMI exception )
//        - RMI: Add proxy call to SyxawFile
//        - RMI: Add proxy call to SyxawFileImpl
//        - Implement (syxaw.storage.hierfs.SyxawFile)

public interface ISyxawFile {

  public final static char querySeparatorChar = '?';
  public final static String querySeparator =
          String.valueOf(querySeparatorChar);

  /** The Syxaw name-separator character /,
   *  represented as a string for convenience */
  public final static String separator = "/";

  /** The Syxaw name-separator character / */
  public final static char separatorChar = '/';

  /** Set file metadata.
   * @throws IOException metadata cannot be written */
  public void setMetadata(Metadata meta) throws IOException;

  /** Get file metadata.
  * @throws IOException metadata cannot be read */

  public Metadata getMetadata() throws IOException;

  /** Get file unique ID. All files on the Syxaw file system have a
   * unique ID of maximally 128 bits. The IDs are expressed using a
   * modified Base64 encoding of maximally 22 characters (shorter
   * forms implies that the most significant bits are set to zero).
   * The modified Base64 is described in the documentation for
   * {@link fc.syxaw.fs.UID}.
   *
   * @throws IOException unique id name cannot be obtained
   * @return modified Base-64 encoded ID
   */
  public String getId() throws IOException;

  /** Create a new file, which is linked to another file.
   * @param target unique name of file that this file is linked to, e.g.
   * <code>syxaw.hiit.fi/</code>.
   * @throws IOException cannot create linked file
   */

  public void createLink( String target ) throws IOException;

  /** Synchronize this file. If this file (or directory) is linked to
   * another file/directory, the file is synchronized with that file/directory.
   * @throws IOException an I/O error (including network transfer errors) occurred.
   * @throws SynchronizationException the file could not be synchronized, e.g.
   * due to conflicting updates.
   * */
  public void sync() throws SynchronizationException, IOException;
  
  public void sync(boolean getData, boolean getMetadata) throws SynchronizationException, IOException;

  /** Retrieves the conflicting data. Conflicting data is available
   * only if the last call to {@link #sync} for this file resulted in a conflict.
   * The conflicting data is the latest version
   * obtained of the file this file is linked to.
   *
   * <p>Used in conjunction with {@link #getConflictLog} and
   * {@link #conflictsResolved} to resolve conflicts after synchronization.
   *
   * @return file containing conflicting updated data.
   * @throws IOException cannot get conflicting file
   */

  public ISyxawFile getConflictingFile() throws IOException;

  /** Retrieves the conflict log for this file. A conflict log is available
   * only if the last call to {@link #sync} for this file resulted in a conflict.
   * The format of the conflict log depends on the merging algorithm used to
   * reconcile updates to this file.
   * @return file containing conflict log
   * @throws IOException cannot get conflict log
   */
  public ISyxawFile getConflictLog() throws IOException;

  /** Check if the file has conflicts.
   * @return <code>true</code> if the last call to {@link #sync}  caused
   * a conflict
   * @throws IOException unable to get conflict status
   */

  public boolean hasConflicts() throws IOException;

  /** Mark update conflicts resolved. The resolved data is the current
   * contents of this file. Can only be called if the file has conflicts.
   * @throws SynchronizationException the file has no conflicts
   * @throws IOException cannot mark conflict as resolved
   */

  public void conflictsResolved() throws IOException, SynchronizationException;

  // FIXME-W: Remove Note 2 when nonroot mounts work
  /** Create a linked directory tree. The name of the linked directory created is given by
   * this file.
   * <p><b>Note</b>: the directory trees are not initially synchronized. Call
   * {@link #sync} to synchronize the trees.
   * <p><b>Note 2</b>: This version of Syxaw only supports mounting the
   * root directory of the file system.
   * @param target GUID (i.e. DID/UID) of directory tree being linked to.
   * @throws IOException cannot create mount
   */

  public void mount(String target) throws IOException;

  /** Remove a linked directory tree. Note: any local modifications to
   * the directory tree are lost, unless the tree is synchronized prior
   * to calling <code>umount</code>.
   * @throws IOException cannot perform umount
   */

  public void umount() throws IOException;

}
// arch-tag: 4e4c8b101fec4da95b293494b6436c4f *-
