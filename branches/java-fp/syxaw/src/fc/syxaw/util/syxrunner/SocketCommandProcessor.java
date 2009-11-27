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

// Imported from Id: SocketCLIClient.java,v 1.4 2003/10/15 11:06:22 ctl Exp $

package fc.syxaw.util.syxrunner;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedList;

import fc.syxaw.tool.CommandProcessor;
import fc.syxaw.tool.SocketCLIServer;
import fc.util.log.Log;

/** Generic client for Syxaw command line interface over a TCP socket.
 * A command processor that sends the commands over a TCP connection to a command
 * processor on a remote host, such as a {@link SocketCLIServer}.
 * Implements an automatic reconnection policy, whereby a socket connect
 * is attempted every {@link #CONNECT_RETRY} milliseconds.
 */

public class SocketCommandProcessor extends CommandProcessor  {

  private PrintWriter diag;
  private boolean isConnected = false; // access by commandQueue lock
  private String host ;
  private int port;
  private boolean terminated=false;
  private Thread reader=null;
  private Thread writer=null;

  /** Delay in milliseconds between attempts to establish a socket connection.
   * Currently set to {@value}.
   */

  public static final int CONNECT_RETRY = 2000;

  private LinkedList commandQueue = new LinkedList();

  /** Create socket CLI client.
   *
   * @param aDiagnostics stream to send diagnostic messages related to the
   * socket connection. E.g. <code>Connect</code>,
   * <code>Connect retry in 5000ms</code> etc.
   * @param aOutput stream to send command output to. The output is copied
   * verbatim as received over the socket.
   * @param aHost host to connect to
   * @param aPort port to connect to
   */
  public SocketCommandProcessor(OutputStream aDiagnostics,
                         OutputStream aOutput, String aHost, int aPort) {
    diag = new PrintWriter( aDiagnostics );
    host = aHost;
    port = aPort;
    output = aOutput;
    (reader=new Thread(){
      public void run() {
        sendLoop();
      }
    }).start();
    (writer=new Thread(){
      public void run() {
        readLoop();
      }
    }).start();

  }

  public void kill() {
    if( terminated )
      throw new IllegalStateException();
    terminated = true;
    writer.interrupt();
    reader.interrupt();
    Log.log("CLI sent interrupt.",Log.INFO);
    try { writer.join(); } catch ( InterruptedException ex) {}
    try { reader.join(); } catch ( InterruptedException ex) {}
    Log.log("CLI killed.",Log.INFO);
  }

  public void exec( String cmd, OutputStream out ) {
    if( out != output )
      Log.log("Wrong output stream",Log.ASSERTFAILED);
    exec(cmd,false);
  }

  public void exec( String cmd, boolean waitForConnect ) {
    exec(cmd,waitForConnect,false);
  }

  public void exec(String cmd, boolean waitForConnect, boolean sync) {
    synchronized (connectLock) {
      if (isConnected) {
        synchronized (commandQueue) {
          commandQueue.addLast(new Command(cmd, output));
          commandQueue.notify();
        }
        if (sync) {
          synchronized (output) {
            try {
              output.wait();
            } catch (InterruptedException ex) {}
          }
          if (output instanceof SyncOutputStream &&
              ((SyncOutputStream) output).excepted()) {
            ((SyncOutputStream) output).resetExcepted();
            throw new RuntimeException("Command failed: " + cmd);
          }
        }
      } else if (waitForConnect) {
        try {
          Log.log("Waiting for connect...", Log.INFO);
          connectLock.wait();
        } catch (InterruptedException ex) {}
        exec(cmd, waitForConnect, sync);
      } else
        diag("Command ignored: not connected");
    }
  }

  public void breakCommandWait() {
    synchronized(output) {
      output.notify();
    }
  }


  private Socket readSocket=null;
  private Object connectLock = new Object();

  private void sendLoop() {
    boolean waitMsg = false;
    while( !terminated ) {
      try {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host,port),5000);
        diag("Connect to "+host);
        waitMsg = false;
        synchronized( commandQueue ) {
          isConnected = true;
          synchronized( connectLock ) {
            readSocket = s;
            connectLock.notify();
          }
        }
//        diag.flush();
        // Sendloop
        while ( !terminated && readSocket != null) {
          Command cmd = null;
          // Get command
          synchronized (commandQueue) {
            if (commandQueue.isEmpty()) {
              try {
                commandQueue.wait();
              }
              catch (InterruptedException x) {}
              continue;
            }
            else {
              try {
                cmd = (Command) commandQueue.removeFirst();
                // Exec it
                //Log.log("Send cmd="+cmd.cmd,Log.INFO);
                s.getOutputStream().write(cmd.cmd.getBytes());
                s.getOutputStream().write('\n'); // Cmd terminator
                s.getOutputStream().flush();
              }
              catch( java.net.SocketException x) {
                isConnected = false;
                readSocket = null;
                diag("Disconnected from "+host);
              } catch (java.io.IOException x) {
                Log.log("Comm excepted", Log.INFO, x);
                isConnected = false;
                readSocket = null;
              }
            }
          } // end synchronized
        }
      } catch (Exception x ) {
        if( !waitMsg ) {
          diag("Connect to " + host + " failed, will keep retrying each " +
               CONNECT_RETRY + " ms");
          waitMsg = true;
        }
//        diag.flush();
        try {Thread.sleep(CONNECT_RETRY);} catch (InterruptedException x2) {}
      }
    }
    if( readSocket != null )
      try { readSocket.close(); } catch (IOException ex) {};
  }

  private OutputStream output;
  private byte[] readBuf = new byte[4096];

  private void readLoop() {
    boolean doSleep = false;
    while(!terminated) {
      if( doSleep ) {
        try {
          output.flush();
          Thread.sleep(10);
        }
        catch (Exception x) {} // IOExcept or InterruptExecpt; ignore both
      }
      doSleep = false;
      synchronized( commandQueue ) {
        try {
          if (readSocket == null ||
              readSocket.getInputStream().available() <= 0) {
            doSleep = true;
          }
          else {
            int len = readSocket.getInputStream().read(readBuf);
            if (len > 0) {
              output.write( readBuf,0,len );//output.print(new String(readBuf, 0, len));
            }
          }
        } catch (java.io.IOException  x ) {
          Log.log("Reader IOExcepted",Log.INFO);
          doSleep = true;
        }
      }
    }
    if( readSocket != null )
      try { readSocket.close(); } catch (IOException ex) {};
  }

  private void diag(String msg ) {
    diag.println(msg);
    diag.flush();
  }

  public static class SyncOutputStream extends FilterOutputStream {

    public SyncOutputStream( OutputStream out ) {
      super(out);
    }

    int starts = 0;
    int ends = 0;
    Automaton sa = new Automaton(SocketCLIServer.ESC_START);
    Automaton ea = new Automaton(SocketCLIServer.ESC_END);
    Automaton exa = new Automaton(CommandProcessor.EXCEPT_SIGNAL);

    boolean inProgress =false;

    public void write( int achar ) throws IOException {
      setFlags( new byte[] {(byte) achar},0,1 );
      out.write(achar);
    }
    public void write( byte[] buf, int off, int len ) throws IOException  {
      setFlags( buf,off,len );
      out.write(buf,off,len);
    }

    public boolean excepted() {
      return exa.matches > 0;
    }

    public void resetExcepted() {
      exa.matches = 0;
    }

    private void setFlags( byte[] buf, int off, int len ) {
      int smatches = sa.matches;
      try {
        sa.digest(buf, off, len);
        ea.digest(buf, off, len);
        exa.digest(buf,off,len);
      } catch ( Throwable t ) {
        Log.log("Ex",Log.ERROR,t);
      }
      /*
      if( sa.matches >  smatches ) {
        exa.matches = 0;
      }*/
      if( ea.matches > ends ) {
        ends = ea.matches;
        synchronized(this) {
          notifyAll();
          //out.notifyAll();
        }
      }
    }
  }

  private static class Automaton {
    byte[] pattern;
    int patpos=-1; // -1 = outside, 0..pattern.length-1 = has matched char patpos
    int matches = 0;

    public Automaton(byte[] pattern) {
      this.pattern = pattern;
    }

    public void digest(byte[] chars, int off, int len) {
      for( int i=off;i<off+len;i++)
        digest(chars[i]);
    }

    public void digest(byte ch) {
      patpos = ch == pattern[patpos+1] ? patpos +1 : -1;
      if( patpos == pattern.length -1) {
        matches++;
        patpos = -1; // Await next
      }
    }
  }


  private static class Command {
    public OutputStream os;
    public String cmd;

    public Command(String aCmd,OutputStream aOs) {
      cmd = aCmd;
      os =aOs;
    }
  }
}

// arch-tag: 0e822ef3-3397-4d84-9fef-0793b2888f04
