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

// $Id: Config.java,v 1.9 2005/01/03 11:59:01 ctl Exp $
package fc.syxaw.transport;

import java.util.HashMap;
import java.util.Map;

import fc.syxaw.util.Util;
import fc.util.StringUtil;
import fc.util.log.Log;

/** Configuration parameters for network data transport. */

public class Config {

  /** Compress data when transmitting over the network. Set to <code>true</code> to
   * enable data compression using the gzip method.
   * The value is read from the system property
   * <code>syxaw.compressdata</code>. The default value is
   * <code>false</code>.
   */

  public static final boolean COMPRESS_DATA =
      Boolean.valueOf(System.getProperty("syxaw.compressdata","false")).booleanValue();

  /** Perform timing measurements for debug purposes.
   *  The value is read from the system property
   * <code>syxaw.debug.measuretimings</code>. The default value is
   * <code>false</code>. Note: as a side effect of the current implementation,
   * HTTP replies will be buffered to memory if this setting is enabled.
   * This will severely degrade troughput and increase memory usage for
   * large files.
   */
  public static final boolean MEASURE_TIMES =
      Boolean.valueOf(System.getProperty("syxaw.debug.measuretimings",
                                         "false")).booleanValue();

  /** Port to listen to for incoming HTTP requests.
   *  The value is read from the system property
   * <code>syxaw.http.listenport</code>. The default value is 4244.
   */

  public static final int LISTEN_PORT =
      Integer.parseInt(System.getProperty("syxaw.http.listenport","4244"));

  /** Port to make HTTP requests to.
   *  The value is read from the system property
   * <code>syxaw.http.requestport</code>. The default value is 4244.
   */

  public static final int REQUEST_PORT =
          Integer.parseInt(System.getProperty("syxaw.http.requestport","4244"));

  /** Setting to enable Xebu default encoding for XML documents.
   * If this setting is enabled Syxaw strives to use the Xebu binary
   * XML encoding for all XML documents, both internally and over the wire.
   * Note that Syxaw by default automatically detects binary XML over-the-wire,
   * so this flag need not be enabled to sync with an instance having
   * the setting enabled.
   *
   *  The value is read from the system property
   * <code>syxaw.xml.xebu</code>. The default value is <code>false</code>.
   */
  public static final boolean XEBU_XML =
      Boolean.valueOf(System.getProperty("syxaw.xml.xebu","false")).booleanValue();

  /** Path of servlet responding to Syxaw HTTP requests. Currently set to
   * {@value} */

  public static final String SERVLET_URL = "/syxaw";

  /** Default send buffer size. */
  public static final int SENDBUF_SIZE = 16384;

  /** Default receive buffer size. */
  public static final int RECIEVEBUF_SIZE = 16384;

  /** Maximum length of <code>GET</code> URLs.
   * If the Syxaw request URL exceeds this length,
   * <code>POST</code> will be used instead.
   */

  public static final int MAX_URL_LENGTH = 512;

  public static final Map LID_HOST_MAP;
  public static final Map LID_PORT_MAP;

  public static final int HTTP_RETRIES = 
    Integer.parseInt(System.getProperty("syxaw.http.retries", "1") );
  
  static {
    String lidMap = System.getProperty("syxaw.http.lidmap",null);
    Map lhm = null, lpm = null;
    if( lidMap != null ) {
      lhm = new HashMap();
      lpm = new HashMap();
      String[] entries = StringUtil.split(lidMap,';');
      for( int ie=0;ie<entries.length;ie++) {
        String[] parts = StringUtil.split(entries[ie],':');
        String did = parts[0];
        String host = parts.length > 1 && !Util.isEmpty(parts[1]) ?
                      parts[1] : "localhost";
        Integer port = null;
        try {
          port = parts.length > 2 && !Util.isEmpty(parts[2]) ?
                         Integer.valueOf(parts[2]) :
                         new Integer(REQUEST_PORT);
        } catch ( NumberFormatException ex ) {
          Log.log("Invalid port in libmap; entry is " + entries[ie],
                  Log.FATALERROR);
        }
        lhm.put(did,host);
        lpm.put(did,port);
      }
    }
    LID_HOST_MAP = lhm;
    LID_PORT_MAP = lpm;
  }
}
// arch-tag: 806fc3f4291840ca784bb50defa2e2f2 *-
