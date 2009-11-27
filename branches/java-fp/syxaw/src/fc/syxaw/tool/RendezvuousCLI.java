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

package fc.syxaw.tool;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import fc.syxaw.fs.Config;
import fc.util.log.Log;
import fc.util.StringUtil;
import fc.util.Util;

public class RendezvuousCLI {

  private static final String RDZ_HOST;
  private static final int RDZ_PORT;


  static {
    String RDZ_POINT =
            System.getProperty("syxaw.cli.rendezvous","alchemy:23902");
    if( Util.isEmpty(RDZ_POINT) )
      Log.log("Rendezvous CLI needs a rendezvous address. "+
              "(set the syxaw.cli.rendezvous property)",Log.FATALERROR);
    String[] namePort = StringUtil.split(RDZ_POINT,':');
    int port = -1;
    String host = "";
    try {
      if( namePort.length != 2 )
        throw new Exception();
      port = Integer.parseInt(namePort[1]);
      host = namePort[0];
    } catch( Exception x) {
      Log.log("Invalid rendevouz address " + RDZ_POINT, Log.FATALERROR);
    }
    RDZ_PORT = port;
    RDZ_HOST = host;
  }

  private String host;
  private int port;


  protected static RendezvuousCLI instance = new RendezvuousCLI();
  protected SyxawCommandProcessor cp = SyxawCommandProcessor.getInstance();

  /** Get global instance. */
  public static RendezvuousCLI getInstance() {
    return instance;
  }

  protected RendezvuousCLI() {
  }


  /** Start server in separate thread. Starts the server in a separate thread
   * and returns.
   */
  public void start() {
    start( RDZ_HOST, RDZ_PORT );
  }

  /** Start server in separate thread. Starts the server in a separate thread
   * and returns.
   */
  public void start(String host, int port) {
    this.host = host;
    this.port = port;
    (new ServerThread()).start();
  }

  protected class ServerThread extends Thread {
    public void run() {
      int tries = 0;
      while (true) {
        Socket s = null;
        try {
          if (tries < 10)
            Log.log("Connecting to rendezvous point "+host+":"+port,Log.INFO);
          s = new Socket(host, port);
          if ( /*!ONESHOT*/true) {
            s.getOutputStream().write(
                ("Syxaw Rendezvous CLI at " + Config.THIS_LOCATION_ID +
                " attached to "+host+":"+port+
                "\n\n").getBytes());
            s.getOutputStream().flush();
            s.getInputStream();
          }
        } catch (IOException ex) {
          tries++;
          if (tries < 10)
            Log.log("Connect failed, retrying... (this message max 10 times)",
                  Log.INFO);
          try {
            Thread.sleep(2000);
          } catch (InterruptedException ex2) {}
          continue; 
        }        
        Log.log("Connected " + port , Log.INFO);
        try {
          /* For some reason broken on Nokia 9500
           BufferedReader r = new BufferedReader(
                  new InputStreamReader(s.getInputStream()));*/
          String cmdi = null;
          OutputStream sout = s.getOutputStream();
          InputStream sin = s.getInputStream();
          while ((cmdi = readLine(sin)) != null) {
            String cmd = cmdi;
            //Log.log("Executing "+cmd,Log.INFO);
            if( SocketCLIServer.ESCAPES)
              sout.write(SocketCLIServer.ESC_START);
            cp.exec(cmd, sout);
            if( SocketCLIServer.ESCAPES)
              sout.write(SocketCLIServer.ESC_END);
            sout.flush();
            /*if (ONESHOT)
              break;*/
          }
          s.close();
        } catch (IOException x) {
          Log.log("Error during socket comms", Log.WARNING, x);
        }
      }
    }

    private StringBuffer lineBuf = new StringBuffer();
    protected String readLine(InputStream in) throws IOException {
      lineBuf.setLength(0);
      for(int ch=-1;(ch=in.read())!='\n'; ) {
        if( ch == -1 )
          throw new EOFException();
        if( ch >= ' ' )
          lineBuf.append((char) ch);
      }
      return lineBuf.toString();
    }
  }

  public static void main(String[] args) {
    getInstance().start();
  }

}

// arch-tag: 0726e4e5-f241-4a56-bd92-7384ec6f2ee4
