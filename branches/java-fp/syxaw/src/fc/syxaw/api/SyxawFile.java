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

// $Id: SyxawFile.java,v 1.18 2005/04/19 11:59:48 ctl Exp $

package fc.syxaw.api;

import java.io.File;
import java.io.IOException;

import fc.syxaw.fs.Syxaw;

/** Implementation of <code>ISyxawFile</code>. The FP version does
 * not work across VMs.
 */

public class SyxawFile implements ISyxawFile {

  protected File f;
  protected fc.syxaw.hierfs.SyxawFile stub;

  /** Creates a new SyxawFile for a pathname. The pathname <code>fname</code>
   * denotes the same file as <code>new java.io.File(fname)</code>. The file
   * named by the pathname must reside on a Syxaw file system.
   * @param fname Pathname of file
   * @throws IOException the file is not on a Syxaw file system
   */

  public SyxawFile(String fname) throws IOException {
    f = new File(fname);
    stub = Syxaw.getFile(fname);
  }

  /** Creates a new SyxawFile object for a file. The file
   * named by <code>file</code> must reside on a Syxaw file system.
   * @param file File to create a SyxawFile object for
   * @throws IOException the file is not on a Syxaw file system
   */

  public SyxawFile(File file) throws IOException {
    f = file;
    stub = Syxaw.getFile(file.toString());
  }

  /** Get a File object for this SyxawFile.
   * @return a <code>java.io.File</code> object for this SyxawFile object
   */

  public File getFile() {
    return f;
  }

  /* {@inheritDoc} */
  public String getId() throws IOException {
    return stub.getId();
  }

  /* {@inheritDoc} */
  public void createLink(String target) throws IOException {
    stub.createLink(target);
  }

  /* {@inheritDoc} */
  public void sync() throws SynchronizationException, IOException {
    sync(true, true);
  }
  
  /* {@inheritDoc} */
  public void sync(boolean getData, boolean getMetadata) throws IOException {
    stub.sync(getData, getMetadata);
  }

  /* {@inheritDoc} */
  public ISyxawFile getConflictingFile() throws IOException {
    return stub.getConflictingFile();
  }

  /* {@inheritDoc} */
  public ISyxawFile getConflictLog() throws IOException  {
    return stub.getConflictLog();
  }

  /* {@inheritDoc} */
  public boolean hasConflicts() throws IOException {
    return stub.hasConflicts();
  }

  /* {@inheritDoc} */
  public void conflictsResolved() throws SynchronizationException, IOException {
    stub.conflictsResolved();
  }

  /* {@inheritDoc} */
  public void setMetadata(Metadata meta) throws IOException {
    stub.setMetadata(meta);
  }

  /* {@inheritDoc} */
  public Metadata getMetadata() throws IOException {
    return stub.getMetadata();
  }


  /* {@inheritDoc} */
  public void mount(String target) throws IOException {
    stub.mount(target);
  }

  /* {@inheritDoc} */
  public void umount() throws IOException {
    stub.umount();
  }
}
// arch-tag: 0e81b67c11124e1124ce76b980c773c1 *-
