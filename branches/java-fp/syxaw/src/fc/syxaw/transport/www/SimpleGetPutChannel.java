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

package fc.syxaw.transport.www;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import EDU.oswego.cs.dl.util.concurrent.Semaphore;

import fc.syxaw.api.MetadataImpl;
import fc.syxaw.api.StatusCodes;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.QueriedUID;
import fc.syxaw.protocol.DownloadRequest;
import fc.syxaw.protocol.TransferHeader;
import fc.syxaw.protocol.TransmitStatus;
import fc.syxaw.protocol.UploadRequest;
import fc.syxaw.transport.ObjectInputStream;
import fc.syxaw.transport.ObjectOutputStream;
import fc.syxaw.transport.SynchronousCallChannel;
import fc.syxaw.util.Util;
import fc.util.IOUtil;
import fc.util.StringUtil;
import fc.util.io.DelayedInputStream;
import fc.util.io.DelayedOutputStream;
import fc.util.log.Log;

public class SimpleGetPutChannel extends SynchronousCallChannel {
  
  private static final char[] CT_DELIMS = {';'};
  
  public static final String URI = "simple-test.www-gw.syxaw.hiit.fi";
  
  public SimpleGetPutChannel(String dest) {
    super(dest);
  }

  @Override
  public ObjectInputStream call(String name, SynchronousCall call,
      Serializable[] replyHeaders) throws IOException {
    Log.debug("Call=", name);
    if (Constants.SYNC_DOWNLOAD.equals(name)) {
      return download(call, replyHeaders);
    } else if (Constants.SYNC_UPLOAD.equals(name)) {
      return upload(call, replyHeaders);
    }
    throw new IOException("Unsupported method: "+name);
  }

  private ObjectInputStream upload(SynchronousCall call, 
      Serializable[] replyHeaders) throws IOException {
    Object[] callHeaders = call.getRequestHeaders();
    UploadRequest rq = (UploadRequest) callHeaders[0];
    TransferHeader th = (TransferHeader) callHeaders[1];
    QueriedUID obj = QueriedUID.createFromBase64Q(rq.getObject());
    String urlStr = obj.getQuery();
    Log.debug("Uploading to",urlStr);
    URL url = new URL(urlStr);
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod("PUT");
    conn.setDoOutput(true);
    
    final Semaphore sendDone = new Semaphore(0); 
    OutputStream dos = new DelayedOutputStream() {
      @Override
      protected void stream(InputStream in) throws IOException {
        try {
          ObjectInputStream ois = new ObjectInputStream(in);
          MetadataImpl md = new MetadataImpl();
          ois.readObject(md);
          OutputStream dout = conn.getOutputStream();
          int len = IOUtil.copyStream(ois.readSubStream(), dout);
          Log.debug("Copied byte count",len);
          dout.flush();
          conn.connect();
        } finally {
          sendDone.release();
        }
      }      
    };
    call.getData(new ObjectOutputStream(dos));
    // Wait for http upload thread to finish...
    try {
      sendDone.acquire();
    } catch (InterruptedException ex)  {
      // NOP
    }
    TransmitStatus repTs = (TransmitStatus) replyHeaders[0];
    repTs.setStatus(conn.getResponseCode() == 204 ? TransmitStatus.OK 
        : TransmitStatus.ERROR);
    int version = (int) (conn.getDate()/1000);
    repTs.setVersion(version);
    repTs.setVersionsAvailable(new int[] {version});
    return new ObjectInputStream(new Util.EmptyIn());
  }

  private ObjectInputStream download(SynchronousCall call,
      Serializable[] replyHeaders) throws IOException {
    Object[] callHeaders = call.getRequestHeaders();
    DownloadRequest rq = (DownloadRequest) callHeaders[0];
    TransferHeader th = (TransferHeader) callHeaders[1];
    QueriedUID obj = QueriedUID.createFromBase64Q(rq.getObject());
    String urlStr = obj.getQuery();
    Log.debug("Downloading from",urlStr);
    URL url = new URL(urlStr);
    final URLConnection conn = url.openConnection();
    int modTime = (int) (conn.getLastModified()/1000);
    int version = (int) (conn.getLastModified()/1000); 
    TransferHeader repTh = (TransferHeader) replyHeaders[0];
    TransmitStatus repTs = (TransmitStatus) replyHeaders[1];
    
    repTh.setAcceptedEncodings(new int[] {TransferHeader.ENC_BINARY});
    repTh.setEncoding(TransferHeader.ENC_BINARY);
    //repTh.setHash();
    repTh.setMetaEncoding(TransferHeader.ENC_BINARY);
    repTh.setSizeEstimate(-1l); // Laziness...
    
    repTs.setStatus(StatusCodes.OK);
    repTs.setVersion(version);
    repTs.setVersionsAvailable(new int[] {version});
    
    final MetadataImpl md = new MetadataImpl();
    md.setMetaModTime(modTime);
    md.setModTime(modTime);
    if (conn.getContentType() != null) {
      md.setType(StringUtil.splitWords(conn.getContentType(),true,CT_DELIMS)[0]);
    }
    md.setReadOnly(false);
    
    InputStream dis = new DelayedInputStream() {
      @Override
      protected void stream(OutputStream out) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(out);
        oos.writeObject(md);
        ObjectOutputStream data = 
          oos.writeSubStream("data", Constants.NO_SIZE, true);
        IOUtil.copyStream(conn.getInputStream(),data);
        data.close();
        oos.close();
      }
    };
    return new ObjectInputStream(dis); 
  }

}
// arch-tag: f71d272e-401a-4373-862a-f99fc8e1d3ee
//
