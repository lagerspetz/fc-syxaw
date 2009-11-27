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

package fc.syxaw.proto;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import fc.syxaw.fs.GUID;
import fc.syxaw.fs.Syxaw;
import fc.syxaw.hierfs.SyxawFile;
import fc.syxaw.protocol.Proto;
import fc.syxaw.transport.Config;
import fc.util.log.Log;
import fc.util.Base64;

/** Mock-up RPC framework for multicasts. This is mock-up code that provides the
 * find command the ability to gather known branches from other devices.
 */
public class RPC {

  Map sockets = Collections.synchronizedMap( new HashMap() ); // Map of <string dest,streams>
  LinkedList mailbox = new LinkedList(); // Queue of messages

  public static final int PORT =
      Integer.parseInt(System.getProperty("syxaw.rpc.port","42042"));

  private static RPC inst = new RPC();

  public RPC() {
    (new Accepter()).start();
    (new Receiver()).start();
    /*if( fc.syxaw.fs.Config.THIS_LOCATION_ID.equals("buzz") )*/
    //(new RPCServer()).start();
  }

  public static RPC getInstance() {
    return inst;
  }

  private synchronized SocketStreams connect(String dest) {
    SocketStreams s = (SocketStreams) sockets.get(dest);
    if( s == null ) {
      Socket so = null;
      try {
        so = new Socket(getRealDest(dest), getRequestPort(dest));
      } catch (IOException ex) {
        Log.log("Can't connect to "+dest+", retry in 1000ms",Log.ERROR);
        try { Thread.sleep(1000); } catch (InterruptedException ex2) {}
        return connect(dest);
      }
      try {
        s = new SocketStreams(so);
        s.out.write((fc.syxaw.fs.Config.THIS_LOCATION_ID+"\n").getBytes());
      } catch (IOException x) {
        Log.log("Can't open output stream to " + dest, Log.ERROR);
        return connect(dest);
      }
      Log.log("Opened connection to "+dest,Log.INFO);
      sockets.put(dest,s);
    } else
      Log.log("Reusing connection for dest "+dest,Log.INFO);
    /* NOT ON FP, Maybe getInputStream() will throw an ex if disconnected?
      if( !s.s.isConnected() ) {
      sockets.remove(dest);
      return connect( dest );
    }*/
    return s;
  }
  protected String getRealDest( String dest ) {
    if( Config.LID_HOST_MAP == null )
      return  "localhost";
    else
      return (String) Config.LID_HOST_MAP.get(dest);
  }

  protected int getRequestPort( String dest ) {
    if( Config.LID_HOST_MAP != null ) {
      Integer pi = ((Integer) Config.LID_PORT_MAP.get(dest));
      if( pi == null ) {
        Log.log("Unknown destination " + dest, Log.ERROR);
        pi = new Integer(39000);
      }
      return pi.intValue()+100;
    } else
      return Config.REQUEST_PORT + 100;
  }

  public class Accepter extends Thread {
    int port = getRequestPort(fc.syxaw.fs.Config.THIS_LOCATION_ID);
    public void run() {
      try {
        ServerSocket ss = new ServerSocket(port);
        Log.log("RPC Server listening at port " + port, Log.INFO);
        while (true) {
          Socket s = ss.accept();
          SocketStreams st = new SocketStreams(s);
          String src = st.inr.readLine();
          Log.log("Accepted connection from "+src,Log.INFO);
          sockets.put(src,st);
        }
      }
      catch (IOException e) {
        Log.log("Can't create server socket", Log.WARNING, e);
      }

    }
  }

  /** Asynchronous RPC.
   * @param dest call destination
   * @param m Message of call
   * @return reply message
   */

  public Message call(String dest, Message m) {
    send(dest,m);
    return getReplay(m.id);
  }

  /** Send message.
   * @param dest Message destination
   * @param m Message
   */

  public void send(String dest, Message m) {
    try {
      m.write(connect(dest).out);
      Log.log("Sent message "+m.id+" to "+dest+", src="+m.src,Log.INFO);
    } catch (IOException ex) {
      Log.log("Cant send message to "+dest,Log.FATALERROR);
    }
  }

  /** Wait for message.
   * @param id id of message
   * @return Message received message
   */
  public Message getReplay( final long id ) {
    return waitFor(new Acceptor() {
      public boolean test(Message m) {
        return m.id == id;
      }
    },60*1000);
  }

  /** Wait for message.
   * @param a Message Acceptor
   * @return Accepted Message
   */
  public Message waitFor( Acceptor a ) {
    return waitFor(a, Long.MAX_VALUE );
  }

  /** Wait for message.
   * @param a Message Acceptor
   * @param timeout timeout in msec
   * @return Accepted Message
   */
  public Message waitFor( Acceptor a, long timeout ) {
    for( int j=0;j<2;j+= timeout <= 0 ? 1 : 0) {
      synchronized(this) {
        for (Iterator i = mailbox.listIterator(); i.hasNext(); ) {
          Message m = (Message) i.next();
          if (a.test(m) ) {
            i.remove();
            return m;
          }
        }
        long wait = Math.min(500,timeout);
        if( wait == 0)
          return null;
        synchronized( mailbox ) {
          try {
            mailbox.wait(wait);
          } catch (InterruptedException ex) {}
        }
        timeout -= wait;
      }
    }
    return null;
  }

  /** Collect messages.
   *
   * @param a Message Acceptor
   * @param timeout time in msec to gather messages
   * @return List gathered messages
   */
  public List collect( Acceptor a, long timeout ) {
    long deadLine = System.currentTimeMillis() + timeout;
    List l = new LinkedList();
    synchronized( mailbox ) {
      while (System.currentTimeMillis() < deadLine) {
        for (Iterator i = mailbox.listIterator(); i.hasNext(); ) {
          Message m = (Message) i.next();
          if (a.test(m)) {
            i.remove();
            l.add(m);
          }
        }
        try { mailbox.wait(deadLine-System.currentTimeMillis()); }
        catch (InterruptedException ex) {}
      }
    }
    return l;
  }

  private class Receiver extends Thread {
    public void run() {
      // Round-robin all input streams for messages
      while(true) {
        int relayed = 0;
        synchronized (this) {
          for (Iterator i = sockets.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry) i.next();
            SocketStreams ss = (SocketStreams) e.getValue();
            try {
              if (ss.inr.ready() /*.available() > 0*/) {
                Message m = Message.read(ss);
                Log.log("Received message "+m.id+", obj="+m.m,Log.INFO);
                synchronized(mailbox) {
                  mailbox.add(m);
                  mailbox.notifyAll();
                }
                // Debug
                relayed++;
              }
            } catch (IOException ex) {
              Log.log("Error reading msg, try connection reset?",Log.ERROR,ex);
            }
          } //endfor
          if( relayed == 0 ) {
            try {wait(250); } catch (InterruptedException ex) {} // Releases monitor
          } else
            this.notify();
        } // endsync
      } //endwhile
    }
  }

  private static Random rnd = new Random(System.currentTimeMillis());

  /** RPC Message container.
   */
  public static class Message implements Serializable {

    /** Message source. Initialized to
     * {@link fc.syxaw.fs.Config#THIS_LOCATION_ID } when not
     * explicitly given.
     */

    public String src;

    /** Message id. Initialized to a random number when not explicitly given.
     */
    public long id;

    /** Message content. */
    public Serializable m;

    public Message(  ) {
    }

    public Message( Serializable m ) {
      id = rnd.nextLong();
      src = fc.syxaw.fs.Config.THIS_LOCATION_ID;
      this.m = m;
    }

    public Message( long id, String src, Serializable m ) {
      this.id = id;
      this.src = src;
      this.m = m;
    }

    public Message( long id,  Serializable m ) {
      this.id = id;
      src = fc.syxaw.fs.Config.THIS_LOCATION_ID;
      this.m = m;
    }


    private void write(OutputStream os) throws IOException {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(512);
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeObject(this);
      oos.close();
      String msg =new String( Base64.encode(bos.toByteArray()));
      //Log.log("Write msg string is "+msg,Log.INFO);
      os.write((msg+"\n").getBytes());
      os.flush();
    }

    private static Message read(SocketStreams in) throws IOException {

      Message m = null;
      try {
        String ms = in.inr.readLine();
        //Log.log("Read msg string is "+ms,Log.INFO);
        ByteArrayInputStream bin = new ByteArrayInputStream(Base64.decode(ms.
                toCharArray()));
        m = (Message) (new ObjectInputStream(bin)).readObject();
      } catch (ClassNotFoundException ex) {
        Log.log("Unknown object in stream",Log.FATALERROR,ex);
      }
      return m;
    }
  }

  private static class SocketStreams {
    public Socket s;
    public InputStream in;
    public OutputStream out;
    public BufferedReader inr;

    public SocketStreams(Socket s) throws IOException {
      this.s = s;
      in =  s.getInputStream();
      out = s.getOutputStream();
      inr = new BufferedReader(new InputStreamReader(in));
    }
  }

  /** Interface for message acceptor. */
  public interface Acceptor {

    /** Test if the acceptor accepts a message.
     * @param m Message
     * @return true if accepted
     */
    boolean test( Message m );
  }

  private class Relay extends Thread {
    public void run() {
      while(true) {
        Message m = waitFor(new Acceptor() {
          public boolean test(fc.syxaw.proto.RPC.Message m) {
            return true;
          }
        });
        Log.log("Relaying message back to "+m.src,Log.INFO);
        send(m.src, new Message(m.id, "Pong!"));
      }
    }
  }

  /** Prototype find reply implementation. Replies to each
   * {@link fc.syxaw.protocol.Proto.Request} message with
   * a list of locally available branches. */

  public class FindResponder extends Thread {
    public void run() {
      while(true) {
        Message m = waitFor(new Acceptor() {
          public boolean test(fc.syxaw.proto.RPC.Message m) {
            return m.m instanceof Proto.Request;
          }
        });
        // Alternatives
        if( m.m instanceof Proto.FindReq ) {
          SyxawFile f = Syxaw.getFile("//");
          List vl = f.getLinkVersionHistory(false).getFullVersions(null); // FIXME-versions:
          Set branches = new HashSet();
          Version p = null;
          // scan for
          // x
          // x/thisdev/y
          // --> x/thisdev/y is a branching point
          for (Iterator i = vl.iterator(); i.hasNext(); ) {
            Version v = (Version) i.next();
            if( p!=null && v.compareTo(p) == 1 &&
                GUID.getCurrentLocation().equals(v.getLID()) )
              branches.add(v);
              //v.addToJoins(branches);
            p = v;
          }
          Log.log("Hist is "+branches,Log.INFO);
          send(m.src,
               new Message(m.id,
                           new Proto.FindRep(GUID.getCurrentLocation(),
                           Version.makeJoins(branches),
                           ((Proto.FindReq) m.m).replyTo)));
        }
      }
    }
  }

}

// arch-tag: 20050805213312ctl
