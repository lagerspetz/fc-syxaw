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

// $Id: HTTPCallChannel.java,v 1.30 2005/01/05 11:37:04 ctl Exp $
package fc.syxaw.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import fc.syxaw.fs.Constants;
import fc.syxaw.util.Util;
import fc.util.log.Log;

/** HTTP implementation of synchronous call channel.
 * <p><b>Synchronization protocol RPC fomat</b>
 * <p><i>RPC request format</i>
 * <p>header objects: header objects are serialized to a list of (name,value)s
 * using {@link PropertySerializer}. This list is then added to the HTTP
 * query string as query parameters and values (applying URL encoding
 * as necessary).<br>
 * data: if present, the <code>POST</code> method is used, otherwise
 * <code>GET</code> is used. The
 * data is filtered (i.e. serialized in case of objects written to the data stream)
 * by {@link ObjectOutputStream}, and then optionally HTTP Content-encoded
 * using the <code>gzip</code> encoding (if {@link Config#COMPRESS_DATA} is enabled).
 * <p>The method name is encoded in the request URL as the requested file.
 * The request URL becomes<pre>
http://<i>host</i>:{@link Config#REQUEST_PORT}/{@link Config#SERVLET_URL}/<i>callName</i>?<i>headers</i>
 * </pre>where
 * <i>host</i> is the IP of the host the channel is connected to<br>
 * <i>callName</i> is the name of the call<br>
 * <i>headers</i> is the RPC headers as query string.
 * <p>Example request URL:<pre>
http://constitution.hiit.fi:23902/syxaw/download?versionsAvailable=&objectId=&data=true&metadata=true&1&acceptedEncodings=0+3+1
</pre>
 * <p><i>RPC response format</i>
 * <p>header objects: header objects are serialized to a list of (name,value)s
 * using {@link PropertySerializer}, and then passed as extended HTTP headers,
 * identified by the prefix {@link #SYXAW_HEADER_PREFIX}. The name of the
 * HTTP response header for each name is {@link #SYXAW_HEADER_PREFIX}+name.<br>
 * data: any data is filtered (i.e. serialized in case of objects written to the data stream)
 * by {@link ObjectOutputStream}, and then optionally HTTP Content-encoded
 * using the <code>gzip</code> encoding (if {@link Config#COMPRESS_DATA} is enabled).
 * The HTTP response URL becomes<pre>
<i>Standard HTTP headers</i>
<i>Syxaw HTTP headers</i>

<i>data</i>
</pre>
 * <p>Example HTTP response:<pre>
HTTP/1.1 200 OK
Date: Tue, 02 Sep 2003 11:47:40 GMT
Server: Jetty/4.2.10pre0 (Linux/2.4.18 i386 java/1.4.1-beta)
Content-Encoding: gzip
Transfer-Encoding: chunked
Xstatus: 0
Xversion: 1003

14
....................
</pre>
 *  */

public class HTTPCallChannel extends SynchronousCallChannel {

  protected static long _requestCounter=0;
  protected String destName;
  
  /** Prefix used for all Syxaw HTTP headers. */
  public static final String SYXAW_HEADER_PREFIX = "X";

  protected static final String GZIP_ENCODING = "gzip";

  protected HTTPCallChannel(String dest) throws IOException {
    super(dest);
    destName = dest; //OLD: dest.resolve().getHostName();
  }

  public synchronized ObjectInputStream call(String name,
      SynchronousCallChannel.SynchronousCall call, Serializable[] replyHeaders
  ) throws IOException {
    int left = Config.HTTP_RETRIES;
    ObjectInputStream is = null;
    boolean ok = false;
    long delay = 500;
    do {
      try {
        is = callImpl(name, call, replyHeaders);
        ok = true;
      } catch (IOException e) {
        if (delay > 4000) {
          Log.log("Could not connect: " + e + ", giving up.", Log.ERROR);
          return null;
        }
        else {
          Log.log("Could not connect: " + e + ", trying again in " + delay/1000.0 + " seconds.", Log.WARNING);
        }
        ok = false;
        delay = delay * 2;
        try {
          wait(delay);
        } catch (InterruptedException i) {/*woken up*/ }
      }
    } while (!ok && left-->0);
    return is;
  }

  public synchronized ObjectInputStream callImpl(String name,
      SynchronousCallChannel.SynchronousCall call, Serializable[] replyHeaders
      ) throws IOException {
    _requestCounter++;
    if( Config.MEASURE_TIMES ) {
        fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class, "time-b-rpc",
                      System.currentTimeMillis());
                      
    }
    Map callRequestProperties = new java.util.HashMap();
    PropertySerializer os = new PropertySerializer(callRequestProperties);
    Map httpRequestProperties = new java.util.HashMap();
    Serializable[] requestHeader = null;
    String postQueryString = null;
    if( call instanceof HTTPSynchrounousCall )
      requestHeader =
          ((HTTPSynchrounousCall) call).getRequestHeaders(httpRequestProperties);
    else
      requestHeader = call.getRequestHeaders();
    for( int i=0;requestHeader != null && i<requestHeader.length;i++)
      os.writeObject((PropertySerializable) requestHeader[i]);
    os.close();

    URL dest = null;
    boolean hasdata = call instanceof HTTPSynchrounousCall ?
        (( HTTPSynchrounousCall ) call).hasData() : true;
    try {
      String file = call instanceof HTTPSynchrounousCall ?
          (( HTTPSynchrounousCall ) call).getFile(name,callRequestProperties) :
          getDefaultFile(name,callRequestProperties);
      if( file.length() > Config.MAX_URL_LENGTH || hasdata ) {
        int splitpos = file.indexOf('?');
        postQueryString = file.substring(splitpos+1);
        file = file.substring(0,splitpos);
      }
      dest = new URL("http", getRealDest(destName), getRequestPort(destName),
                     file);
    } catch (java.net.MalformedURLException x ) {
      Log.log("Malformed url",Log.ASSERTFAILED);
    }
    Log.log("HTTP Request: "+dest,Log.INFO);
    if( !Util.isEmpty(postQueryString) )
      Log.log("Post query str: "+postQueryString,Log.INFO);
    HttpURLConnection conn = (HttpURLConnection) dest.openConnection();
        String requestMethod = postQueryString != null ? "POST" : "GET";
    conn.setRequestMethod(requestMethod );
    for( Iterator i = httpRequestProperties.entrySet().iterator();i.hasNext();) {
      Map.Entry e = (Map.Entry) i.next();
      String value = (String) e.getValue();
      if ( !Util.isEmpty(postQueryString) && "Content-Length".equalsIgnoreCase((String) e.getKey())) {
	  continue;
	  /* Include when correct
	  long v = Long.parseLong(value);
	  v += postQueryString.getBytes().length;
	  if (hasdata) {
	      v += 2;
	  }
	  value = Long.toString(v);
	  Log.log("Setting " + e.getKey() + "=" + value, Log.INFO);*/
      }
      conn.setRequestProperty((String) e.getKey(),value);
    }
    conn.setRequestProperty("User-Agent","Syxaw/"+Constants.SYXAW_VERSION+
                            " (Java "+System.getProperty("java.version")+")");
    conn.setRequestProperty("Accept","application/syxaw"); // No accept header
    if( Config.COMPRESS_DATA ) {
      // Note: not sure if it's valid HTTP/1.1 to use ContEnc with POST
      conn.setRequestProperty("Content-Encoding",GZIP_ENCODING);
      conn.setRequestProperty("Accept-Encoding",GZIP_ENCODING);
    }
    fc.syxaw.util.Log.time("START RPC "+name);
    if( _requestCounter == 1) {
      Log.log("Connection class is", Log.DEBUG, conn.getClass().getName());
    }
    if(hasdata || postQueryString != null) {
      conn.setDoOutput(true); // Needed for data upload
      /*!5 conn.setChunkedStreamingMode(Config.SENDBUF_SIZE); */
      if( hasdata )
        conn.setRequestProperty("Content-Type","application/syxaw");
      else
        conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");

      OutputStream requestDataStream =
         Config.MEASURE_TIMES ? null : conn.getOutputStream();
      if( Config.MEASURE_TIMES ) {
        requestDataStream = new ByteArrayOutputStream();
        fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"sendbuf",requestDataStream);
      }
      if (Config.COMPRESS_DATA) {
        requestDataStream = new GZIPOutputStream(requestDataStream);
      }
      if( postQueryString != null ) {
        //Log.log("postQueryString="+postQueryString,Log.INFO);
        requestDataStream.write(postQueryString.getBytes());
        if( hasdata )
          requestDataStream.write("\r\n".getBytes());
      }
      // Only hasdata
      if( !Config.MEASURE_TIMES )
        conn.connect();
      // Write any upload data
      if( Config.MEASURE_TIMES ) {
          fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"time-b-gzip",System.currentTimeMillis());
      }
      ObjectOutputStream oos = new ObjectOutputStream(requestDataStream);
      if( hasdata )
        call.getData(oos);
      if (requestDataStream instanceof GZIPOutputStream)
        ( (GZIPOutputStream) requestDataStream).finish();
      oos.close();
      if( Config.MEASURE_TIMES ) {
	  fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"time-gzip",
                        System.currentTimeMillis()-
                        fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,
                        	"time-b-gzip",true) );
        byte[] sendbuf =
            ( (ByteArrayOutputStream) fc.syxaw.util.Log.getDebugObj(
        	    HTTPCallChannel.class, "sendbuf", true)).toByteArray();
        InputStream din = new ByteArrayInputStream(sendbuf);
        Log.log("Setting sendbuf content length to " + sendbuf.length, Log.INFO);
        conn.setRequestProperty("Content-Length",""+sendbuf.length);
        fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"request-bytes",
			sendbuf.length);
        fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class, "time-connect",
                        System.currentTimeMillis());
        conn.connect();
        OutputStream os2 = conn.getOutputStream();
        Util.copyStream(din,os2);
//        conn.getOutputStream().close();
      }
    } else { // !hasdata
      if( Config.MEASURE_TIMES ) {
          fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"request-bytes",
			dest.toString().length());
          fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"time-connect",
                        System.currentTimeMillis());
          fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"time-gzip",0l);
      }
      conn.connect();
    }
    fc.syxaw.util.Log.time("REQUEST_SENT RPC "+name);
    if( Config.MEASURE_TIMES ) {
        fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class, "time-send",
                        System.currentTimeMillis());
        fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"time-answer",
                      System.currentTimeMillis());
    }
    if( conn.getResponseCode() != HttpURLConnection.HTTP_OK ) {
      Log.log("RPC request failed: ",Log.ERROR, new Object[] {
              "code="+conn.getResponseCode(),
              "message="+conn.getResponseMessage()});
    }
    Log.log("Receiving reply for "+dest,Log.INFO);
    fc.syxaw.util.Log.time("Getting reply");
    Map replyHeadersMap = new java.util.HashMap();
//    Log.log("HFIELDS="+conn.getHeaderFields(),Log.INFO);
    // Read headers using the JDK 1.1 interface
    // Allows use og e.g. HTTPClient.HttpUrlConnection as protocol handler
    for (int i = 1; conn.getHeaderFieldKey(i) != null; i++) {
      String key = conn.getHeaderFieldKey(i);
      //Log.log("Odhdr: "+key+"="+conn.getHeaderField(i),Log.DEBUG);
      if (!key.startsWith(SYXAW_HEADER_PREFIX))
        continue;
      replyHeadersMap.put(key.substring(SYXAW_HEADER_PREFIX.length()),
                          conn.getHeaderField(i));
    }

    PropertyDeserializer rhDes = new PropertyDeserializer(replyHeadersMap);
    for( int i=0;i<replyHeaders.length;i++)
      rhDes.readObject((PropertySerializable) replyHeaders[i]);
    InputStream replyIn = conn.getInputStream();
    if( Config.MEASURE_TIMES ) {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      Util.copyStream(replyIn,buf);
      // Time transfer = connect to reply read - gzip time
      fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"time-transfer-done",
                      System.currentTimeMillis());
      fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"time-transfer+rp",
                      System.currentTimeMillis()-
                      (fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,
"time-answer",false)));

      fc.syxaw.util.Log.putDebugObj(HTTPCallChannel.class,"time-rprocess",Long.parseLong(
          conn.getHeaderField("T")));
      fc.syxaw.util.Log.time("Reply read, raw bytes="+buf.size());
      fc.syxaw.util.Log.stat("HTTP-"+requestMethod,"request",
               requestMethod+" "+dest,"reply-raw-size",""+buf.size());
      replyIn=new ByteArrayInputStream(buf.toByteArray());
    }
    if( GZIP_ENCODING.equalsIgnoreCase( conn.getHeaderField("Content-Encoding") ) )
      replyIn = new GZIPInputStream(replyIn);
    if( Config.MEASURE_TIMES ) {
	long now = System.currentTimeMillis(),
          lat_transfer_rprocess =
            now-fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,"time-connect",false),
          transfer_rprocess = fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,"time-transfer+rp",true),
          rprocess = fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,"time-rprocess",true),
          lat=lat_transfer_rprocess-transfer_rprocess,
          transfer=transfer_rprocess-rprocess,
          gzip = fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,"time-gzip",true),
	  rqbytes= fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,
				    "request-bytes",true),
	 winstart =
         fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,"time-connect",false),
	      winend = fc.syxaw.util.Log.getDebugLong(HTTPCallChannel.class,
					"time-transfer-done",true);
    fc.syxaw.util.Log.stat("TIMES-HTTP-"+requestMethod,
               "winstart",""+winstart,
               "rprocess",""+rprocess,
               "winend",""+winend,
	       "winsize",""+(winend-winstart),
               "request-bytes",""+rqbytes);
    }
    return new ObjectInputStream( replyIn );
  }

  /** Call handler providing access to HTTP call channel specific features. */
  public static abstract class HTTPSynchrounousCallHandler
      implements SynchronousCallHandler {

    public abstract Serializable[] getRequestHeaders();

    /** Dummy implementation.
     * Throws <code>java.lang.UnsupportedOperationException</code>
     */
    public Serializable[] invoke(ObjectInputStream requestDataStream) throws
        IOException {
      throw new UnsupportedOperationException();
    }


    /** Invoke call and get reply headers. Allows the call handler to access
     * the HTTP response in order to set e.g. the content length. The default
     * implementation calls {@link #invoke(ObjectInputStream)}.
     *
     * @param response servlet response object for this HTTP request
     * @param requestDataStream data stream from caller
     * @return reply headers as an array of serializable objects
     * @throws IOException if the call invocation fails.
     */

    public Serializable[] invoke(ObjectInputStream requestDataStream,
                                 javax.servlet.http.HttpServletResponse response)
        throws IOException {
      return invoke(requestDataStream);
    }

    public abstract void getData(ObjectOutputStream os) throws IOException;

  }

  /** Synchronous call providing access to HTTP call channel specific features. */
  public static abstract class HTTPSynchrounousCall implements SynchronousCall {

    /** Dummy implementation.
     * Throws <code>java.lang.UnsupportedOperationException</code>
     */
    public Serializable[] getRequestHeaders() throws IOException {
      throw new UnsupportedOperationException();
    }

    /** Get request headers for call. Allows the caller to access and
     * modify the HTTP request headers before the request is sent.
     * @param headers map (String,String) of HTTP request headers.
     */
    public Serializable[] getRequestHeaders(Map headers)
        throws IOException {
      return getRequestHeaders();
    }

    public abstract void getData(ObjectOutputStream os) throws IOException;

    public boolean hasData() {
      return true;
    }

    public String getFile(String callName, Map properties) {
      return getDefaultFile(callName, properties);
    }
  }

  protected static String getDefaultFile(String callName,Map dataList) {
    StringBuffer sb = new StringBuffer();
    for (Iterator i = dataList.entrySet().iterator(); i.hasNext(); ) {
      sb.append(sb.length() == 0 ? '?' : '&');
      Map.Entry e = (Map.Entry) i.next();
      sb.append(java.net.URLEncoder.encode(
              (String) e.getKey()));
      sb.append('=');
      sb.append(java.net.URLEncoder.encode(
              (String) e.getValue()));

    }
    return Config.SERVLET_URL + '/' + callName + sb.toString();
  }

  protected String getRealDest(String dest) {
    if (Config.LID_HOST_MAP != null) {
      String host = (String) Config.LID_HOST_MAP.get(dest);
      return host != null ? host : dest;
    } else
      return dest;
  }

  protected int getRequestPort(String dest) {
    if (Config.LID_HOST_MAP != null) {
      Integer port = (Integer) Config.LID_PORT_MAP.get(dest);
      return port != null ? port.intValue() : Config.REQUEST_PORT;
    } else
      return Config.REQUEST_PORT;
  }

}
// arch-tag: 22ca2505e6087605bd5b57b9e128465c *-
