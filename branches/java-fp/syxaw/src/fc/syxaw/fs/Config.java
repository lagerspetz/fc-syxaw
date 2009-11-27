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

// $Id: Config.java,v 1.21 2005/06/07 13:07:02 ctl Exp $
package fc.syxaw.fs;

import java.net.InetAddress;
import java.net.UnknownHostException;

import fc.util.log.Log;

/** File system general configuration parameters.
 */

public class Config {


  /** Class name of XML parser used by Syxaw. The value is read from
   * the system property <code>syxaw.xmlparser</code>. Default value is
   * <code>org.apache.xerces.parsers.SAXParser</code>.
   * <b>This setting is being phased out.</b>
   */

  public static final String SYXAW_XMLPARSER =
      System.getProperty("syxaw.xmlparser",
                                       "org.apache.xerces.parsers.SAXParser");

  /** Initializes file system on startup if set to <code>true</code>.
   * File system initialization means that the directory subtree rooted at
   * {@link fc.syxaw.storage.hfsbase.ServerConfig#ROOT_FOLDER} is initialized as
   * a Syxaw file system. In practice, this means that some special folders
   * are added to this directory hierarchy.
   * The value is read from the system property <code>syxaw.initfs</code>.
   * Default value is
   * <code>false</code>
   * @see fc.syxaw.tool.Toolkit#init */

  public static final boolean STARTUP_INITFS =
      Boolean.valueOf(System.getProperty("syxaw.initfs","false")).booleanValue();

  /** Check the file system on startup if set to <code>true</code>.
   * The file system check verifies the integrity of Syxaw-specific metadata
   * (the GUID database, the version store etc.)
   * The value is read from the system property
   * <code>syxaw.checkfs</code>.
   * Default value is
   * <code>false</code>
   *
   * @see fc.syxaw.tool.Toolkit#check */

  public static final boolean STARTUP_CHECKFS =
    Boolean.valueOf(System.getProperty("syxaw.checkfs","false")).booleanValue();

  /** Clean away all Syxaw metadata from the file system. This operation
   * removes the persistent Syxaw "extensions" to an underlying file system,
   * if applicable. If this flag is set, but {@link #STARTUP_INITFS} is not,
   * Syxaw exits immediately after cleaning.
   * The value is read from the system property <code>syxaw.cleanfs</code>.
   * Default value is
   * <code>false</code>
   * @see fc.syxaw.tool.Toolkit#clean */

  public static final boolean STARTUP_CLEANFS =
    Boolean.valueOf(System.getProperty("syxaw.cleanfs", "false")).
    booleanValue();

  /** Start the synchronization daemon on startup if set to <code>true</code>.
   * The synchronization daemon anwers sycnhronization requests from other
   * Syxaw clients, and must thus be enabled if the file system is used in a server
   * role.
   * The value is read from the system property
   * <code>syxaw.syncd</code>.
   * Default value is
   * <code>true</code>
   * @see fc.syxaw.fs.SynchronizationServer */

  public static final boolean STARTUP_SYNCD =
    Boolean.valueOf(System.getProperty("syxaw.syncd","true")).booleanValue();

  /** Start the Syxaw command line interperater (CLI) server if set to <code>true</code>.
   * When the CLI server is started, the Syxaw CLI can be accessed by opening socket
   * connections to port {@link fc.syxaw.tool.SocketCLIServer#PORT}.
   *  The value is read from the system property
   * <code>syxaw.cliserver</code>.
   * Default value is
   * <code>true</code>
   * @see fc.syxaw.tool.SocketCLIServer */

  public static final boolean STARTUP_CLISERVER =
      Boolean.valueOf(System.getProperty("syxaw.cliserver","false")).booleanValue();

  /* Explicit set enables CLI automatically */
  public static final String CLISERVER_TYPE =
          System.getProperty("syxaw.cli",null);

  /** Start the NFS daemon on startup if set to <code>true</code>.
   * When started, the NFS daemon allows a Syxaw file system to be accessed as
   * an NFS mount.
   * The value is read from the system property
   * <code>syxaw.nfsd</code>.
   * Default value is
   * <code>false</code>
   * @see fc.dessy.jnfsd.mainline */

  public static final boolean STARTUP_NFSD =
      Boolean.valueOf(System.getProperty("syxaw.nfsd","false")).booleanValue();

  /** Start the Syxaw API RMI daemon.
   * The RMI daemon allows the Syxaw API (the <code>fc.syxaw.api</code> package)
   * to be used across Java VMs, i.e. the file system may be run in a
   * Java VM different from that of the application.
   * The value is read from the system property
   * <code>syxaw.rmiapi</code>.
   * Default value is
   * <code>true</code>.
   */

  public static final boolean STARTUP_RMIAPI =
      Boolean.valueOf(System.getProperty("syxaw.rmiapi","false")).booleanValue();

  /** Use experimental file system change daemon. NOT USED. 
   */
  public static final boolean USE_CHANGED =
      Boolean.valueOf(System.getProperty("syxaw.experimental.changed","false")).
      booleanValue();

  /** Enable XML delta encoding if set to <code>true</code>.
   * Changes to XML documents (notably the Syxaw directory tree) will be
   * transmitted as deltas if this option is enabled.
   * The value is read from the system property
   * <code>syxaw.xmldelta</code>.
   * Default value is
   * <code>true</code>*/

  public static final boolean XML_DELTA =
      Boolean.valueOf(System.getProperty("syxaw.xmldelta","true")).booleanValue();

  /** Enable synchronization of dependent objects if set to <code>true</code>.
   * When synchronizing a directory tree, the contents of any files that
   * have been changed in that tree
   * are automatically synchronized if this option is set to <code>true</code>.
   * This feature is usually only disabled for debug purposes.
   * The value is read from the system property
   * <code>syxaw.syncdependent</code>.
   * Default value is
   * <code>true</code> */

  public static boolean SYNC_DEPENDENT =
      Boolean.valueOf(System.getProperty("syxaw.syncdependent","true")).booleanValue();

  /** Class name of storage provider. The storage provider is an
   * object that provides an implementation of
   * {@link fc.syxaw.hierfs.SyxawFile} as well as
   * the file system toolkit.
   * The value is read from the system property
   * <code>syxaw.storageprovider</code>.
   * Default value is
   * <code>fc.syxaw.storage.xfs.XFSStorageProvider</code>, which
   * is a storage provider for XFS file systems.
   *
   * @see StorageProvider
   */

  public static final String STORAGE_PROVIDER =
      System.getProperty("syxaw.storageprovider",
      //     "fc.dessy.storage.NFSStorageProvider"); 
    "fc.syxaw.storage.hierfs.HfsStorageProvider"); // FIXME: change storageprovider

  /** Default expiration time for soft-locks. The time is in
   * milliseconds.
   * The value is read from the system property
   * <code>syxaw.lock.expirer</code>.
   * Default value is
   * <code>10000</code> (10s)
   */

  public static final long LOCK_EXPIRATION_TIME =
      Long.parseLong(System.getProperty("syxaw.lock.expire","10000"));

  /** Default attempt time for file locks. The time is in
   * milliseconds, and indicates for how long the synchronization daemon
   * will try to get a lock for a file before failing.
   * The value is read from the system property
   * <code>syxaw.lock.attempt</code>.
   * Default value is
   * <code>10000</code> (10s)
   */

  public static final long LOCK_ATTEMPT_TIME =
      Long.parseLong(System.getProperty("syxaw.lock.attempt","10000"));

  /** Modification status of local directory tree on synchronization.
   * Syxaw currently does not maintain modification flags for directories. If
   * enabled, this option tells Syxaw to always consider the local directory hierarchy
   * to be modified when synchronizing. If disabled, the local directory hierarchy
   * is never considered to be changed. Normally the option is set to <code>true</code>;
   * setting it to <code>false</code> will result in loss of local modifications
   * to the directory tree upon synchronization. This option is disabled mainly
   * for testing purposes.
   *
   * <p>The value is read from the system property
   * <code>syxaw.debug.dirmod</code>.
   * Default value is
   * <code>true</code> */

  public static final boolean DEBUG_DIRMODFLAG =
    Boolean.valueOf(System.getProperty("syxaw.debug.dirmod","false")).booleanValue();

  /** Debug option for dumping XML delta documents to the system log.
   * If enabled, this option dumps and XML delta documents received to the
   * system log.
   * The value is read from the system property
   * <code>syxaw.debug.dumpdelta</code>.
   * Default value is
   * <code>false</code> */

  public static final boolean DEBUG_DUMPDELTA =
   Boolean.valueOf(System.getProperty("syxaw.debug.dumpdelta","false")).booleanValue();

  /** Debug option to enable verification of XML delta documents.
   * This option enables verification of the XML delta documents transmitted,
   * enable if you suspect that the <code>3dm --diff</code> algorithm is buggy.
   * The value is read from the system property
   * <code>syxaw.debug.verifydelta</code>.
   * Default value is
   * <code>false</code> */

  public static final boolean DEBUG_VERIFYDELTA =
   Boolean.valueOf(System.getProperty("syxaw.debug.verifydelta","false")).booleanValue();

  public static final boolean DEBUG_CHECKPOINTS =
    Boolean.valueOf(System.getProperty("syxaw.debug.checkpoints", "false")).
    booleanValue();

  // Prohibit use of branch code
  public static final boolean PROTO_BRANCH_FENCE =
    Boolean.valueOf(System.getProperty("syxaw.proto.branchfence", "false")).
    booleanValue();

  /** The location id of this device.
   * The value is read from the system property
   * <code>syxaw.deviceid</code>.
   * Default value is the local hostname, as obtained by
   * <code>InetAddress.getLocalHost().getHostName()</code>.
   */

  public static final String THIS_LOCATION_ID;

  static {
    if( USE_CHANGED && DEBUG_DIRMODFLAG ) {
      Log.log("Suspicious config: USE_CHANGED and DEBUG_DIRMODFLAG are both set",Log.WARNING);
    }
    String locationId = "127.0.0.1";
    try {
      locationId = System.getProperty("syxaw.deviceid",
                                      InetAddress.getLocalHost().getHostName());
    } catch (UnknownHostException ex) {
      Log.log("Cannot resolve local hostname", Log.FATALERROR);
    }
    THIS_LOCATION_ID = locationId;
    if( XML_DELTA && !PROTO_BRANCH_FENCE ) {
      Log.log("Using XML delta and no branch fence. This may not work!", 
          Log.ERROR);
    }
  }
}
// arch-tag: 1cf772eadb629978ed9db4081cb67e84 *-
