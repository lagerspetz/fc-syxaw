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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;

import EDU.oswego.cs.dl.util.concurrent.Channel;
import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import fc.syxaw.tool.CliUtil;
import fc.syxaw.util.Util.ObjectHolder;
import fc.util.log.Log;
import fc.util.log.SysoutLogger;

public class TcpForward implements Runnable {

  public static int connections = 0, bytesUp = 0, bytesDown = 0;
    
  private int from,to,bwlimit,latency;
  
  Channel sendQ = new LinkedQueue();
  Channel receiveQ = new LinkedQueue();
  
  public TcpForward(int from, int to, int bwlimit, int latency) {
    super();
    this.from = from;
    this.to = to;
    this.bwlimit = bwlimit;
    this.latency = latency;
  }

  private class Packet {
    byte [] data;
    long earliestArrive;
    Socket from;
    Socket to;
    int size;
      
    public Packet(byte[] data, int size, long earliestArrive, Socket from,
        Socket to) {
      super();
      this.data = data;
      this.earliestArrive = earliestArrive;
      this.from = from;
      this.to = to;
      this.size = size;
    }    
  }
  
  // Acceptor
  public void run() {
    (new QueueConsumer(sendQ)).start();
    (new QueueConsumer(receiveQ)).start();
    ServerSocket ss = null;
    try {
      ss = new ServerSocket(from);
      while (true) {
        Socket s = ss.accept();
        Socket s2 = new Socket("localhost",to);
        (new SocketConsumer(sendQ,s,s2)).start();
        (new SocketConsumer(receiveQ,s2,s)).start();
        connections++;
        Log.log("Accepted connection at port "+from,Log.INFO);
      }
    } catch (IOException ex) {
      Log.log("Failed to accept connection on "+from,Log.ERROR,ex);
    } finally {
      if( ss !=  null) {
        try {
          ss.close();
        } catch (IOException e) {
          Log.error(e);
        }
      }
    }

  }
  
  public class QueueConsumer extends Thread {
    Channel queue;
    
    public QueueConsumer(Channel queue) {
      super();
      this.queue = queue;
    }

    public void run() {
      try {
        while (true) {
          Packet p = (Packet) queue.take();
          long now = System.currentTimeMillis();
          long wait = p.earliestArrive - now;
          if (wait > 0) {
            Thread.sleep(wait);
          }
          try {
            OutputStream os = p.to.getOutputStream();
            if( p.data == null) {
              p.to.getOutputStream().close();
            } else if (os != null) {
              if (queue==receiveQ) {
                bytesDown += p.size; 
              } else {
                bytesUp += p.size;
              }
              os.write(p.data,0,p.size);
            }
          } catch (IOException e) {
            Log.error(e);
          }
        }
      } catch (InterruptedException e) {
        Log.error(e);
      } 

    }
  }
  
  public class SocketConsumer extends Thread {

    Channel queue;
    Socket from;
    Socket to;
    
    public SocketConsumer(Channel queue, Socket from, Socket to) {
      super();
      this.queue = queue;
      this.from = from;
      this.to = to;
    }


    public void run() {
      long adj = 0;
      try {
        try {
          InputStream src = from.getInputStream();
          int maxLeft = Integer.MAX_VALUE;
          int total = 0, count = 0;
          do {            
            byte[] buffer = new byte[4096];
            int maxchunk = (int) (maxLeft > buffer.length ? buffer.length : maxLeft);
            count = src.read(buffer, 0, maxchunk);
            if (count > 0) {
              long milliBytes = 1000l * ((long) count) + adj;
              long transfertimeMs = milliBytes/((long) bwlimit);
              long milliBytesInWindow = ((long) bwlimit) * transfertimeMs;
              adj = milliBytes - milliBytesInWindow;
              long sendDone = System.currentTimeMillis() + transfertimeMs;              
              long earliestArrive = sendDone + latency;
              //Log.debug("Relaying packet of size "+count+" from "+from+" to "+to);
              queue.put(new Packet(buffer,count,earliestArrive,from,to));            
              total += count;
              maxLeft -= count;
              Thread.sleep(transfertimeMs);
            }
          }
          while (count > -1 && maxLeft > 0);
          //Log.debug("Relaying normal EOS from "+from+" to "+to);
          queue.put(new Packet(null,-1,0,from,to)); // End of stream packet 
        } catch( IOException ex) {
          //Log.debug("Relaying error EOS from "+from+" to "+to);
          queue.put(new Packet(null,-1,0,from,to)); // End of stream packet 
        }
      } catch (InterruptedException ix) {
        Log.error(ix);
      }
      
    }
    
  }
  
  public static void forward(PrintStream pw, String[] args)
      throws ParseException {
    ObjectHolder<Integer> bw = new ObjectHolder<Integer>(5*1024);
    ObjectHolder<Integer> latency = new ObjectHolder<Integer>(1000);
    ObjectHolder<Integer> from = new ObjectHolder<Integer>(40000);
    ObjectHolder<Integer> to = new ObjectHolder<Integer>(39999);
    args = CliUtil.hasOpt(args, "bw", bw);
    args = CliUtil.hasOpt(args, "latency", latency);
    args = CliUtil.hasOpt(args, "from", from);
    args = CliUtil.hasOpt(args, "to", to);
    TcpForward f = new TcpForward(from.get(),to.get(),bw.get(),latency.get());
    (new Thread(f)).start();
    pw.println("New limited tcp fwd from "+from+" to "+to+ ", bw="+bw+
        ", latency="+latency);
  }

  public static void stat(PrintStream pw, String[] args) {
    pw.println("Connections="+connections+", downloaded="+bytesDown+
        ",uploaded="+bytesUp);
  }

  public static void main(String[] args) {
    Log.setLogger(new SysoutLogger());
    int bwlimit = 5*1024;
    int latency = 1000;
    TcpForward f = new TcpForward(40000,39999,bwlimit,latency);
    (new Thread(f)).start();
  }


}
// arch-tag: 31c588c7-e31c-4b89-97a6-4ffe3effe641
//
