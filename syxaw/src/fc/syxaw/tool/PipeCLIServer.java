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

// $Id: PipeCLIServer.java,v 1.1 2005/06/07 13:04:20 ctl Exp $

package fc.syxaw.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import fc.util.log.Log;

public class PipeCLIServer {

  protected SyxawCommandProcessor cp = SyxawCommandProcessor.getInstance();

/*  protected static PipeCLIServer instance = new PipeCLIServer();

  /** Get global instance. */
/*  public static PipeCLIServer getInstance() {
    return instance;
  }*/

  public PipeCLIServer() {
  }

  /** Start server in separate thread. Starts the server in a separate thread
   * and returns.
   */
  public void start() {
    (new ServerThread(System.in,System.out)).start();
  }

  /** Start server in separate thread. Starts the server in a separate thread
   * and returns.
   */
  public void start(InputStream in, OutputStream out) {
    (new ServerThread(in,out)).start();
  }


  protected class ServerThread extends Thread {

    OutputStream out;
    InputStream in;

    public ServerThread(InputStream in, OutputStream out) {
      this.in = in;
      this.out = out;
    }

    public void run() {
      Log.log("Pipe CLI server started", Log.INFO);
      BufferedReader r = new BufferedReader(
              new InputStreamReader(in));
      while (true) {
        try {
          String cmd = null;
          while ((cmd = r.readLine()) != null) {
            out.write((cmd+"\n").getBytes());
            cp.exec(cmd, out);
          }
        } catch (IOException x) {
          Log.log("Error during pipe comms", Log.WARNING, x);
        }
      }
    }
  }

}
// arch-tag: 0c560a150a7c2b0828fbedeb2f286410 *-
