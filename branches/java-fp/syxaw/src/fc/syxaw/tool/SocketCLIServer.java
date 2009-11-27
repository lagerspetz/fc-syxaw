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

// $Id: SocketCLIServer.java,v 1.5 2003/10/15 11:06:23 ctl Exp $
package fc.syxaw.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import fc.syxaw.fs.GUID;
import fc.util.log.Log;

/** Socker Syxaw command line interface. The class implements a command line
 * interperater that listens on a TCP port, for easy acess using e.g.
 * <code>telnet</code>. The commands are executed by an object of type
 * {@link SyxawCommandProcessor}.
 * <p>Below is sample use of the socket CLI:<pre>
ctl@alchemy:~/fuegocore/code/java$ telnet localhost 4243
Trying 127.0.0.1...
Connected to localhost.
Escape character is '^]'.
ls /
Content of /
name       guid             version link linkVersion flags readOnly
dia/       alchAAAVBgAAGA0A -1           -1          XX    false
fuegocore/ alchAABgcsYCjuBK -1           -1          XX    false
lodju/     alchAADyvQAAr4QB -1           -1          XX    false
afile.txt  alchAOCCcb3PVBxl -1           -1          XX    false
^]
telnet> close
Connection closed.
ctl@alchemy:~/fuegocore/code/java$
</pre>
*/

public class SocketCLIServer {

  /** Port to listen for incoming connections on. Configured by the
   * system property <code>syxaw.cliserver.port</code>. Default value is
   * 4243 */

  public static final int PORT =
      Integer.parseInt(System.getProperty("syxaw.cliserver.port","4243"));

  /** Close connection after one command. If set to <code>true</code>,
   * the server will close
   * the socket after the first command has been executed and its ouput has
   * been displayed. Useful foir scripting Syxaw CLI commands via
   * <code>netcat</code>.Configured by the
   * system property <code>syxaw.cliserver.oneshot</code>. Default value is
   * <code>false</code>. */


  public static final boolean ONESHOT =
      Boolean.getBoolean("syxaw.cliserver.oneshot");

  /** Enable command status escapes. If enabled, the {@link #ESC_START} sequence
   * is sent before command replies, and {@link #ESC_END} after. The status
   * escapes may be used to monitor the status of the command processor.
   * Configured by the  system property <code>syxaw.cliserver.escapes</code>.
   * Default value is <code>false</code>.
   */

  public static final boolean ESCAPES =
          Boolean.valueOf(System.getProperty("syxaw.cliserver.escapes",
                                        "true")).booleanValue();

  protected static SocketCLIServer instance = new SocketCLIServer();
  protected int port = -1;
  protected SyxawCommandProcessor cp = SyxawCommandProcessor.getInstance();

  /** Get global instance. */
  public static SocketCLIServer getInstance() {
    return instance;
  }

  protected SocketCLIServer() {
  }


  /** Start server in separate thread. Starts the server in a separate thread
   * and returns.
   */
  public void start() {
    start( PORT );
  }

  /** Start server in separate thread. Starts the server in a separate thread
   * and returns.
   * @param aPort port to start the server on.
   */
  public void start(int aPort) {
    port = aPort;
    (new ServerThread()).start();
  }

  protected class ServerThread extends Thread {
    public void run() {
      try {
        ServerSocket ss = new ServerSocket(port);
        Log.log("Socket CLI Server listening at port " + port + "; oneshot=" +
                ONESHOT, Log.INFO);
        while (true) {
          Socket s = ss.accept();
          Log.log("Connected " + port, Log.INFO);
          if (!ONESHOT) {
            s.getOutputStream().write(("Syxaw socket CLI $Revision: 1.5 $ " +
                                      "at " + GUID.getCurrentLocation()+"\n\n").
                                      getBytes());
            s.getOutputStream().flush();
          }
          try {
            BufferedReader r = new BufferedReader(
                new InputStreamReader(s.getInputStream()));
            String cmdi = null;
            while ( (cmdi = r.readLine()) != null) {
              final OutputStream sout = s.getOutputStream();
              final String cmd = cmdi;
/*              (new Thread() {
                public void run() {
                  try {*/
                    if (ESCAPES)
                      sout.write(ESC_START);
                    cp.exec(cmd, sout);
                    if (ESCAPES)
                      sout.write(ESC_END);
/*                  } catch (IOException x) {
                    Log.log("I/O on socket comm in cmd thread", Log.WARNING, x);
                  }
                }
              }).start();*/
              if (ONESHOT)
                break;
            }
            s.close();
          }
          catch (IOException x) {
            Log.log("Error during socket comms", Log.WARNING, x);
          }
        }
      }
      catch (IOException e) {
        Log.log("Can't create server socket", Log.WARNING, e);
      }
    }
  }

  /** Escape sequence to indicate start of command reply. */
  public static final byte[] ESC_START = "[-[- ".getBytes();
  /** Escape sequence to indicate end of command reply. */
  public static final byte[] ESC_END = "]]: ".getBytes();

}
// arch-tag: 849e8e8739abd3ab96f2c1e0fb5c97dc *-
