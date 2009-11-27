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

// $Id: SynchronizationServer.java,v 1.36 2004/12/29 11:49:17 ctl Exp $
// History:
// Code before 2004/01/13 as inner class Syncd in SynchronizationEngine r1.50

package fc.syxaw.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Iterator;

import javax.servlet.http.HttpServletResponse;

import fc.syxaw.api.MetadataImpl;
import fc.syxaw.proto.Version;
import fc.syxaw.protocol.CreateRequest;
import fc.syxaw.protocol.CreateResponse;
import fc.syxaw.protocol.DownloadRequest;
import fc.syxaw.protocol.TransferHeader;
import fc.syxaw.protocol.TransmitStatus;
import fc.syxaw.protocol.UploadRequest;
import fc.syxaw.transport.HTTPCallChannel;
import fc.syxaw.transport.ObjectInputStream;
import fc.syxaw.transport.ObjectOutputStream;
import fc.syxaw.transport.SynchronousCallChannel;
import fc.util.log.Log;

import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;

/** Syxaw synchronization protocol daemon. Answers the Syxaw
 * synchronization protocol requests <code>DOWNLOAD</code>,
 * <code>UPLOAD</code>, and <code>CREATE</code>.
 * Requests are received trough the
 * {@link fc.syxaw.transport.HTTPCallChannel} listener.
 */

public class SynchronizationServer implements
    Runnable, SynchronousCallChannel.CallMultiplexer {

  /** Max number of worker threads. */
  public static final int MAX_WORKERS=
    Integer.getInteger("syxaw.server.workers", 4);
  
  protected LockManager lm;

  /** Create synchronization server.
   *
   * @param lm LockManager for object softlocks
   */
  public SynchronizationServer(LockManager lm) {
    this.lm =lm;
    workers.setMinimumPoolSize(MAX_WORKERS);
  }

  /** Run server. */
  public void run() {
    try {
      fc.syxaw.transport.HTTPCallServer.listen(this);
    } catch( IOException x ) {
      Log.log("Failed to listen on HTTPCallChannel",Log.FATALERROR,x);
    }
  }

  /** Return call handler for a call.
   * @param name name of the call. One of {@link Constants#SYNC_DOWNLOAD},
   * {@link Constants#SYNC_UPLOAD}, {@link Constants#SYNC_CREATE}.
   * @return call handler, or <code>null</code> if no handler available.
   */

  public SynchronousCallChannel.SynchronousCallHandler getHandler(String name) {
    FileTransfer t = FileTransfer.getInstance();
    if( Constants.SYNC_DOWNLOAD.equals(name) )
      return new DownloadRequestResponder(t);
    else if( Constants.SYNC_UPLOAD.equals(name) )
      return new UploadRequestResponder(t);
    else if( Constants.SYNC_CREATE.equals(name) )
      return new CreateResponder();
    return null; // No handler
  }

  /** Responder for the <code>CREATE</code> call in the Syxaw sychronization
   * protocol */

  public class CreateResponder
      implements SynchronousCallChannel.SynchronousCallHandler {

    /** Maximum number of object created in one batch. */
    public final int MAX_BATCH = 16384; // Maximum number of new objects created

    protected CreateRequest request = new CreateRequest();

    CreateResponder() {}

    public Serializable[] getRequestHeaders() {
      return new Serializable[] {request};
    }

    public Serializable[] invoke(ObjectInputStream rs)
        throws IOException {
      // Alloc ids
      int count = Math.max(0, Math.min(request.getObjectCount() ,MAX_BATCH));
      Log.log("CreateObjects, count="+count,Log.INFO);
      String names[] = new String[count];
      for( int i=0;i<names.length;i++) {
        // FIXME: This might create uids differently from the FileSystem implementation. 
        //names[i]=UID.newUID().toBase64();
        names[i] = Syxaw.getStorageProvider().getNextUid().toBase64();
      }
      Log.log("Created UIDs ",Log.INFO,names);
      CreateResponse response = new CreateResponse();
      response.setObjects(names);
      response.setInitialVersion(Constants.FIRST_VERSION
              /* .ZERO_VERSION */);
      return new Serializable[] {response};
    }

    public void getData(ObjectOutputStream os) {
      // NOP
    }

  }

  /** Responder for the <code>DOWNLOAD</code> call in the Syxaw synchronization
   * protocol */

  public class DownloadRequestResponder
      extends HTTPCallChannel.HTTPSynchrounousCallHandler {

    private FileTransfer.UploadTask upload;
    private FileTransfer transfer;

    private DownloadRequest request = new DownloadRequest();
    private TransferHeader th = new TransferHeader();

    DownloadRequestResponder( FileTransfer t ) {
      transfer = t;
    }


    public Serializable[] getRequestHeaders() {
      return new Serializable[] {request,th};
    }

    private FileTransfer.BatchDownloadResponse batchResponse = null;

    public Serializable[] invoke(ObjectInputStream rs,
      javax.servlet.http.HttpServletResponse hr )  {

     if( request.getObjects() != null ) {
       batchResponse = transfer.new BatchDownloadResponse(
           request.getObjects().length);
       for(int i =0;i<request.getObjects().length;i++) {
         Runnable reader =
             new FileReader(request.getObjects()[i],th,batchResponse);
         try { workers.execute(reader); }
         catch (InterruptedException x ) {
           Log.log("Unexpected interrupt",Log.FATALERROR);
         }
       }
       // Reply headers
       return new Serializable[] {
           batchResponse.getTransferHeader(),
           batchResponse.getStatus()};
     }
     FileReader frd = new FileReader(request,th,null);
     upload = (FileTransfer.UploadTask) frd.call();
     // A note about the upload resources: if the code is correct
     // (i.e. no NullptrExes etc), we'll
     // always get to getData() where the upload resources are freed.
     if ( upload == null || upload.getStatus().getStatus() < TransmitStatus.OK) {
       TransmitStatus ts =  upload == null ? frd.status :
               upload.getStatus();
       if( ts.getStatus() == TransmitStatus.NOT_FOUND )
         hr.setStatus(HttpServletResponse.SC_NOT_FOUND);
       else
         hr.setStatus(HttpServletResponse.SC_BAD_REQUEST);
       // Something went wrong in init, return error code
       return new Serializable[] {new TransferHeader(),ts};
     }
     int contentLength = (int) upload.getContentLength();
     if (contentLength != -1)
       hr.setContentLength(contentLength);
     // Report our versions to the client, so he can use them for deltas
     upload.getStatus().setVersionsAvailable(
          Version.getNumbers(upload.history.getFullVersions(Version.CURRENT_BRANCH)));
     return new Serializable[] {
         upload.getTransferHeader(), upload.getStatus()};
   }

    public void getData(ObjectOutputStream os) throws IOException {
      if( batchResponse != null ) {
        batchResponse.getData(os,false);
        Log.log("Batch download reply of " + request.getObjects().length +
                " objects completed.", Log.INFO);
        return;
      } else if ( upload != null ) {
        try {
          upload.getData(os);
        } finally {
          upload.getCompletionHandler().uploadComplete(upload);
        }
      }
    }
  }

  /** Responder for the <code>UPLOAD</code> call in the Syxaw synchronization
   * protocol */

  public class UploadRequestResponder
      implements SynchronousCallChannel.SynchronousCallHandler,
                 FileTransfer.ReceptionHandler,
                 FileTransfer.DownloadCompletionHandler {

    private FileTransfer.BatchUploadResponse batchResponse=null;

    private FileTransfer transfer;
    private TransmitStatus status;

    private UploadRequest request = new UploadRequest();
    private TransferHeader th = new TransferHeader();

    UploadRequestResponder( FileTransfer t ) {
      transfer = t;
      status = new TransmitStatus();
      status.setStatus(TransmitStatus.ERROR);
    }

    //FIXME-W: Better name might be getRequestHeaderStorage. Check usage before
    // renaming though!
    public Serializable[] getRequestHeaders() {
      return new Serializable[] {request,th};
    }

    public Serializable[] invoke(ObjectInputStream rs) throws IOException {
      if (request.getObject() == null) {
        // Batch receive
        status.setStatus(TransmitStatus.SEE_SUBSTATUSES);
        batchResponse = transfer.new BatchUploadResponse(this, this);
        batchResponse.receive(rs, true);
      } else {
        // Single obj receive
        status.setStatus(TransmitStatus.OK);
        //Log.log("===Single ul got lock " + request.getLock(), Log.DEBUG);
        FileTransfer.DownloadRequestBase upload = getReceiver(
            QueriedUID.createFromBase64Q(request.getObject()), request);
        upload.replyHeader = th;
        upload.receive(rs, th);
        downloadComplete(upload);
        TransmitStatus s = upload.getRemoteStatus();
        // Not in single status: setObject(request.getObject());
        status.setStatus(s.getStatus());
        status.setVersion(s.getVersion());
      }
      return new Serializable[] { status};
    }

    public void getData(ObjectOutputStream os) throws IOException {
      if( batchResponse != null ) {
        for( Iterator i=batchResponse.getStatuses().iterator();i.hasNext();) {
          TransmitStatus ts = new TransmitStatus(), s=(TransmitStatus) i.next();
          ts.setObject(s.getObject());
          ts.setStatus(s.getStatus());
          ts.setVersion(s.getVersion());
          ts.setVersionsAvailable(s.getVersionsAvailable());
          os.writeObject(ts);
        }
      }
    }

    public FileTransfer.DownloadRequestBase getReceiver(QueriedUID object,
        UploadRequest ulReq) {
      SyxawFile f = Syxaw.getFile(object);
      if (!f.exists()) {
        return null;
      }
      final LockManager.PrivateLock flock =
          new LockManager.PrivateLock( lm.get(ulReq.getLock(),f) );
      if( flock != null )
        flock.acquireExclusive();
      try {
        return transfer.new DownloadTask(object, ulReq.getData(),
                                         ulReq.getMetadata(),
                                         f.createStorage("upload", true),
                                         f.getVersionHistory(), false, null,
                                         flock);
      } catch( IOException x) {
        Log.log("Error initializing receiver",Log.ERROR,x);
      }
      return null;
    }

    public void downloadComplete(FileTransfer.DownloadRequestBase req) {
      SyxawFile f = Syxaw.getFile(req.getObject());
      BLOBStorage fs = req.getStorage();
      if( req.getStatus().getStatus() > TransmitStatus.ERROR ) {
        LockManager.PrivateLock flock = null;
        try {
          flock = ((FileTransfer.DownloadTask) req).getLock();
          if (flock == null) {
            Log.log("Lock " + req.getRemoteStatus().getLock() + " has expired.",
                    Log.ERROR);
            req.getRemoteStatus().setStatus(TransmitStatus.LOCK_EXPIRED);
            return;
          }
          if (req.getTransferHeader().getEncoding() != TransferHeader.ENC_NONE ) {
            f.rebindStorage(fs);
            fs = null;
          }
          else {
            fs.delete(); // No longer needed
            fs = null;
          }
          if (req.getTransferHeader().getMetaEncoding() != TransferHeader.ENC_NONE ) {
            f.setMetadata(req.getMetadata());

            System.out.flush();
          }
          // FIXME-joins: joins stuff
          
          if( th.getBranchState() == TransferHeader.BS_ON_BRANCH ) {
            // NOTE Here we actually modify the uploaded data; all should turn out
            // well anyway, since the client will do the same modification!
            // Doing it in advance is another option, which requires the
            // client to know which linkver the server will hand out...
            MetadataImpl jmd = MetadataImpl.createFrom( f.getMetadata() );
            jmd.setJoins(
                    new Version( GUID.getCurrentLocation(), f.getLinkDataVersion(),//FIXME-versions:
                                 f.getNextVersion()).addToJoins(jmd.getJoins()));
            Log.log("Upload: Updating joins to "+jmd.getJoins(),Log.INFO);
            f.setMetadata(jmd);
          }
          
          f.FIXME_COMMIT_RECEIVED_NOT_CURRENT = true;
          FullMetadata md = f.commit(true, true); // Commit newly uploaded data
          req.getRemoteStatus().setStatus(TransmitStatus.OK);
          req.getRemoteStatus().setVersion(md.getDataVersion());
        }
        catch (IOException x) {
          Log.log("Batch u/l receive failed for "+req.getObject(), Log.ERROR, x);
          req.getRemoteStatus().setStatus(TransmitStatus.ERROR);
        }
        finally {
          f.FIXME_COMMIT_RECEIVED_NOT_CURRENT = false;
          if( fs != null )
            fs.delete();
          if (flock != null) {
            flock.release();
          }
        }
      }
      Log.log("Finished processing " + req, Log.INFO);
      Log.log("Receive done for "+req.getObject(), Log.INFO);
    }
  }
  private static int __fr_instcnt=0;

  private class FileReader implements Runnable, FileTransfer.UploadCompletionHandler {

    final int __inst = ++__fr_instcnt;

    protected DownloadRequest request;
    protected TransferHeader th;
    protected TransmitStatus status = new TransmitStatus();
    protected InputStream uploadStream=null; // Also set to null if file read
                                             // init failed
    protected SyxawFile.Lock flock = null;

    protected FullMetadata md = null;

    FileTransfer.BatchDownloadResponse bdr=null;

    public FileReader(DownloadRequest aRequest, TransferHeader aTh,
                      FileTransfer.BatchDownloadResponse bdr) {
      request = aRequest;
      th = aTh;
      this.bdr = bdr;
    }


    /**
     * call
     *
     * @return Object
     */

    public Object call() {
//      Log.log("=== called reader "+__inst,Log.INFO);
      status.setStatus(TransmitStatus.ERROR);
      fc.syxaw.fs.SyxawFile file=null;
      QueriedUID uid = null;
      try {
        Log.log("Beginning batch download request "+__inst+": ",Log.INFO,request);
        try {
          uid = QueriedUID.createFromBase64Q(request.getObject());
          Log.log("Queried UID: " + uid, Log.DEBUG);
          file = Syxaw.getFile(uid);
        } catch( Exception x ) {
          Log.log("Illegal requested object: "+request.getObject(),Log.ERROR);
        }
        if (file==null || !file.exists()) {
          status.setStatus(TransmitStatus.NOT_FOUND);
          // BUGFIX-080404-2: Throw correct excpetion when file not found
          throw new FileNotFoundException("Can't find "+file);
        }
        final boolean fullLockRelease = !request.getLockRequested();
        final LockManager.Lock fileLock = lm.newLock(file);
        final Object flockMex=fileLock.acquireExclusive();
        flock = new SyxawFile.Lock() {
          public void release() {
            if( fullLockRelease )
              fileLock.release(flockMex);
            else
              fileLock.releaseExclusive(flockMex);
          }
        };
        // FIXME-joins: joins stuff
        
        if( th.getBranchState() == TransferHeader.BS_ON_BRANCH ) {
          MetadataImpl jmd = MetadataImpl.createFrom( file.getMetadata() );
          Version requiredJoin = new Version(GUID.getCurrentLocation(),
                                             file.getLinkDataVersion(),//FIXME-versions:
                                             file.getCommitVersion());
          String newJoins = requiredJoin.addToJoins(jmd.getJoins());
          if( !newJoins.equals(jmd.getJoins()) ) {
            // Joins need update
            requiredJoin.setNumber(file.getNextVersion());
            jmd.setJoins(requiredJoin.addToJoins(jmd.getJoins()));
            Log.log("Download: updating joins to "+jmd.getJoins(),Log.INFO);
            file.setMetadata(jmd);
          } else
            Log.log("Download: joins already has "+requiredJoin,Log.DEBUG);
        }

        boolean taskOk = false;
        try {
          md = file.commit(true, true); // Make sure we have a valid (local)version before sending
          // the file MUST NOT be touched from here to end of transmission
          uploadStream = file.getInputStream();
          VersionHistory history = file.getVersionHistory();
          //Log.log("====Sender has versions: " + request.getVersionsAvailable(),Log.INFO );
          FileTransfer.UploadTask task = FileTransfer.getInstance().
                  new UploadTask(uid, uploadStream, md, history,
                                 md.getDataVersion(), request.getVersionsAvailable(),
                                 request.getData(), request.getMetadata(), this,
                                 flock,
                                 request.getLockRequested() ?
                                 fileLock.getToken() : null);

          taskOk = true;
          return task;
        } finally  {
          if( !taskOk )
            flock.release();
        }
      } catch (Exception x) {
        Log.log("Dlreqresp init failed for "+file,Log.ERROR,x);
        if( x instanceof FileNotFoundException )
          status.setStatus(TransmitStatus.NOT_FOUND);
        else
          status.setStatus(TransmitStatus.ERROR);
        uploadStream = null; //FIXME-W no close if != null?
      }
      return null;
    }

    public void uploadComplete(FileTransfer.UploadRequestBase req) {
      //Log.log("=== Freeing dl resp locks",Log.DEBUG);
      FileTransfer.UploadTask ut = (FileTransfer.UploadTask) req;
      try {
        if( ut.getFileLock()  != null )
          ut.getFileLock().release();
      } finally {
        if( ut.getStorageStream() != null )
          try { ut.getStorageStream().close(); }
          catch( IOException x) {Log.log("Can't close",Log.FATALERROR,x);}
      }
    }

    public void run() {
      bdr.enqueue((FileTransfer.UploadTask) call());
    }
  }

  private PooledExecutor workers = new PooledExecutor(
      new LinkedQueue(), MAX_WORKERS );

}


// arch-tag: 693985c98d7f1a021635731f84539ace *-
