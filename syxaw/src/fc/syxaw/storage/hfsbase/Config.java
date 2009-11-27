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

// $Id: Config.java,v 1.25 2005/06/08 13:03:28 ctl Exp $
package fc.syxaw.storage.hfsbase;

import java.io.File;

public class Config {

  protected Config() { }  // Ensure singelton

  /** File name prefix used to name Syxaw special files.
   * The value is read from the system property
   * <code>syxaw.prefix</code>.
   * Default value is
   * <code>.syxaw</code> */

  public static final String SYXAW_PREFIX =
      System.getProperty("syxaw.prefix", ".syxaw");

  /** File name prefix of special "execute command" files in the root folder.
   * The Syxaw command line interface can be invoked trough the file system
   * by reading the file <code>/CMDFILE_PREFIX<i>cmd</i></code>, where
   * <i>cmd</i> is the CLI command to execute. The contents of the file is the
   * ouput of the command.
   * <p>For instance, to execute the <code>ping</code> command:<pre>
   * $ cat /.syxaw-execping
   * alchemy
   * $
   * </pre>
   * <p>The value is read from the system property
   * <code>syxaw.cmdprefix</code>.
   * Default value is
   * <code>.syxaw-exec</code> */

  public static final String CMDFILE_PREFIX =
      System.getProperty("syxaw.cmdprefix", ".syxaw-exec");

  /** Name of directory containing Syxaw metadata for the files in a directory.
   * On the underlying file system, Syxaw maintains metadata for each entry
   * in a directory in the <code>SYXAW_FOLDER</code> subdirectory, just as
       * CVS maintains metadata in a <code>CVS</code> subdirectory for each directory.
   * <p>The value is read from the system property
       * <code>syxaw.syxawfolder</code> and is always prefixed by {@link #SYXAW_PREFIX}
   * Default value is
   * {@link #SYXAW_PREFIX}<code>+"-dir"</code> */

  public static final String SYXAW_FOLDER = SYXAW_PREFIX +
      System.getProperty("syxaw.syxawfolder", "-dir");


  /** Name of directory containing unnamed files. Unnamed files have a
   * GUID, but no pathname in the Syxaw file system.
   * The directory is read from the system property
   * <code>syxaw.unnamed</code> and is relative to {@link ServerConfig#ROOT_FOLDER}
   * Default value is
   * {@link #SYXAW_PREFIX}<code>+"-unnamed"</code> */

  public static final String UNNAMED_FOLDER = SYXAW_PREFIX +
      System.getProperty("syxaw.unnamed", "-unnamed");

  /** Name of the Syxaw system directory. The system directory is invisible,
   * but can be accessed trough the Syxaw API and the loopback NFS file system.
   * Special filesystem data, such as conflict logs are found beneath the system directory.
   * The directory is read from the system property
   * <code>syxaw.systemfolder</code> and is relative to {@link ServerConfig#ROOT_FOLDER}
   * Default value is
   * {@link #SYXAW_PREFIX} */

  public static final String SYSTEM_FOLDER =
      SYXAW_PREFIX +System.getProperty("syxaw.systemfolder","");

  /** Name of the Syxaw conflict directory.
   * Conflicting data and conflict logs are stored  in this directory.
   * <code>syxaw.conflictfolder</code> and is relative to {@link #SYSTEM_FOLDER}
   * Default value is <code>conflicts</code> */

  public static final String CONFLICT_FOLDER =
      System.getProperty("syxaw.conflictfolder","conflicts");

  /** Flag to indicate if host file system is case insensitive. Enable this
   * option if the underlying file system is case insensitive (most notable
   * case: Microsoft file systems).
   * The value is read from the system property
   * <code>syxaw.caseinsensitivehostfs</code>.
   * Initialized by default to the value of the Java expression
   * <code>(new File("cAsE")).equals(new File("case"))</code>, which
   * seems to accurately detect case insensitivity on Windows. */

  // Set to true if host fs is case insensitive
  public static boolean CASE_INSENSITIVE_HOSTFS =
      Boolean.valueOf(System.getProperty(
      "syxaw.caseinsensitivehostfs",
      (new File("cAsE")).equals(new File("case")) ?
                      "true":"false")).booleanValue();

  /** The value to use for the metadata <code>type</code> field for directories.
   * The value is read from the system property
   * <code>syxaw.dirtype</code>.
   * Default value is
   * <code>xml/syxaw-dirtree</code>
   * @see fc.syxaw.api.Metadata */

  public static final String DIRECTORY_TYPE =
      System.getProperty("syxaw.dirtype", "xml/syxaw-dirtree");

  /** Name of directory containing the file system UID database.
   * The directory is read from the system property
   * <code>syxaw.indexdb</code> and is relative to {@link #SYSTEM_FOLDER}
   * Default value is <code>index</code> */

  public static final String INDEX_FOLDER =
      System.getProperty("syxaw.indexdb", "index");

  /** Name of directory containing the version database.
   * The directory is read from the system property
   * <code>syxaw.versiondb</code> and is relative to {@link #SYSTEM_FOLDER}
   * Default value is <code>versions</code> */

  public static final String VERSION_FOLDER =
      System.getProperty("syxaw.versiondb", "versions");


  /** Name of directory containing the directory tree database.
   * The directory is read from the system property
   * <code>syxaw.dirdb</code> and is relative to {@link #SYSTEM_FOLDER}
   * Default value is <code>dirdb</code> */

  public static final String DIRDB_FOLDER =
      System.getProperty("syxaw.dirdb", "dirdb");

  /** Set if the file system provides real directory modflags. Most
   * directory modflags may be manually set by scanning the dirtree before sync,
   * but in cases of directory uploads we need to explicitly set the
   * modflag if the file system doesn't provide true modflags. If this
   * setting is enabled, Syxaw won't manually maintain directory modflags in
   * the manner that is needed when using a dirtree modflag scanner+
   * sync. Default is <code>false</code>.
   */

  public static final boolean TRUE_DIR_MODFLAGS =
      Boolean.getBoolean("syxaw.debug.hierfs.truedirmodflags");

  public static final boolean NO_XMLR_DELTA =
    Boolean.getBoolean("syxaw.debug.hierfs.noxmldelta");

  /** Set if externally generated .syxaw-uid files are allowed (i.e.
   * you may unpack a tarball having .syxaw-uids inside). 
   */
  /* NOT implemented yet
  public static final boolean ALLOW_IMPORTED_DIRUIDS =
      Boolean.parseBoolean(System.getProperty("syxaw.hierfs.externaluids",
                                              "true"));
   */
}
// arch-tag: 2572dd52bd1a0c5c1c028b2709ff912a *-
