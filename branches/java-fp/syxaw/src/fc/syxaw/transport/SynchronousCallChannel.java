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

// $Id: SynchronousCallChannel.java,v 1.10 2003/10/15 07:22:03 ctl Exp $
package fc.syxaw.transport;

import java.io.IOException;
import java.io.Serializable;

/** Synchronous large-object RPC channel. Abstract class for a communications
 * channel between two devices suitable for synchronous RPCs which require
 * arbitrary amounts of data to be transmitted (such as file downloads and
 * uploads). Each RPC request and reply is divided into headers (of fixed size)
 * and an arbitrary amount of data.
 */

public abstract class SynchronousCallChannel  {

  public SynchronousCallChannel(String dest) {  
  }
  
  /** Invoke RPC call.
   * @param name name of RPC call, e.g. <code>download</code>
   * @param call RPC call request.
   * @param replyHeaders Array of empty header objects (that must match the
   * headers returned by the remote device) that will be filled
   * in when the method returns.
   * @return input stream to RPC reply data.
   * @throws IOException if the call fails, e.g. network down
   */
  public abstract ObjectInputStream call( String name, SynchronousCall call,
                                             Serializable[] replyHeaders )
      throws IOException;


  /** Interface for RPC requests. The call channel handles
   * an outgoing RPC requests by <ol>
   * <li> invoking {@link #getRequestHeaders}, and sending
   * the headers to the callee</li>
   * <li> invoking {@link #getData}, that streams
   * any request data to the callee.</li>
   * </ol>
   */

  public interface SynchronousCall {
    /** Get request headers for call. */
    public Serializable[] getRequestHeaders() throws IOException;
    /** Get request data. When invoked, this method must write all
     * request data to the given output stream.*/
    public void getData(ObjectOutputStream os) throws IOException;
  }

  /** Interface for handling RPC requests. The call channel handles
   * incoming RPC requests by locating a call handler <i>ch</i> for the request,
   * and:<ol>
   * <li> invoking {@link #getRequestHeaders ch.getRequestHeaders()} and
   * filling in the request headers into the returned array.</li>
   * <li> invoking {@link #invoke ch.invoke()}, and sending
   * the reply headers back to the caller</li>
   * <li> invoking {@link #getData ch.getData()}, that streams
   * any reply data to the caller</li>
   * </ol>
   */

  public interface SynchronousCallHandler {

    /** Get request header storage.  Returns an array of empty header objects
     * (that must match the headers sent by the corresponding
     * {@link SynchronousCallChannel.SynchronousCall} object)
     * that will be used to store the request headers. */

    public Serializable[] getRequestHeaders();

    /** Invoke call and get reply headers.
     * The method is responsible for executing the server-side code of the call
     * and returning the RPC reply headers. The call channel initializes
     * request headers returned by {@link #getRequestHeaders}
     * prior to calling this method.
     *
     * @param requestDataStream data stream from caller
     * @return reply headers as an array of serializable objects
     * @throws IOException if the call invocation fails.
     */

    public Serializable[] invoke( ObjectInputStream requestDataStream )
        throws IOException;

    /** Get reply data.  When invoked, this method must write all
     * reply data to the given output stream.*/
    public void getData(ObjectOutputStream os) throws IOException;
  }


  /** Interface for locating a call handler for a request. */
  public interface CallMultiplexer {
    /** Get call hanlder for request.
     *
     * @param call call name
     * @return hanlder for the call, or <code>null</code> if none
     * available.
     */
    SynchronousCallHandler getHandler( String call );
  }
    
}// arch-tag: 356fcdb89847880e0b41f4c63d944d9e *-
