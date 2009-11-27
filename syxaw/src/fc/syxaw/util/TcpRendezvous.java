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

package fc.syxaw.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import fc.util.StringUtil;
import fc.util.log.Log;

/** Simple TCP rendezvous point. This is actually a TCP hub nowadays;
 * each incoming byte sequence on a socket is forwarded to all other sockets.
 * Allows you to snoop on TCP traffic as well :)
 */


public class TcpRendezvous {

  public static final boolean DROP_MESSAGES = false;

  List readers = new LinkedList();
  List messages = new LinkedList();

  public static void start(int rdzPort, int serverPort) {
    TcpRendezvous rdz = new TcpRendezvous();
    rdz.addListener(rdzPort,1);
    rdz.addListener(serverPort);
  }

  protected TcpRendezvous() {
    (new SocketWriter()).start();
  }

  public void addListener(int port) {
    addListener(port,Long.MAX_VALUE,null);
  }

  public void addListener(int port, long count) {
    addListener(port,count,null);
  }

  public void addListener(int port, long count, String endDelim) {
    (new SocketListener(port,count,endDelim)).start();
  }

  private class SocketListener extends Thread {

    int port;
    long connectionsLeft;
    String endDelim;

    public SocketListener(int port,long maxConnections,String endDelim) {
      this.port = port;
      connectionsLeft = maxConnections;
      this.endDelim = endDelim;
    }

    public void run() {
      ServerSocket ss = null;
      try {
        ss = new ServerSocket(port, 1);
      } catch (IOException ex) {
        Log.log("Cant listen on port "+port,Log.ERROR,ex);
        return;
      }
      Log.log("Listening on port "+port,Log.INFO);
      try {
        int cc =0;
        while (connectionsLeft-- > 0) {
          Socket s = ss.accept();
          cc++;
          SocketReader sr = new SocketReader(s,""+port+":"+cc,endDelim);
          sr.start();
          Log.log("Accepted connection at port "+port+(
                  connectionsLeft > 100 ? "" : ", "+connectionsLeft+
                  " connections left")+(endDelim != null ?
                  ", closing on "+endDelim : ""),Log.INFO);
        }
        ss.close();
        Log.log("No longer listening at port "+port,Log.INFO);
      } catch (IOException ex) {
        Log.log("Failed to accept connection on "+port,Log.ERROR,ex);
      }
    }
  }

  private class SocketReader extends Thread {

    Socket s;
    OutputStream os;
    boolean monitorClose = false;
    String name;

    public SocketReader(Socket s, String name, String endDelim) throws
            IOException {
      this.s = s;
      this.name = name;
      os = s.getOutputStream();
      if( endDelim != null )
        os = new MonitoredStream(os,endDelim);
    }

    public void run() {
      try {
        synchronized(readers) {
          readers.add(this);
        }
        InputStream in = null;
        try {
          in = s.getInputStream();
        } catch (IOException ex) {
          Log.log("Can't open socket in/output stream for "+name, Log.ERROR);
          return;
        }
        try {
          out: do {
            int sz = Math.max(1, in.available());
            byte[] buf = new byte[sz];
            int count = in.read(buf);
            if (count <= 0)
              break out;
            if (count < sz) {
              byte[] b2 = new byte[count];
              System.arraycopy(buf, 0, b2, 0, count);
              buf = b2;
              Log.log("Less bytes than promised (promise=" + sz + ", count=" +
                      count+" for "+name, Log.WARNING);
            }
            synchronized (messages) {
              //Log.log("Msg: "+new String(buf),Log.DEBUG);
              messages.add(new Msg(this,buf));
              messages.notifyAll();
            }
          } while (true);
          Log.log("Connection "+name+" done.",Log.INFO);
        } catch (SocketException ex) {
          if( os instanceof MonitoredStream && ((MonitoredStream) os).wasClosed)
            Log.log("Connection "+name+" closed by delimiter.",Log.INFO);
          else
            Log.log("Error reading socket on "+name, Log.ERROR);
        } catch (IOException ex) {
          Log.log("Error reading socket on "+name, Log.ERROR, ex);
        }
      } finally {
        synchronized(readers) {
          readers.remove(this);
        }
        try {
          s.close();
        } catch (IOException ex) {
          Log.log("Error closing socket on "+name, Log.ERROR);
        }
      }
    }

    public OutputStream getOutputStream() {
      return os;
    }
  }

  private class SocketWriter extends Thread {
    public void run() {
      int relayed=0; // No of dests that received the last taken msg
      while( true) {
        Msg buf;
        int msgLeft=0;
        synchronized (messages) {
            if( relayed > 0 )
              messages.remove(0);
            if( messages.size() == 0 || relayed == 0) {
              try {
                messages.wait(relayed > 0 ? 10000 : 500);
              } catch (InterruptedException ex) {
                // delib emoty
              }
            }
            relayed = 0;
            if( messages.size() == 0 )
              continue; // Nothing yet, restart
            buf = (Msg) messages.get(0);
            msgLeft = messages.size();
        }
        synchronized (readers) {
          for( Iterator i = readers.iterator();i.hasNext();) {
            SocketReader r = (SocketReader) i.next();
            if( r == buf.src )
              continue; // Dont echo
            //Log.log("Relay: "+new String(buf.buf)+" to "+r.name,Log.DEBUG);
            try {
              r.getOutputStream().write( buf.buf);
              relayed++;
              if( msgLeft == 0 )
                r.getOutputStream().flush();
            } catch ( IOException ex ) {
              Log.log("Can't send",Log.WARNING);
            }
          }
        }
        relayed+= DROP_MESSAGES ? 1 : 0; // If drop, fake 1 relay target
      }
    }
  }

  private static class Msg {
    SocketReader src;
    byte[] buf;

    public Msg(SocketReader src, byte[] buf) {
      this.src = src;
      this.buf = buf;
    }
  }

  public static void main(String[] args) {
    if( args.length == 0 ) {
      start(100, 101); // Demo code
    } else {
      TcpRendezvous rz = new TcpRendezvous();
      for( int i=0;i<args.length;i++) {
        String[] portCount = StringUtil.split(args[i],':');
        long count = Long.MAX_VALUE;
        if( portCount.length > 1 && !Util.isEmpty(portCount[1]))
          count = Long.parseLong(portCount[1]);
        rz.addListener(Integer.parseInt(portCount[0]),count,
                portCount.length > 2 ? portCount[2]: null);
      }
    }
  }

  private class MonitoredStream extends FilterOutputStream {

    Automaton skipAutomaton;
    Automaton endAutomaton;
    boolean wasClosed = false;

    public MonitoredStream( OutputStream os /*, String skip*/, String end ) {
      super(os);
      //skipAutomaton = new Automaton(skip.getBytes());
      endAutomaton = new Automaton(end.getBytes());
    }

    boolean inProgress = false;

    public void write(int achar) throws IOException {
      try {
        out.write(achar);
      } finally {
        checkEnd(new byte[] {(byte) achar}, 0, 1);
      }
    }

    public void write(byte[] buf, int off, int len) throws IOException  {
      try {
        out.write(buf,off,len);
      } finally {
        checkEnd(buf,off,len);
      }
    }

    private void checkEnd(byte[] buf, int off, int len) throws IOException {
      int state = endAutomaton.matches;
      endAutomaton.digest(buf,off,len);
      if( endAutomaton.matches != state ) {
        wasClosed = true;
        out.close();
      }
    }
  }

  private static class Automaton {
    byte[] pattern;
    int patpos = -1; // -1 = outside, 0..pattern.length-1 = has matched char patpos
    int matches = 0;

    public Automaton(byte[] pattern) {
      this.pattern = pattern;
    }

    public void digest(byte[] chars, int off, int len) {
      for (int i = off; i < off + len; i++)
        digest(chars[i]);
    }

    public void digest(byte ch) {
      patpos = ch == pattern[patpos + 1] ? patpos + 1 : -1;
      if (patpos == pattern.length - 1) {
        matches++;
        patpos = -1; // Await next
      }
    }
}

}

// arch-tag: 75a68bed-9ded-46a4-b488-08883e83aa7a
