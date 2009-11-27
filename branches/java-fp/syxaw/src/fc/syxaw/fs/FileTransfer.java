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

// $Id: FileTransfer.java,v 1.61 2005/01/20 10:59:07 ctl Exp $
// NOTE: This code was moved from BinaryTransfer.java in rev BinaryTransfer.java,v 1.24

package fc.syxaw.fs;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import fc.raxs.DeltaStream;
import fc.syxaw.api.Metadata;
import fc.syxaw.api.MetadataImpl;
import fc.syxaw.codec.CodecException;
import fc.syxaw.codec.DeltaDecoder;
import fc.syxaw.codec.DeltaEncoder;
import fc.syxaw.codec.Encoder;
import fc.syxaw.codec.VersionRefDecoder;
import fc.syxaw.codec.VersionRefEncoder;
import fc.syxaw.proto.Version;
import fc.syxaw.protocol.CreateRequest;
import fc.syxaw.protocol.CreateResponse;
import fc.syxaw.protocol.TransferHeader;
import fc.syxaw.protocol.TransmitStatus;
import fc.syxaw.transport.ChannelProvider;
import fc.syxaw.transport.HTTPCallChannel;
import fc.syxaw.transport.ObjectInputStream;
import fc.syxaw.transport.ObjectOutputStream;
import fc.syxaw.transport.SynchronousCallChannel;
import fc.syxaw.util.Util;
import fc.util.log.Log;

import EDU.oswego.cs.dl.util.concurrent.BoundedChannel;
import EDU.oswego.cs.dl.util.concurrent.BoundedLinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.FutureResult;
import EDU.oswego.cs.dl.util.concurrent.Semaphore;

/** File transfer engine.
 * Provides methods for (batch) downloading and uploading of objects,
 * as well as batch creation of objects on a remote device. Encapsulates
 * encoding and decoding of data, as well as processing of transfer
 * headers.
 */

public class FileTransfer {

  private static FileTransfer instance = new FileTransfer();

  protected List queuedDownloads = null; // List of queued downloads
  protected BatchUploadRequest activeBatchUpload = null; // Active batch upload
  protected boolean batchUploads = false; // True if uploads should be batched

  private Semaphore _dl_batch_done = null;
  
  private FileTransfer() {
  }

  /** Get instance of file transfer engine */
  public static FileTransfer getInstance() {
    return instance;
  }

  /** Encodings accepted by the transfer engine. */
  // FIXME-W: We do not actually check that the chosen encoding
  // is valid for the peer.
  public static final int[] ACCEPTED_ENCODINGS = TransferHeader.ALL_ENCODINGS;

  /** Create objects on remote device. Guaranteed to get the
   * number of objects requested. Implements a developers API to the
   * <code>CREATE_OBJECTS</code> synchronization protocol call.
   * <p><b>Synchronization protocol RPC fomat</b>
   * <p>RPC request format<br>
   * header objects: <code>{@link fc.syxaw.protocol.CreateRequest
   * CreateRequest}</code><br>
   * data: -
   * <p>RPC response format<br>
   * header objects: <code>{@link fc.syxaw.protocol.CreateResponse
   * CreateResponse}</code><br>
   * data: -
   * @param dest location id on which the objects are created
   * @param acount the number of objects to create
   */

  public CreateResponse createObjects( String dest, int acount ) throws IOException {
    final int count = acount;
    SynchronousCallChannel ch = ChannelProvider.getChannel(dest);
    CreateResponse response = new CreateResponse();
    ObjectInputStream rs = ch.call(Constants.SYNC_CREATE,
      new SynchronousCallChannel.SynchronousCall() {
      public Serializable[] getRequestHeaders() throws IOException {
        return new Serializable[] {new CreateRequest(count) };
      }

      public void getData(ObjectOutputStream os) throws IOException {
        // NOP
      }

    }, new Serializable[] {response});
    if( response.getObjects() == null || response.getObjects().length <count )
      Log.log("Didn't get all requested objs; this implementation does not re-request--giving up",Log.FATALERROR);
    return response;
  }


  /** Download an object from a remote device.
   * Implements a developers API to the
   * <code>DOWNLOAD</code> synchronization protocol call. Metadata, possible
   * reference to a version in <code>objectVersions</code>, as well as
   * other information about the downloaded object is returned as a
   * {@link FileTransfer.DownloadRequestBase} object. This same method is used
   * to add an object for download to an in-progress batch transfer
   * (see {@link #startDownloadBatch startDownloadBatch}). The method
   * may be called asynchronosuly by setting the <code>completionHandler</code>
   * parameter.
   *
   * <p><b>Synchronization protocol RPC format for single object downloads</b>
   * <p>RPC request format<br>
   * header objects: <code>{{@link fc.syxaw.protocol.DownloadRequest
   * DownloadRequest},{@link fc.syxaw.protocol.TransferHeader
   * TransferHeader}}</code><br>
   * data: -
   * <p>RPC response format<br>
   * header objects: <code>{{@link fc.syxaw.protocol.TransferHeader
   * TransferHeader},{@link fc.syxaw.protocol.TransmitStatus
   * TransmitStatus}}</code><br>
   * data: the metdata, in a format specified by the encoding (if requested),
   * followed by the data (if requested). See the classes in the
   * {@link fc.syxaw.codec} for a description of encodings.
   *
   * <p><b>Synchronization protocol RPC format for batch object downloads</b>
   * <p>RPC request format<br>
   * header objects: <code>{{@link fc.syxaw.protocol.DownloadRequest
   * DownloadRequest},
   * {@link fc.syxaw.protocol.TransferHeader TransferHeader}}</code>
   * The <code>objects</code>
   * field of the <code>DownloadRequest</code> is set to an array
   * of DownloadRequests, where each element contains a single object request.
   * The <code>TransferHeader</code> is ignored (reserved for future use).
   * <br>
   * data: -
   * <p>RPC response format<br>
   * header objects: <code>{{@link fc.syxaw.protocol.TransferHeader
   * TransferHeader},{@link fc.syxaw.protocol.TransmitStatus
   * TransmitStatus}}</code>. The transfer header is ignored and the transmit
   * status either encodes a failure code for the whole batch or the code
   * {@link fc.syxaw.protocol.TransmitStatus#SEE_SUBSTATUSES
   * SEE_SUBSTATUSES}<br>
   * data: for each requested object the block <code>
   * {{@link fc.syxaw.protocol.TransmitStatus
   * TransmitStatus},{@link fc.syxaw.protocol.TransferHeader
   * TransferHeader},[metadata],[data_substream]}</code>. The blocks are
   * sent without any separator and in an arbitrary order.
   * The fields of the block correspond to the same fields in the single
   * object response, with the addition that the <code>object</code> field of the
   * <code>TransmitStatus</code> is filled in (so as to indicate which object the block
   * encodes) and the use of substream format for the data (see
   * {@link fc.syxaw.transport.ObjectOutputStream}).
   *
   *
   * @param object name of object to download
   * @param storage output stream to store downloaded data in. If a reference to a
   * version in <code>objectVersions</code> is obtained, no data is stored in this stream.
   * @param objectVersions version history for <code>object</code>. Used
   *        when choosing an appropriate transfer encoding.
   * @param requestLock set to request a lock token for the object
   *  (typically set if the object has local modifications)
   * @param completionHandler handler to call on download completion. If a
   * non-null handler is given, <code>download</code> returns immediately,
   * rather than waiting for the response.
   * @return atatus of the completed download, or <code>null</code> if the
   * method was called asynchronously.
   */
  public synchronized DownloadRequestBase download( GUID object,
                                   BLOBStorage storage,
                                   VersionHistory objectVersions,
                                   boolean requestLock,
                                   DownloadCompletionHandler completionHandler, boolean getData, boolean getMetadata )
                           throws IOException {
    if( completionHandler != null && queuedDownloads != null ) {
      // Async call, but only if a batch is started!
      queuedDownloads.add(new DownloadTask(object.getQueriedUId(),
                                           getData, getMetadata, storage,
                                           objectVersions, requestLock,
                                           completionHandler, null));
      return null;
    }

    // Download (which is "stat" at the same time)
    FileTransfer.DownloadRequest dlRequest = null;
    try {
      dlRequest = new DownloadRequest(object, storage, objectVersions,
                                      requestLock);
      TransmitStatus status = dlRequest.download();
      if( status.getStatus() == TransmitStatus.OK ) {
      } else {
        Log.log("Download failed, status code = "+status.getStatus(),Log.ERROR);
        return dlRequest;
      }
      Log.log("Download done, stat=",Log.INFO,status);
      Log.log("Transfer header=",Log.INFO,dlRequest.getTransferHeader());
    } catch( IOException ex) {
      throw ex;
    } catch (Exception x) {
      Log.log("D/l req unexpected exception", Log.ERROR,x);
      dlRequest.getStatus().setStatus(TransmitStatus.ERROR);
    } finally {
      if( completionHandler != null )
        completionHandler.downloadComplete(dlRequest);
    }
    return dlRequest;
  }

  /** Download task. Encapsulates a request as well as some additional
   * resources, like file locks.
   */

  public class DownloadTask extends DownloadRequestBase {

    private DownloadCompletionHandler ch;
    private LockManager.PrivateLock lock;

    /** Create a new task.
     *
     * @param object object to download
     * @param aStorage BLOBStorage to store downloaded data
     * @param aHistory VersionHistory of object
     * @param requestLock <code>true</code> if a lock should be requested
     * @param ch handler for completed download
     * @param lock resource lock
     */
    public DownloadTask(QueriedUID object,
                        boolean getData,
                        boolean getMeta,
                        BLOBStorage aStorage,
                        VersionHistory aHistory,
                        boolean requestLock,
                        DownloadCompletionHandler ch,
                        LockManager.PrivateLock lock) {
      super(object, getData, getMeta, aStorage, aHistory, requestLock);
      this.ch = ch;
      this.lock = lock;
    }

    public DownloadCompletionHandler getCh() {
      return ch;
    }

    public LockManager.PrivateLock getLock() {
      return lock;
    }
  }

  /** Initiate download batching. Calls to <code>download</code> between
   * the invocation of <code>startDownloadBatch</code> and
   * <code>completeDownloadBatch</code> are aggregated to form download
   * batches.
   */

  public void startDownloadBatch() {
    if( queuedDownloads != null )
      throw new IllegalStateException("Batch download already initiated");
    queuedDownloads = new LinkedList();
    if( _dl_batch_done != null )
      _dl_batch_done.release();
    _dl_batch_done = new Semaphore(0);
  }

  /** Initiate upload batching. Calls to <code>upload</code> between
   * the invocation of <code>startUploadBatch</code> and
   * <code>completeUploadBatch</code> are aggregated to form upload
   * batches.
   */
  public void startUploadBatch() {
    batchUploads = true;
  }

  /** Complete upload batching. Calls to <code>upload</code> between
   * the invocation of <code>startUploadBatch</code> and
   * <code>completeUploadBatch</code> are aggregated to form upload
   * batches.
   */
  public void completeUploadBatch() {
    batchUploads = false;
  }

  /** Complete download batching. Calls to <code>download</code> between
   * the invocation of <code>startDownloadBatch</code> and
   * <code>completeDownloadBatch</code> are aggregated to form download
   * batches.
   *
   * @param location target loaction of batch
   * @throws IOException if an I/O error occurs
   */

  public void completeDownloadBatch(String location) throws IOException {
    try {
      if (queuedDownloads == null)
        throw new IllegalStateException("No batch download initiated");
      // Put fancy thread pool stuff here
      BatchDownloadRequest rq = new BatchDownloadRequest(location);
      rq.setObjects(queuedDownloads);
      rq.download();
    } finally {
      queuedDownloads = null; // No batch active
      _dl_batch_done.release();
    }
  }

  protected void completeUploadBatch(String location, List requests) {

  }

  /** Interface for obatining object receive resources. */
  public interface ReceptionHandler {
    /** Called when resources for object reception are needed.
     *
     * @param object UID of object being received
     * @param ulReq UploadRequest associated with the object
     * @return DownloadRequestBase object holding resources for object reception,
     * or <code>null</code> if the objet is not desired.
     */
    public DownloadRequestBase getReceiver(QueriedUID object,
                                           fc.syxaw.protocol.UploadRequest
                                           ulReq);
  }

  /** Handler for completed downloads. */
  public interface DownloadCompletionHandler {
    /** Method called on completed download.
     *
     * @param req DownloadRequestBase for the download
     */
    public void downloadComplete( DownloadRequestBase req );
  }

  /** Handler for completed uploads.
   */
  public interface UploadCompletionHandler {
    /** Method called on completed uploads.
     *
     * @param req UploadRequestBase for the upload
     */
    public void uploadComplete( UploadRequestBase req );
  }

  /**  Upload an object to a remote device.
   * Implements a developers API to the
   * <code>UPLOAD</code> synchronization protocol call.
   * Information about the upload status is returned as a
   * {@link FileTransfer.UploadRequestBase} object. This same method is used
   * to add an object for upload to an in-progress batch transfer
   * (see {@link #startUploadBatch startUploadBatch}). The method
   * may be called asynchronosuly by setting the <code>completionHandler</code>
   * parameter.
   *
   * <p><b>Synchronization protocol RPC format for single object uploads</b>
   * <p>RPC request format<br>
   * header objects: <code>{{@link fc.syxaw.protocol.UploadRequest
   * UploadRequest},{@link fc.syxaw.protocol.TransferHeader
   * TransferHeader}}</code><br>
   * data: the metdata, in a format specified by the encoding (if transmitted),
   * followed by the data (if transmitted). See the classes in the
   * {@link fc.syxaw.codec} for a description of encodings.
   * <p>RPC response format<br>
   * header objects: <code>{{@link fc.syxaw.protocol.TransmitStatus
   * TransmitStatus}}</code><br>
   * data: -
   *
   * <p><b>Synchronization protocol RPC format for batch uploads</b>
   * <p>RPC request format<br>
   * header objects: <code>{{@link fc.syxaw.protocol.UploadRequest
   * UploadRequest},{@link fc.syxaw.protocol.TransferHeader
   * TransferHeader}}</code>. Ignored and reserved for future use<br>
   * data: for each transmitted object the block <code>
   * {{@link fc.syxaw.protocol.UploadRequest
   * UploadRequest},{@link fc.syxaw.protocol.TransferHeader
   * TransferHeader},[metadata],[data_substream]}</code>. The blocks are
   * sent without any separator and in an arbitrary order.
   * The fields of the block correspond to the same fields in the single
   * object case, with the addition that the substream format
   * is used for the data (see
   * {@link fc.syxaw.transport.ObjectOutputStream}).
   *
   * <p>RPC response format<br>
   * header objects: <code>{{@link fc.syxaw.protocol.TransmitStatus
   * TransmitStatus}}</code>. The transmit
   * status either encodes a failure code for the whole batch or the code
   * {@link fc.syxaw.protocol.TransmitStatus#SEE_SUBSTATUSES
   * SEE_SUBSTATUSES}<br>
   * data: a list (no separarors) of
   * <code>{@link fc.syxaw.protocol.TransmitStatus
   * TransmitStatus}</code> objects. Each object encodes the transmit status
   * for an uploaded object. The statuses are used as in the single object
   * reply header, with the addition that the <code>object</code> field is set.
   *
   * @param object object to upload
   * @param storage object data
   * @param md object metadata
   * @param sendData if <code>true</code>, upload data
   * @param sendMeta if <code>true</code>, upload metadata
   * @param history version history of the object. Used
   *        when choosing an appropriate transfer encoding.
   * @param objectVersion the current version number of <code>object</code>
   * @param peerVersionsAvailable array of versions of the object available
   *        on the receiving side.  Used when choosing an appropriate transfer
   *        encoding.
   * @param lockToken lock token for the objects, as returned by
   *  dowload, or <code>null</code> if no token.
   * @param completionHandler handler called on upload completion
   * @return upload status, or <code>null</code> if called asynchronously
   */

  public UploadRequestBase upload( GUID object,
                               InputStream storage,
                               FullMetadata md,
                               boolean sendData,
                               boolean sendMeta,
                               VersionHistory history,
                               int objectVersion,
                               int[] peerVersionsAvailable,
                               String lockToken,
                               UploadCompletionHandler completionHandler ) {
    UploadRequestBase request=null;
    try {
      if (completionHandler != null && batchUploads) {
        UploadTask lrequest =
            new UploadTask( object.getQueriedUId(), storage,
                                       md,
                                       history,
                                       objectVersion,
                                       peerVersionsAvailable,
                                       sendData,
                                       sendMeta,completionHandler,null,lockToken);
        storage = null; // Don't close here
        synchronized(this) { // Init batch upload if none active
          if( activeBatchUpload == null || !activeBatchUpload.inProgress() ) {
            Log.log("New upload batch", Log.INFO);
            activeBatchUpload = new BatchUploadRequest(object.getLocation(),
                Collections.EMPTY_LIST);
            (new Thread() {
              public void run() {
                if( SynchronizationEngine.MAX_WORKERS < 0 
                    && queuedDownloads != null) {
                  long wait = System.currentTimeMillis();
                  Log.warning("1-Thread limitation detected; upload waits...");
                  try {
                    _dl_batch_done.acquire();
                    Log.info("Upload got to go after ms= "+(-wait+System.currentTimeMillis()));
                  } catch (InterruptedException e) {
                    Log.error(e);
                  }
                }
                try{
                  activeBatchUpload.upload();
                } catch (IOException x) {
                  Log.log("Batch upload threw ", Log.ERROR, x);
                }
              }
            }).start();
          }
        }
        activeBatchUpload.add((UploadTask) lrequest);
      } else {
        UploadRequest lrequest =
            new UploadRequest( object, storage,
                                       md,
                                       history,
                                       objectVersion,
                                       peerVersionsAvailable,
                                       sendData,
                                       sendMeta, lockToken);

        request = lrequest;
        //Log.log("Single obj upload of "+object.getUId(),Log.DEBUG);
        try {
          lrequest.upload();
        } finally {
          if( completionHandler != null )
            completionHandler.uploadComplete(request);
        }
        return request;
      }
    } catch (IOException x) {
      Log.log("Upload request excepted", Log.ERROR, x);
      if( request != null )
        request.getStatus().setStatus(TransmitStatus.ERROR);
    } finally {
        try {
          if( storage != null )
            storage.close();
        }
        catch (IOException x) {
          Log.log("Can't close storage stream", Log.FATALERROR, x);
        }
    }
    return request;
  }

  /** Class handling data and metadata transmission. */
  protected class Transmitter {

    protected long contentLength;
    protected TransferHeader header=null;
    protected FullMetadata md;
    protected fc.syxaw.protocol.DownloadRequest request;
    protected VersionHistory history;

    protected MetadataEncoder me=null;
    protected DataEncoder de=null;
    protected InputStream storageStream=null;

    protected Transmitter( fc.syxaw.protocol.DownloadRequest aRequest,
                        InputStream aUploadStream, FullMetadata aMd,
                        VersionHistory aHistory,
                        int objectVersion ) throws IOException {
        request = aRequest;
        history = aHistory;
        md = aMd;
        storageStream=aUploadStream;
        init(aUploadStream,objectVersion);
    }

    public InputStream getStorageStream() {
      return storageStream;
    }

    public long getContentLength() {
      return contentLength;
    }

    protected TransferHeader getTransferHeader()  {
      return header;
    }


    protected int getVerRef( int currentVer, int prevChangeVer, int[] availVers ) {
      if (currentVer == Constants.NO_VERSION ||
          prevChangeVer == Constants.NO_VERSION)
        // BUGFIX 05-08-12: no prevChangeRev treated as *unknown* rather than
        // equality for all lower revs.
        return Constants.NO_VERSION;
      // We can always refer to the 0-byte (first) version
      if( currentVer == Constants.ZERO_VERSION )
        return Constants.ZERO_VERSION;

      if( Util.arrayLookup(availVers,Constants.NO_VERSION,
                           Constants.NO_VERSION+1) != -1 )
        Log.log("Client claims to have \"NO_VERSION\" available",Log.ERROR);
      int acceptStart =  prevChangeVer +1;
      int ix = Util.arrayLookup(availVers,acceptStart,currentVer+1,
                                Constants.NO_VERSION);
      return ix == -1 ? Constants.NO_VERSION : availVers[ix];
    }

    // Determines transferHeader from request & available versions + md
    protected void init(InputStream uploadStream, int objectVersion)
        throws IOException {
      header = new TransferHeader();
      if( uploadStream == null ) {
        contentLength = -1;
        return;
      }
      long encodedSize=0;
      if( request.getMetadata() ) {
        int metaRef = getVerRef(objectVersion,
                            history.getPreviousMetadata(objectVersion),
                            request.getVersionsAvailable());
        me = new MetadataEncoder(md,metaRef);
        header.setMetaEncoding(me.getEncoding());
        encodedSize = me.getEncodedSize();
      }
      if( request.getData() ) {
        int dataRef = getVerRef(objectVersion,
                            history.getPreviousData(objectVersion),
                            request.getVersionsAvailable());
        de = new DataEncoder(uploadStream,md.getType(),
                             dataRef,history,request.getVersionsAvailable(),
                             md.getLength());
        header.setHash(md.getHash());
        header.setEncoding(de.getEncoding());
        header.setSizeEstimate(dataRef != Constants.NO_VERSION ?
                                 Constants.NO_SIZE:md.getLength());
        long dataEncSize = de.getEncodedSize();
        if( encodedSize != -1l )
          encodedSize = dataEncSize > -1l ? encodedSize + dataEncSize : -1l;
      }
      header.setBranchState( history.onBranch() ? TransferHeader.BS_ON_BRANCH : 0 );
      contentLength = encodedSize;
    }

    protected void getData(ObjectOutputStream os) throws IOException {
      if( me != null )
        me.write(os);
      if( de != null )
        de.write(os);
    }

  }

  /** Batch transmitter. Support enqueuing objects for transmission while
   * transmission is in progress. */
  protected abstract static class BatchTransmitter {

    int promised = 0;

    final static TransferHeader header = new TransferHeader(); // Empty top-level transfer header
    final static TransmitStatus status = new TransmitStatus(
        TransmitStatus.SEE_SUBSTATUSES, Constants.NO_VERSION);

    protected boolean inProgress = true;
    protected int _sentItems = 0; // For stats only

    /** Size of object queue. This is the number of objects that may be
     * in preparation for transmission (streams being opened etc.) before
     * actually being streamed.
     */

    public static final int SEND_QUEUE = 10;

    /** Batch transmission close window. This is the time in milliseconds
     * the transmitter will wait after the last object
     * for an object to piggyback to the transmission.
     */
    public static final int ITEM_WAIT = 500; // Msec to wait for item before upload close

    /** Batch transmission idle close time. This is the time in milliseconds
     * the transmitter will wait for outstading objects to transmit.
     */

    public static final int PROMISED_WAIT = 60000; // Msec to wait for item before
                                                   // uload close when items promised

    BoundedChannel uploadQueue = null; // Queue of UlReqBase (optionally encapsulated in FutureResult)

    /** Create transmitter.
     *
     * @param initQueue Initial transmission queue. See {@link #enqueue}.
     */
    protected BatchTransmitter(List initQueue) {
      BoundedLinkedQueue queue = new BoundedLinkedQueue(
          Math.max(initQueue.size(),SEND_QUEUE));
      for( Iterator i = initQueue.iterator();i.hasNext();)
          try { queue.put(i.next()); }
          catch( InterruptedException x) {} //Empty exhandler OK
      uploadQueue = queue;
    }

    /** Enqueue object to transmit. */
    protected void enqueue( UploadRequestBase request) {
      try { uploadQueue.put(request); }
      catch( InterruptedException x) {} //Empty exhandler OK
    }

    /** Length of transmission.
     *
     * @return -1L (unknown)
     */
    public long getContentLength() {
      return -1L;
    }

    /** Test if transmission is in progress.
     *
     * @return <code>true</code> if a transmission is in progress
     */
    public boolean inProgress() {
      return inProgress;
    }

    /** Get transfer header. Not used.
     *
     * @return TransferHeader
     */
    public TransferHeader getTransferHeader() {
      return header;
    }

    /** Get aggregate status.
     *
     * @return TransmitStatus aggregate status of batch
     */
    public TransmitStatus getStatus() {
      return status;
    }

    /** Handler for transmitted objects. Gets called when an object has
     * been transmitted.
     * @param sent UploadRequestBase object from the send queue
     */
    protected abstract void handleTransmitted(UploadRequestBase sent);

    /** Transmit object batch.
     * @param os stream to send batch to
     * @param inUploadSender set to <code>true</code> if in sender of batch
     * upload, <code>false</code> if in responder to batch download
     * @throws IOException if an I/O error occurs
     */
    protected void getData(ObjectOutputStream os, boolean inUploadSender) throws
        IOException {
      try {
        for (Object item = null; (item = uploadQueue.poll(promised-- > 0 ?
            PROMISED_WAIT : ITEM_WAIT)) != null; ) {
          if( item instanceof FutureResult )
            item=((FutureResult) item).get();          
          UploadRequestBase subReq = (UploadRequestBase) item;
          Log.log("Uploading " +  subReq.getObject(), Log.INFO);
          TransmitStatus subStatus=subReq.getStatus();
          subStatus.setObject(subReq.getObject().toBase64Q());
          if( subReq.getStatus().getStatus() >= TransmitStatus.OK ) {
            if( inUploadSender )
              os.writeObject(subReq.getRequestHeader());
            else
              os.writeObject(subStatus);
            os.writeObject(subReq.getTransferHeader());
            subReq.getData(os);
            _sentItems++;
          }
          handleTransmitted(subReq);
        }
        Log.log("Batch upload complete.", Log.INFO);
      } catch (java.lang.reflect.InvocationTargetException x1) {
        Log.log("Can't get future value",Log.ASSERTFAILED);
      } catch (InterruptedException ex) {
        Log.log("Abnormally interrupted",Log.ERROR);
      } finally {
        boolean oldIp = inProgress;
        inProgress = false;
        if( oldIp && uploadQueue.peek() != null ) {
          Log.log("Handling unlikely case of sneaked-in upload",Log.WARNING);
          getData(os,inUploadSender);
        }
      }
    }
  }

  /** Upload request information. */

  // Task: construct prtocol u/l request + keep track of required local
  // resources=input stream, metadata, version histories etc.
  public class UploadRequestBase extends Transmitter  {

    protected QueriedUID object = null;
    protected fc.syxaw.protocol.UploadRequest request;
    protected TransmitStatus remoteStatus = null;
    protected TransmitStatus status = new TransmitStatus(TransmitStatus.NO_STATUS,
                                               Constants.NO_VERSION);


    protected UploadRequestBase( QueriedUID object,
                                 InputStream aUploadStream,
                                 FullMetadata aMd,
                                 VersionHistory aHistory,
                                 int objectVersion,
                                 int[] peerVersionsAvailable,
                                 boolean sendData, boolean sendMeta,
                                 String lock) throws
        IOException {
      super(new fc.syxaw.protocol.DownloadRequest(null,
          peerVersionsAvailable,
          sendMeta, sendData, false, null),
            aUploadStream, aMd, aHistory, objectVersion);
      this.object = object;
      request = new fc.syxaw.protocol.UploadRequest(object.toBase64Q(),
          sendMeta, sendData,lock);
      // Local status so far is OK
      status =new TransmitStatus(TransmitStatus.OK,objectVersion);
      status.setLock(lock);
    }

    public QueriedUID getObject() {
      return object;
    }

    /** Status of the completed upload. */
    public TransmitStatus getStatus() {
      return status;
    }

    /** Status, as reported from the remote device. */
    public TransmitStatus getRemoteStatus() {
      return remoteStatus;
    }

    /** Header for upload request. */
    public fc.syxaw.protocol.UploadRequest getRequestHeader() {
      return request;
    }

    /** Transfer header for upoad request */
    public TransferHeader getRequestTransferHeader() {
      return getTransferHeader();
    }

  }

  /** Single object upload request. */
  public class UploadRequest extends UploadRequestBase {

    protected SynchronousCallChannel.SynchronousCall call;
    protected String location;

    public UploadRequest(GUID aName, InputStream aUploadStream,
                            FullMetadata aMd,
                            VersionHistory aHistory,
                            int objectVersion,
                            int[] peerVersionsAvailable,
                            boolean sendData, boolean sendMeta,
                            String lockToken) throws
        IOException {
      super(aName.getQueriedUId(), aUploadStream, aMd, aHistory, objectVersion,
            peerVersionsAvailable, sendData, sendMeta,lockToken);
      location = aName.getLocation();
      call = new UploadCall();
    }

    protected TransmitStatus upload() throws IOException {
      SynchronousCallChannel ch = ChannelProvider.getChannel(location);
      TransmitStatus ts = new TransmitStatus();
      ObjectInputStream rs = ch.call(Constants.SYNC_UPLOAD, call, new
                                     Serializable[] {ts});
      remoteStatus = ts;
      remoteStatus.assignTo(status);
      return ts;
    }

    public String getLocation() {
      return location;
    }

    protected class UploadCall extends HTTPCallChannel.HTTPSynchrounousCall {

      public Serializable[] getRequestHeaders() throws IOException {
        return getRequestHeaders(null);
      }

      public Serializable[] getRequestHeaders(java.util.Map httpHeaders)
        throws IOException {
        if (httpHeaders != null) {
          long contentLength = getContentLength();
          Log.log("Setting content length to " + contentLength, Log.INFO);
          if( contentLength != -1l )
            httpHeaders.put("Content-Length",String.valueOf(contentLength));
        }
        return new Serializable[] {request,getRequestTransferHeader()};
      }

      public void getData(ObjectOutputStream os) throws IOException {
        UploadRequest.this.getData(os);
      }

      public boolean hasData() {
        return true;
      }
    }
  }

  /** Batch upload rqeuest. */
  public class BatchUploadRequest extends BatchTransmitter {

    protected SynchronousCallChannel.SynchronousCall call;
    protected final TransferHeader rqHeader = new TransferHeader(); // Empty
    protected String location;
    protected TransmitStatus remoteStatus = null;
    protected TransmitStatus status = new TransmitStatus(TransmitStatus.NO_STATUS,
                                               Constants.NO_VERSION);
    protected Map nonComleted = Collections.synchronizedMap(new HashMap());

    public BatchUploadRequest(String location, List objects) throws IOException {
      super(objects);
      for( Iterator i = objects.iterator();i.hasNext();) {
        UploadTask t = (UploadTask) i.next();
        nonComleted.put(t.getObject(),t);
      }
      this.location = location;
      call = new BatchUploadCall();
    }

    public TransmitStatus upload() throws IOException {
      ObjectInputStream rs = null;
      TransmitStatus ts = new TransmitStatus();
      Log.log("Starting batch upload.", Log.INFO);
      try {
        SynchronousCallChannel ch = ChannelProvider.getChannel(location);
        rs = ch.call(Constants.SYNC_UPLOAD, call, new Serializable[] {ts});
        remoteStatus = ts;
        if( remoteStatus.getStatus() == TransmitStatus.SEE_SUBSTATUSES ) {
          // Get object substatuses
          while(true) {
            TransmitStatus replyStatus = new TransmitStatus();
            replyStatus.setObject(""); // FIXME-W: Default value to workaround broken XML deser
            // (empty tag deser currently yields null, not "")
            try {
              rs.readObject(replyStatus);
            } catch( EOFException x) {
              break; // Done
            }
            UID object = UID.createFromBase64(replyStatus.getObject());
            UploadTask ut = (UploadTask) nonComleted.remove(object);
            if(ut==null)
              Log.log("Ignoring status for unknown object "+object,Log.WARNING);
            else {
              ut.remoteStatus = replyStatus; // FIXME-W: duplicated status objects
              ut.status = replyStatus;
              ut.getCompletionHandler().uploadComplete(ut);
            }
          }
        }
        remoteStatus.assignTo( status);
      } finally {
        if( rs!=null ) rs.close();
        Log.log("Batch upload of "+_sentItems+" objects completed.",Log.INFO);
        if( nonComleted.size() > 0) {
          Log.log("Finishing " + nonComleted.size() +
                  " orphaned batch ul requests", Log.INFO);
          for( Iterator i=nonComleted.values().iterator();i.hasNext();) {
            UploadTask t =(UploadTask) i.next();
            t.getCompletionHandler().uploadComplete(t);
          }
        }
      }
      return ts;
    }

    public void add(UploadTask t) {
      Log.log("Enqueuing upload of " + t.getObject(), Log.INFO);
      enqueue(t);
      nonComleted.put(t.getObject(),t);
    }

    protected void getData(ObjectOutputStream os) throws
        IOException {
      getData(os,true);
    }

    protected void handleTransmitted(fc.syxaw.fs.FileTransfer.
                                     UploadRequestBase sent) {
      // No action required, we call the uploadhandler from upload()
    }

    protected class BatchUploadCall extends HTTPCallChannel.HTTPSynchrounousCall {
      public Serializable[] getRequestHeaders(java.util.Map httpHeaders)
        throws IOException {
        long contentLength = getContentLength();
        Log.log("Setting batch content length to " + contentLength, Log.INFO);
        if (contentLength != -1l)
          httpHeaders.put("Content-Length", String.valueOf(contentLength));

        fc.syxaw.protocol.UploadRequest request =
            new fc.syxaw.protocol.UploadRequest(null,false,false,null);

        return new Serializable[] {request,rqHeader};
      }

      public void getData(ObjectOutputStream os) throws IOException {
        BatchUploadRequest.this.getData(os);
      }

      public boolean hasData() {
        return true;
      }
    }

  }

  /** Response to a batch download.*/
  public class BatchDownloadResponse extends BatchTransmitter {

    BatchDownloadResponse(int promised) {
      super(Collections.EMPTY_LIST);
      this.promised = promised;
    }

    BatchDownloadResponse(List initQueue) {
      super(initQueue);
    }

    protected void handleTransmitted(fc.syxaw.fs.FileTransfer.
                                     UploadRequestBase sent) {
      ((UploadTask) sent).getCompletionHandler().uploadComplete(sent);
    }

  }

  /** Download response for a download request. */
  public class DownloadResponse extends Transmitter  {

    /** Create a download response for a download request.
     * Initializes the transfer header and chooses an encoding according to
     * the download request being replied to.
     *
     * @param request download request this object is a response to
     * @param dataStream object data to be sent in reply
     * @param md object metadata to be sent in reply
     * @param history version history for the object being downloaded
     * @param objectVersion current local version of object
     * @throws IOException if construction of the object fails.
     */
    public DownloadResponse( fc.syxaw.protocol.DownloadRequest request,
                      InputStream dataStream, FullMetadata md,
                      VersionHistory history,
                      int objectVersion) throws IOException {
      super( request,dataStream,md,history,objectVersion);
    }

    /** Transfer header of reply */
    public TransferHeader getReplyTransferHeader() {
      return getTransferHeader();
    }

    /** Send data into an output stream */
    public void getData(ObjectOutputStream os) throws IOException {
      super.getData(os);
    }

  }

  /** Class implementing reception of data and metadata. */
  protected abstract class Receiver {

    protected BLOBStorage dataStorage;
    protected VersionHistory history;
    protected Metadata receivedMd;
    protected boolean getMetadata;

    protected Receiver( BLOBStorage aStorage,
                     VersionHistory aHistory,
                     boolean agetMetadata ) {
      history = aHistory;
      dataStorage = aStorage;
      getMetadata = agetMetadata;
    }

    // State used by decoder
    protected int dataRef=Constants.NO_VERSION, metaRef=Constants.NO_VERSION;

    /** Get received metadata. <code>null</code> if no metadata or a metadata version
     * reference was received. */

    public Metadata getMetadata() {
      return receivedMd;
    }

    /** Get version reference of downloaded data.
     * Equals to {@link Constants#NO_VERSION} if no version is referenced. */
    public int getDataRef() {
      return dataRef;
    }

    /** Get version reference of downloaded metadata.
     * Equals to {@link Constants#NO_VERSION} if no version is referenced. */

    public int getMetadataRef() {
      return metaRef;
    }

    protected void receive( ObjectInputStream dataSource, TransferHeader th ) throws IOException {
      if (getMetadata) {
        MetadataDecoder md = new MetadataDecoder(th.getMetaEncoding());
        md.decode(dataSource);
        metaRef = md.getVersionReference();
        receivedMd = md.getMetadata();
      }
      if ( dataStorage != null ) {
        OutputStream dataStoreStream = null;
        try {
          dataStoreStream = dataStorage.getOutputStream(false);
          DataDecoder dd = new DataDecoder(th.getEncoding(), dataStoreStream,
                                         history);
          dd.decode(dataSource);
          dataRef = dd.getVersionReference();
        } finally {
          if( dataStoreStream != null )
            dataStoreStream.close();
        }
      }
    }

    public BLOBStorage getStorage() {
      return dataStorage;
    }
  }

  /** Receiver for batch transmissions. */
  public abstract class BatchReceiver {

   // Map dlRequests = new HashMap(); // mapof <UID,DownloadRequestBase>
    protected ReceptionHandler rh;
    //protected DownloadCompletionHandler ch;

    /** Create a new receiver.
     *
     * @param rh ReceptionHandler for the receiver
     */
    public BatchReceiver( ReceptionHandler rh ) {
     this.rh = rh;
     //this.ch = ch;
    }

    /** Handler for received objects. Gets called when an object has been
     * received.
     *
     * @param received DownloadRequestBase for the object, as returned by
     *  the reception handler.
     */
    protected abstract void handleReceived(DownloadRequestBase received);

    /** Receive batch transmission.
     *
     * @param dataSource data to receive
     * @param inUpload set to <code>true</code> if acting as responder to a
     * batch upload call, otherwise acting as a batch download caller.
     * @throws IOException if an I/O error occurs
     */
    protected void receive(ObjectInputStream dataSource, boolean inUpload) throws
        IOException {
      while(true) {
        TransmitStatus replyStatus = new TransmitStatus(TransmitStatus.OK,
            Constants.NO_VERSION);
        fc.syxaw.protocol.UploadRequest ulReq = new
            fc.syxaw.protocol.UploadRequest();
        replyStatus.setObject(""); // FIXME-W: Default value to workaround broken XML deser
        ulReq.setObject("");
        // (empty tag deser currently yields null, not "")
        try {
          if( inUpload )
            dataSource.readObject(ulReq);
          else
            dataSource.readObject(replyStatus);
        } catch( EOFException x) {
          break; // Done
        }
        QueriedUID object = QueriedUID.createFromBase64Q(
            inUpload ? ulReq.getObject() : replyStatus.getObject());
        TransferHeader replyHeader = new TransferHeader();
        if( replyStatus.getStatus() >= TransmitStatus.OK ) {
            dataSource.readObject(replyHeader);
            DownloadRequestBase req = rh.getReceiver(object, inUpload ? ulReq : null);
            if (req == null)
              Log.log("Will not handle this object (was it even requested?): ",
                      Log.FATALERROR, replyStatus);
            req.remoteStatus = replyStatus;
            req.replyHeader = replyHeader;
            req.receive(dataSource,replyHeader);
            // FIXME-W: Status and remoteStatus use is mixed up in code...
            req.status = new TransmitStatus(replyStatus.getStatus(),
                                           replyStatus.getVersion(),
                                           replyStatus.getObject());
            handleReceived(req);
        }
      }
      Log.log("====> Batch receive done.",Log.INFO);
    }
  }

  /** Upload response to an upload request. */
  public class UploadResponse extends Receiver {

    protected TransferHeader header;

    /** Create an upload response for a upload request.
     * Initializes the reply transfer header and chooses an encoding according to
     * the upload transfer header and the upload request being replied to.
     *
     * @param request upload request being replied to
     * @param aheader transfer header of request being replied to
     * @param storage output stream to store uploaded data to
     * @param history version history of the object being uploaded
     */
    public UploadResponse( fc.syxaw.protocol.UploadRequest request,
                           TransferHeader aheader,
                     BLOBStorage storage,
                     VersionHistory history ) {
      super( request.getData() ? storage : null ,history,
             request.getMetadata() );
      header = aheader;
    }

    /** Receive data and metadata from an input stream. */
    public void receive( ObjectInputStream dataSource ) throws IOException {
      super.receive(dataSource,header);
    }
  }

  /** Batch upload response. */
  public class BatchUploadResponse extends BatchReceiver {

    private DownloadCompletionHandler ch;
    protected List statuses = new LinkedList();

    public BatchUploadResponse( ReceptionHandler rh,
        DownloadCompletionHandler ch) {
      super(rh);
      this.ch = ch;
    }

    public List getStatuses() {
      return statuses;
    }

    protected void handleReceived(DownloadRequestBase received) {
      ch.downloadComplete(received);
      TransmitStatus ts = new TransmitStatus(),
          s=received.getRemoteStatus();
      ts.setObject(received.getObject().toBase64Q());
      ts.setStatus(s.getStatus());
      ts.setVersion(s.getVersion());
      statuses.add(ts);
    }
  }

  /** Batch download request. See {@link #download} for a description
   * of the batch download format.
   */

  public class BatchDownloadRequest extends BatchReceiver implements
      ReceptionHandler {

    protected TransferHeader requestHeader = null;
    protected SynchronousCallChannel.SynchronousCall call;
    protected TransmitStatus remoteStatus = null;
    protected TransmitStatus status = new TransmitStatus();
    protected fc.syxaw.protocol.DownloadRequest request;

    protected List queuedDownloads = null; // <Listof download tasks>
    private Map requestsByObject = new HashMap();
    private String location;

    /** Create a new batch download
     *
     * @param location of all objects in the batch
     */
    public BatchDownloadRequest(String location) {
      super(null);
      this.rh = this;
      this.location = location;
      call = new DownloadCall();
      request = new fc.syxaw.protocol.DownloadRequest(null, null, true,
          true, false, null);
      requestHeader = new TransferHeader(Constants.NO_VERSION,null,Constants.NO_SIZE,
                            TransferHeader.ENC_NONE,TransferHeader.ENC_NONE,
                            ACCEPTED_ENCODINGS);
    }

    public void setObjects(List aq) {
      queuedDownloads = aq;
    }

    /** Make batch download call. */
    protected TransmitStatus download() throws IOException {
      try {
        SynchronousCallChannel ch = ChannelProvider.getChannel(location);
        TransmitStatus ts = new TransmitStatus();
        TransferHeader replyHeader = new TransferHeader();
        ObjectInputStream rs = ch.call(Constants.SYNC_DOWNLOAD, call,
                                       new Serializable[] {replyHeader, ts});
        remoteStatus = ts;
        remoteStatus.assignTo(status);
        Log.log("D/l reply status is ", Log.INFO, ts);
        if (ts.getStatus() >= TransmitStatus.OK)
          receive(rs,false);
        return ts;
      } finally {
        // Clean up any queued objects left
        if( requestsByObject.size() > 0 )
          Log.log("Cleaning unhandled requests, count= "+requestsByObject.size(),
                Log.WARNING);
        for (Iterator  iter = requestsByObject.values().iterator();  iter.hasNext(); ) {
          DownloadTask item = (DownloadTask) iter.next();
          Log.log("Cleaning unhandled request: ",Log.WARNING,item);
          item.getStatus().setStatus(TransmitStatus.ERROR);
          item.getCh().downloadComplete(item);
        }
      }
    }

    /** Get download request transfer header. */
    public TransferHeader getRequestTransferHeader() {
      return requestHeader;
    }

    class DownloadCall extends HTTPCallChannel.HTTPSynchrounousCall {
      public Serializable[] getRequestHeaders() throws IOException {
        fc.syxaw.protocol.DownloadRequest[] requests = new
            fc.syxaw.protocol.DownloadRequest[queuedDownloads.size()];
        int pos = 0;
        for (Iterator  i = queuedDownloads.iterator();  i.hasNext(); ) {
          DownloadTask task = (DownloadTask) i.next();
          requests[pos++] = task.getRequestHeader();
          requestsByObject.put(task.getObject(),task);
        }
        request.setObjects(requests);
        return new Serializable[] {request,getRequestTransferHeader()};
      }

      public boolean hasData() {
        return false;
      }

      public void getData(ObjectOutputStream os) throws IOException {
        return; // No data in download req
      }
    }


    public DownloadRequestBase getReceiver(QueriedUID object,
      fc.syxaw.protocol.UploadRequest none) {
      return (DownloadRequestBase) requestsByObject.remove(object);
    }

    protected void handleReceived(fc.syxaw.fs.FileTransfer.
                                  DownloadRequestBase received) {
      ((DownloadTask) received).getCh().downloadComplete(received);
    }

  }

  /** Single object download request. */

  public class DownloadRequest extends DownloadRequestBase {

    protected TransferHeader requestHeader = null;
    protected SynchronousCallChannel.SynchronousCall call;
    private String location;

    // Note: does not close aStorage. (Follows convention that
    // streams given should be closed&dealloced by caller)
    public DownloadRequest( GUID name,
                     BLOBStorage aStorage,
                     VersionHistory aHistory,
                     boolean requestLock ) {
        super( name.getQueriedUId(), true, true, aStorage,aHistory,requestLock);
        location = name.getLocation();
        requestHeader = new TransferHeader(Constants.NO_VERSION,null,Constants.NO_SIZE,
                              TransferHeader.ENC_NONE,TransferHeader.ENC_NONE,
                              ACCEPTED_ENCODINGS);
        requestHeader.setBranchState(aHistory.onBranch() ?
                TransferHeader.BS_ON_BRANCH : 0);
        call = new DownloadCall();
    }


    public TransmitStatus download() throws IOException {
      return download(call,location);
    }
    /** Get download request transfer header. */
    public TransferHeader getRequestTransferHeader() {
      return requestHeader;
    }

    class DownloadCall extends HTTPCallChannel.HTTPSynchrounousCall {
      public Serializable[] getRequestHeaders() throws IOException {
        return new Serializable[] {request,getRequestTransferHeader()};
      }

      public boolean hasData() {
        return false;
      }

      public void getData(ObjectOutputStream os) throws IOException {
        return; // No data in download req
      }
    }

  }

  /** Download request information. */
  // See UploadRequestBase, there should be a lot of symmetry...
  public class DownloadRequestBase extends Receiver {

    protected QueriedUID object = null;

    protected TransferHeader replyHeader = null;
    protected fc.syxaw.protocol.DownloadRequest request;
    protected TransmitStatus remoteStatus = new TransmitStatus(TransmitStatus.OK,
                                               Constants.NO_VERSION);
    protected TransmitStatus status = new TransmitStatus(TransmitStatus.OK,
                                               Constants.NO_VERSION);


    public DownloadRequestBase( QueriedUID object, boolean receiveData,
                                boolean receiveMeta,
                                BLOBStorage aStorage,
                           VersionHistory aHistory, boolean requestLock ) {
      super(aStorage, aHistory, true );
      //A! assert object != null;
      //A! assert aHistory != null;
      request = new fc.syxaw.protocol.DownloadRequest( object.toBase64Q(),
          Version.getNumbers(aHistory.getFullVersions(Version.CURRENT_BRANCH)),
          receiveMeta,
          receiveData, requestLock, null);
      this.object = object;
    }

    public QueriedUID getObject() {
      return object;
    }


    /** Status of the completed download. */
    public TransmitStatus getStatus() {
      return status;
    }

    /** Status, as reported from the remote device. */
    public TransmitStatus getRemoteStatus() {
      return remoteStatus;
    }

    /** Get download request header. */
    public fc.syxaw.protocol.DownloadRequest getRequestHeader() {
      return request;
    }

    /** Get transfer header of reply. */
    public TransferHeader getTransferHeader() {
      return replyHeader;
    }

    /** Make download call. */
    protected TransmitStatus download(SynchronousCallChannel.SynchronousCall
                                      call, String location) throws IOException {
      SynchronousCallChannel ch = ChannelProvider.getChannel(location);
      TransmitStatus ts = new TransmitStatus();
      replyHeader = new TransferHeader();
      ObjectInputStream rs = ch.call(Constants.SYNC_DOWNLOAD, call,
                                     new Serializable[] {replyHeader, ts});
      remoteStatus = ts;
      remoteStatus.assignTo(status);
      Log.log("D/l reply status is ", Log.INFO, ts);
      if (ts.getStatus() >= TransmitStatus.OK)
        receive(rs, replyHeader);
      return ts;
    }
  }

  /** Default data encoder.
   * The default data encoder used by {@link FileTransfer}. Delegates encoding
   * to {@link fc.syxaw.codec.VersionRefEncoder} or
   * {@link fc.syxaw.codec.DeltaEncoder} if applicable; otherwise
   * no encoding is performed.
   */

  public class DataEncoder extends Encoder  {

    InputStream data;
    int encoding;
    Encoder encoder = null;
    long dataSize;

    DataEncoder(InputStream adata, String format,
                int averref, VersionHistory history,
                int[] availVersions, long adataSize) throws IOException {
      data = adata;
      dataSize = adataSize;
      encoding = initEncoder(adata,format,averref,history,availVersions);
    }

    public void write( ObjectOutputStream os ) throws IOException{
      Log.log("Starting binary data transmit of size "+dataSize+
              (encoder != null ? " using encoder "+encoder.getClass().getName():
                ""), Log.DEBUG);
      if( encoder != null ) {
        encoder.write(os);
      } else { // Plain binary
        ObjectOutputStream sos = null;
        try {
          sos = os.writeSubStream(null, dataSize, false);
          int count = Util.copyStream(data, sos);
          Log.log("Binary data transmit, sent bytes=" + count +", promised="+dataSize, Log.INFO);
        } catch (IOException ex) {
            Log.log("Writing failed", Log.WARNING, ex);
            throw ex;
        } finally {
          if( sos != null )
            sos.close();
        }
      }
    }

    public int getEncoding() {
      return encoding;
    }

    public long getEncodedSize() {
      return encoder == null ? dataSize : encoder.getEncodedSize();
    }

    protected int initEncoder(InputStream in, String type,
                              int verRef, VersionHistory h,
                                 int[] availableVersions) throws IOException  {
      /*
     // See if a suitable base is found
      if (in instanceof DeltaStream) {
          Log.log("Maybe delta, localvers=" + Util.toString(h.getVersions()) +
                   "remote=" + Util.toString(availableVersions)+
                   " verref"+verRef, Log.DEBUG);
      }*/

      // Strategy for finding base version: find highest common version in avail
      // and local ver arrays
      if( verRef != Constants.NO_VERSION ) {
        encoder = new VersionRefEncoder(verRef);
        return TransferHeader.ENC_VERSIONREF;
      }
      int baseVersion = Constants.NO_VERSION;
      if( Config.XML_DELTA &&
          availableVersions != null &&
          in instanceof DeltaStream  ) {
        int maxVersion = h.getCurrentVersion();
        int[] localVersions =
               Version.getNumbers(h.getFullVersions(Version.CURRENT_BRANCH));
        Arrays.sort(availableVersions);
        for (int i = availableVersions.length - 1;
             baseVersion == Constants.NO_VERSION && i >= 0; i--) {
          int candidate = availableVersions[i];
          if (candidate > maxVersion)
            continue;
          int ix = Util.arrayLookup(localVersions, candidate, candidate + 1);
          if (ix != -1)
            baseVersion = localVersions[ix];
        }
        Log.log("Delta encoder base version=" + baseVersion, Log.INFO);
      }
      if (baseVersion == Constants.NO_VERSION)
        return TransferHeader.ENC_BINARY;

      // Delta enc=> init delta encoder
      int aEncoding=TransferHeader.ENC_BINARY;
      try {
        encoder = new DeltaEncoder( in, baseVersion);
        aEncoding = TransferHeader.ENC_DELTA;
      } catch (CodecException e) {
        Log.log("Cannot construct delta encoder, reverting to binary",Log.WARNING,e);
      }
      return aEncoding;
    }

  }

  /** Default metadata encoder.
   * The default metadata encoder used by {@link FileTransfer}. Delegates encoding
   * to {@link fc.syxaw.codec.VersionRefEncoder} if applicable; otherwise
   * no encoding is performed.
   */

  public class MetadataEncoder extends Encoder {

    FullMetadata md;
    int verRef;

    MetadataEncoder(FullMetadata amd, int averref) {
      verRef = averref;
      md = amd;
    }

    public void write( ObjectOutputStream os ) throws IOException{
      if( verRef != Constants.NO_VERSION ) {
        (new VersionRefEncoder(verRef)).write(os);
        Log.log("Data transmit, ref="+verRef,Log.DEBUG);
      } else {
        MetadataImpl smd =  MetadataImpl.createFrom( md ); // Ensure serializable
        //Log.log("Metadata transmit, keywords:" + smd.getKeywords().toString(),Log.DEBUG,md);
        os.writeObject(smd);
      }
    }

    public int getEncoding() {
      return verRef != Constants.NO_VERSION ? TransferHeader.ENC_VERSIONREF :
          TransferHeader.ENC_BINARY;
    }

  }

  /** Default metadata decoder.
   * The default metadata decoder used by {@link FileTransfer}. Decodes
   * metadata that is not encoded or encoded with
   * {@link fc.syxaw.codec.VersionRefEncoder}
   */

  public class MetadataDecoder extends VersionRefDecoder {

    protected int encoding;
    protected Metadata md=null;

    /** Create decoder for given encoding.
     * @param aencoding The used encoding. Allowable values are
     *  {@link TransferHeader#ENC_VERSIONREF ENC_VERSIONREF} and
     *  {@link TransferHeader#ENC_BINARY ENC_BINARY}.
     */

    public MetadataDecoder(int aencoding) {
      encoding=aencoding;
    }

    public void decode(ObjectInputStream is) throws IOException {
      if( encoding == TransferHeader.ENC_VERSIONREF )
        super.decode(is);
      else if( encoding == TransferHeader.ENC_BINARY ) {
        md = fc.syxaw.fs.Syxaw.getStorageProvider().createMetadata();
        is.readObject(md);
        //Log.log("MD receive",Log.INFO, md);
      }
    }

    public Metadata getMetadata() {
      return md;
    }
  }

  /** Default data decoder.
   * The default data decoder used by {@link FileTransfer}. Decodes
   * data that is not encoded or encoded with
   * {@link fc.syxaw.codec.VersionRefEncoder} or
   * {@link fc.syxaw.codec.DeltaEncoder}
   */

  public class DataDecoder extends VersionRefDecoder {

    int encoding;
    OutputStream storage;
    VersionHistory history;

    /** Create decoder for given encoding.
     * @param aencoding The used encoding. Allowable values are
     *  {@link TransferHeader#ENC_VERSIONREF ENC_VERSIONREF},
     *  {@link TransferHeader#ENC_BINARY ENC_BINARY} and
     *  {@link TransferHeader#ENC_DELTA ENC_DELTA}
     * @param aStorage output stream for decoded data
     * @param ahistory version history of object being decoded
     * (used when decoding deltas)
     */
    public DataDecoder(int aencoding, OutputStream aStorage,
                       VersionHistory ahistory ) {
      encoding=aencoding;
      storage=aStorage;
      history = ahistory;
    }

    public void decode(ObjectInputStream is) throws IOException {
      if( encoding == TransferHeader.ENC_VERSIONREF )
        super.decode(is);
      else if( encoding == TransferHeader.ENC_DELTA ) {
        try {
          DeltaDecoder dde = new DeltaDecoder(storage, history);
          dde.decode(is);
        } catch( CodecException x ) {
          Log.log("Exception during delta decode.",Log.ERROR,x);
          throw new IOException("Exception during delta decode.");
        }
      } else if( encoding == TransferHeader.ENC_BINARY ) {
        ObjectInputStream sis = null;
        try {
          sis = is.readSubStream();
          int count = Util.copyStream(sis,storage);
          Log.log("Data receive, bin, size="+count,Log.INFO);
        } finally {
          if( sis != null ) sis.close();
        }
      }
    }
  }

  /** Upload task. Encapsulates a request as well as some additional
   * resources, like file locks.
   */
  public class UploadTask extends UploadRequestBase {

    protected SyxawFile.Lock lock=null;
    long ts = System.currentTimeMillis();
    UploadCompletionHandler ch;

    public long getTimestamp() {
      return ts;
    }

    public UploadCompletionHandler getCompletionHandler() {
      return ch;
    }

    public SyxawFile.Lock getFileLock() {
      return lock;
    }

    UploadTask(QueriedUID object, InputStream aUploadStream, FullMetadata aMd,
               VersionHistory aHistory, int objectVersion,
               int[] peerVersionsAvailable, boolean sendData, boolean sendMeta,
               UploadCompletionHandler ch, SyxawFile.Lock l, String lockToken) throws
        IOException {
      super(object, aUploadStream, aMd, aHistory, objectVersion,
            peerVersionsAvailable, sendData, sendMeta,lockToken);
      this.lock =l;
      this.ch = ch;
    }
  }
}
// arch-tag: 5254fb5eb973ac527b89f09ac7a31db9 *-
