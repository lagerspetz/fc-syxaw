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

package fc.syxaw.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import fc.syxaw.fs.Constants;
import fc.syxaw.fs.GUID;
import fc.syxaw.util.Util;
import fc.util.log.Log;

import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpServer;
import org.mortbay.http.SocketListener;
import org.mortbay.jetty.servlet.ServletHandler;

public class HTTPCallServer extends HTTPCallChannel {

  // Be careful with this!
  private static final boolean MULTITHREAD_SERVER = 
    System.getProperty("syxaw.debug.mt_server",null) != null ;
  
  private static CallMultiplexer multiplexer = null;
  private static Object LISTENER_LOCK = new Object();

  private static Object serverLock = new Object();

  HTTPCallServer(String dest) throws IOException {
    super(dest);
  }

  /** Listen for incoming calls. Blocking.
   * @param cm object mapping call names to call handlers
   * @throws IOException could not listen for requests
   */
  public static void listen(CallMultiplexer cm) throws IOException {
    synchronized (LISTENER_LOCK ) {
      multiplexer = cm;
      int listenPort = Config.LISTEN_PORT;
      try {
        java.net.ServerSocket s = new java.net.ServerSocket(listenPort);
        s.close();
      }
      catch (IOException x) {
        listenPort++;
        Log.log(
            "HTTP listener port already bound, assuming HTTP monitor. Retrying at "
            + listenPort, Log.INFO);
      }

      Log.log("HTTP listener at port " + listenPort, Log.INFO);

      // Create the server
      HttpServer server = new HttpServer();

      // Create a port listener
      SocketListener listener = new SocketListener();
      listener.setPort(listenPort);
      listener.setMaxIdleTimeMs(100000);
      server.addListener(listener);

      // Create a context
      HttpContext context = new HttpContext();
      context.setContextPath("");
      server.addContext(context);

      // Create a servlet container
      ServletHandler servlets = new ServletHandler();
      context.addHandler(servlets);

      // Map the Syxaw servlet onto the container
      servlets.addServlet("Syxaw", "/syxaw/*", HTTPCallServlet.class.getName());

      // Start the HTTP server
      try {
        server.start();
      }
      catch (Exception x) {
        Log.log("HTTP server init failed", Log.FATALERROR, x);
        throw new IOException("Cannot start HTTP server");
      }

      try {
        Thread.currentThread().join(); // At some point we may want this to
        // e.g. return an error code if the channel dies
      }
      catch (InterruptedException x) {}
    }
  }
  static void service( String method, HttpServletRequest request,
                       HttpServletResponse response)
      throws ServletException, IOException {
    if( Config.MEASURE_TIMES ) {
      fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"rpcr-begin",System.currentTimeMillis());
    }
    if( multiplexer == null )
      Log.log("No call handlers",Log.FATALERROR);
    String callName = request.getPathInfo();
    fc.syxaw.util.Log.time("RPC call, reading request "+callName);
    SynchronousCallHandler h = null;
    if( callName != null && callName.startsWith("/") ) {
        callName = callName.substring(1);
        h = multiplexer.getHandler(callName);
    } else
      h= null;
    if( h == null ) {
      Log.log("No handler for call " + callName, Log.ERROR);
      response.addHeader("Content-Type","text/plain");
      response.getOutputStream().write((
          "This is the URL of a Syxaw file system.\n"+
          "Syxaw version: "+Constants.SYXAW_VERSION+"\n"+
          "Location id: "+GUID.getCurrentLocation()+"\n").getBytes());
      return;
    }
    Log.log("Processing RPC "+callName,Log.INFO);
    InputStream requestIn = request.getInputStream();
    if( Config.MEASURE_TIMES ) {
      // Buffer all input data
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      Util.copyStream(requestIn,os);
      requestIn = new ByteArrayInputStream(os.toByteArray());
      fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"time-b-process",
                      new Long(System.currentTimeMillis()));
    }
    if( "POST".equalsIgnoreCase(method) && // POST=potentially has data
        GZIP_ENCODING.equalsIgnoreCase(request.getHeader("Content-Encoding") ) )
        requestIn = new GZIPInputStream(requestIn);

    Map parameterMap =  null;
    if( "POST".equalsIgnoreCase(method) ) {
      // NOTE: If we use request.getParameterMap() here we're screwed,
      // as that method will parse the (entire) input stream as a list of key=value
      // First <EOS> or <CR><LF> terminated line = Query string
      // in application/x-www-form-urlencoded
      ByteArrayOutputStream bis = new ByteArrayOutputStream();
      for( int ch = -1;(ch = requestIn.read()) != -1 && ch!='\n'; ) {
        if( ch == '\r' ) continue;
        bis.write(ch);
      }
      parameterMap =
          javax.servlet.http.HttpUtils.parseQueryString(new String(bis.toByteArray()));
    } else
      parameterMap = request.getParameterMap();
    Map flattenedMap = new java.util.HashMap();
    // the map unfortunately contains String[], so we need to unbox it
    for( Iterator i = parameterMap.entrySet().iterator();i.hasNext();) {
      Map.Entry e = (Map.Entry) i.next();
      String key = (String) e.getKey();
      flattenedMap.put(key,
                       ((String[]) e.getValue())[0]);
    }
    //Log.log("=======HTTP Parameter map is "+flattenedMap,Log.INFO);
    ObjectInputStream in = new ObjectInputStream( requestIn );
    PropertyDeserializer is = new PropertyDeserializer(flattenedMap );
    Serializable[] repHeads = h.getRequestHeaders();
    for( int i=0;i<repHeads.length;i++)
      is.readObject((PropertySerializable) repHeads[i]);

    Serializable[] rh = null;
    try {
      long wait = System.currentTimeMillis();
      serverLock = MULTITHREAD_SERVER ? new Object() : serverLock;
      synchronized(serverLock) {
        wait = (-wait+System.currentTimeMillis());
        if(wait > 100) {
          Log.warning("Waited for RPC request handler ms="+wait);
        }
        if (h instanceof HTTPSynchrounousCallHandler)
          rh =
                ((HTTPSynchrounousCallHandler) h).invoke(in, response);
        else
          rh = h.invoke(in);
      }
    } catch ( Exception x ) {
      System.out.println("THROWABLE IN HTTPCALLSERVER!");
      x.printStackTrace();
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    if( rh != null ) {
      Map replyHeaders=new java.util.HashMap();
      PropertySerializer ps = new PropertySerializer(replyHeaders);
      // Send reply headers
      for( int i=0;i<rh.length;i++)
        ps.writeObject((PropertySerializable) rh[i]);
      for( Iterator i = replyHeaders.entrySet().iterator();i.hasNext();) {
        Map.Entry e = (Map.Entry) i.next();
        response.addHeader( SYXAW_HEADER_PREFIX + (String)e.getKey(),(String) e.getValue());
      }
    }
    // Send reply data
    OutputStream dataOut = response.getOutputStream();
    if( Config.MEASURE_TIMES ) {
      dataOut = new ByteArrayOutputStream();
      fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"outbytebuf",dataOut);
    }
    String acceptedEncodings = Config.COMPRESS_DATA ?
        request.getHeader("Accept-Encoding") :  null;
    // FP: poor man's gzip test; proper code is to split by, and check array...
    if( acceptedEncodings != null &&
        acceptedEncodings.toLowerCase().indexOf(GZIP_ENCODING) != -1 ) {
      response.setHeader("Content-Encoding",GZIP_ENCODING);
      response.setHeader("Content-Length",null);
      dataOut = new GZIPOutputStream( dataOut );
    }
    h.getData(new ObjectOutputStream( dataOut ));
    dataOut.flush();
    if( dataOut instanceof GZIPOutputStream )
      ((GZIPOutputStream) dataOut).finish();
    response.setStatus(HttpServletResponse.SC_OK);
    if( Config.MEASURE_TIMES ) {
      // END PROCESS
      InputStream din = new ByteArrayInputStream(
          ( (ByteArrayOutputStream) fc.syxaw.util.Log.getDebugObj(HTTPCallChannel.class, "outbytebuf", true)).
          toByteArray());
      long tp = System.currentTimeMillis() -
      fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,
                                         "time-b-process", true);
      response.setHeader("T",""+tp);
      Util.copyStream(din, response.getOutputStream());
      // NOTE: full RPC time here is inaccurate, since we don't know when the HTTP
      // request headers are actually received (somewhere in servlet server code)
      long rpctime = System.currentTimeMillis() -
      fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,"rpcr-begin",true);

      fc.syxaw.util.Log.stat("HTTP-REPLY-"+request.getMethod(),"rpc-reply-time-INACCURATE",
               ""+rpctime,"process-time",""+tp);
    }
  }
}

// arch-tag: 921ca809-0a72-4a0e-87bb-a2a493519ec6
