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

// $Id: SynchronizationEngine.java,v 1.97 2005/06/08 13:03:28 ctl Exp $
package fc.syxaw.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fc.syxaw.api.ISyxawFile;
import fc.syxaw.api.Metadata;
import fc.syxaw.api.MetadataImpl;
import fc.syxaw.api.SynchronizationException;
import fc.syxaw.proto.ObjectMergerEx;
import fc.syxaw.proto.RPC;
import fc.syxaw.proto.Version;
import fc.syxaw.protocol.Proto;
import fc.syxaw.protocol.TransmitStatus;
import fc.syxaw.storage.hfsbase.ServerConfig;
import fc.syxaw.util.Progress;
import fc.syxaw.util.Util;
import fc.util.log.Log;

import EDU.oswego.cs.dl.util.concurrent.BoundedLinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.DirectExecutor;
import EDU.oswego.cs.dl.util.concurrent.Executor;
import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;
import EDU.oswego.cs.dl.util.concurrent.Semaphore;

/** Syxaw synchronization engine. This class implements synchronization
 * of objects by performing object transfers, version management and
 * data reconciliation. The {@link SynchronizationServer}
 * class processes incoming Syxaw synchronization protocol requests.
 */

public class SynchronizationEngine {

  /** Max no of object in one HTTP request. */
  public static final int MAX_BATCH=256;
  
  /** Max number of worker threads.
   * <p>Special values: 1 = upload + download +1 worker thread; 
   * 0=upload+download/worker thread, -1: upload/download/worker in one thread */
  public static final int MAX_WORKERS=
    Integer.getInteger("syxaw.debug.workers", 4);
  
  protected static SynchronizationEngine instance =
      new SynchronizationEngine();

  private boolean getData = true;
  private boolean getMetadata = true;
  
  
  protected FileTransfer transfer = FileTransfer.getInstance();

  protected SynchronizationEngine() {
  }

  /** Get engine instance.
   *
   * @return engine instance
   */
  public static SynchronizationEngine getInstance() {
    return instance;
  }

 /** Synchronize a Syxaw object. If a directory is synchronized, all
  * changed (locally or remotely) files in the directory subtree will
  * also be synchronized.
  * @param f object to synchronize
  * @return success code. See {@link fc.syxaw.api.StatusCodes}
  * @throws SynchronizationException signals that the synchronization failed
  * @throws IOException an I/O error occurred
  */

 public int synchronize( fc.syxaw.fs.SyxawFile f, boolean getData, boolean getMetadata )
      throws IOException, SynchronizationException {
    if( fc.syxaw.transport.Config.MEASURE_TIMES ) {
      fc.syxaw.util.Log.beginContext("sync");
      fc.syxaw.util.Log.putDebugObj(f,"sync-start",System.currentTimeMillis());
    }
    boolean isDir = f instanceof fc.syxaw.hierfs.SyxawFile && ( (fc.syxaw.hierfs.SyxawFile) f).isDirectory();
    if (!getData && isDir){ // FIXME-Dessy: Md sync limited to root.
      this.getData = !getData;
    }
    this.getMetadata = getMetadata;
    fc.syxaw.hierfs.SyxawFile.Lock rootLock = f.lock();
    try {
      ObjectMerger merger = f.getLinkObjectMerger();
      Progress.moreStuff(1);
      int retval = synchronizeObject(f, isDir, null, merger);
      Set dependentObjects = merger.getObjectsNeedingSync();
      if ( retval != TransmitStatus.OK ||
           Util.isEmpty(f.getLink()) ||
           dependentObjects == null ||
           !Config.SYNC_DEPENDENT || 
           dependentObjects.size() == 0 )
        return retval;
      // Sync individual objects
      Log.log("Root synced OK. Now syncing dependent objects: " +
            dependentObjects,
            Log.INFO);
      if (!ServerConfig.BATCH_SYNC) {
        Log.log("Batch synchronization turned off.", Log.WARNING);
        for (Iterator i = dependentObjects.iterator();i.hasNext(); ) {
          SyxawFile df = f.newInstance((UID) i.next());          
          boolean isDepDir = false;
          ObjectMerger depMerger = df.getLinkObjectMerger();
          synchronizeObject(df, isDepDir, merger.getDependentLID(), depMerger);
        }
        return TransmitStatus.OK; // Well, not entirely true..
      }
      int failcount = 0;
      SyxawFile[] dependentFiles = null;// new SyxawFile[dependentObjects.size()];
      int pos = 0, batchoffset=0;
      for (Iterator i = dependentObjects.iterator();i.hasNext(); ) {
        if( dependentFiles != null && pos == dependentFiles.length ) {
          synchronizeObjectBatch(dependentFiles,merger.getDependentLID());
          dependentFiles=null;
        }
        if( dependentFiles == null ) {
          dependentFiles = new SyxawFile[
                           Math.min(MAX_BATCH, dependentObjects.size() - batchoffset)];
          batchoffset += dependentFiles.length;
          pos=0;
          Log.log("New batch of "+dependentFiles.length+" objects. Left="
                  +(dependentObjects.size()-batchoffset),Log.INFO);
          Progress.moreStuff(dependentFiles.length);
        }
        dependentFiles[pos++] = f.newInstance((UID) i.next());
//        Log.log("Dependent object for " + dependentFiles[pos - 1] + "is " +
//                dependentFiles[pos - 1].getLink(), Log.INFO);
      }
      if( dependentFiles != null ){
        String q = null;
        if (!getData && isDir){
          this.getData = getData;
          String l = f.getLink();
          if (l != null){
            int queryC = l.indexOf(SyxawFile.querySeparatorChar);
            if (queryC >= 0){
              q = l.substring(queryC);
            }
          }
        }
        synchronizeObjectBatch(dependentFiles,merger.getDependentLID(), q);
      }
      return failcount == 0 ? TransmitStatus.OK : TransmitStatus.ERROR;
    }
    finally {
      if( fc.syxaw.transport.Config.MEASURE_TIMES ) {
          fc.syxaw.util.Log.stat("sync","time",
                 ""+(System.currentTimeMillis()-
                         fc.syxaw.util.Log.getDebugLong(f,"sync-start",true)));
        fc.syxaw.util.Log.endContext();
      }
      rootLock.release();
      Progress.reset();
    }
  }

  protected int synchronizeObject(fc.syxaw.fs.SyxawFile af,
                                boolean a__isDir__, String locationScope,
                                ObjectMerger amerger )
    throws IOException, SynchronizationException {
    class NullRunnerPhase extends Phase {
      Semaphore runlock = new Semaphore(0);
      public void doRun(){
        runlock.release();
      }

      public void waitForStart() {
        try { runlock.acquire(); }
        catch( InterruptedException x) {
          Log.log("Unexpected interrupt while waiting for phase run()",Log.WARNING);
        }
      }
    }

    ObjectSynchronizer os = new ObjectSynchronizer(af, a__isDir__,
        locationScope, amerger);
    int retval = TransmitStatus.NO_STATUS;
    try {
      NullRunnerPhase p = new NullRunnerPhase();
      retval = os.synchronize1(p);
      p.checkIsSetUp();
      if(p.isDone)
        return retval;
      p = new NullRunnerPhase();
      retval = os.synchronize2(p);
      if(p.isDone)
        return retval;
      p.waitForStart();
      retval =os.synchronize3(new NullRunnerPhase());
      return retval;
    }
    finally {
      // FIXME-W: object sync return value setting are a bit kludgy
      if( os.retval == TransmitStatus.NO_STATUS )
        os.retval = retval;
      os.cleanup();
    }
  }

  private Executor pool = null;

  public int synchronizeObjectBatch(ArrayList<fc.syxaw.fs.SyxawFile> af, boolean getData, boolean getMeta) 
  throws FileNotFoundException{
    this.getData = getData;
    this.getMetadata = getMeta;
    ArrayList<fc.syxaw.fs.SyxawFile> local = new ArrayList<fc.syxaw.fs.SyxawFile>();
    local.addAll(af);
    SyxawFile root = Syxaw.getFile(fc.syxaw.storage.hfsbase.ServerConfig.ROOT_FOLDER);
    String scope  = root.getLinkObjectMerger().getDependentLID();
    return synchronizeObjectBatch(af.toArray(new SyxawFile[0]), scope);
  }
  
  protected int synchronizeObjectBatch(fc.syxaw.fs.SyxawFile[] af, String
      locationScope) throws
FileNotFoundException {
    return synchronizeObjectBatch(af, locationScope, null);
  }
  
  protected int synchronizeObjectBatch(fc.syxaw.fs.SyxawFile[] af, String
                                       locationScope, String query) throws
      FileNotFoundException {
    if( fc.syxaw.transport.Config.MEASURE_TIMES ) {
        fc.syxaw.util.Log.beginContext("sync-batch");
        fc.syxaw.util.Log.putDebugObj(af,"sync-start",System.currentTimeMillis());
    }
    if( af.length == 0 )
      return TransmitStatus.OK; // Nop
    try {
      Semaphore sync1Done = null;
      Semaphore sync2Done = null;
      Semaphore syncDone = null;
      // NOTE: Might be a good idea not to start synchronizers for unlinked 
      // objects, as this saves resources
      ObjectSynchronizer[] syncers = new ObjectSynchronizer[af.length];
      if( MAX_WORKERS > 0 ) {
        PooledExecutor pex = new PooledExecutor(new LinkedQueue());
        pex.setMinimumPoolSize(MAX_WORKERS);
        pex.setMaximumPoolSize(MAX_WORKERS);
        pool = pex;
      } else {
        pool = new DirectExecutor();
      }
      transfer.startDownloadBatch();
      transfer.startUploadBatch();
      sync1Done = new Semaphore( -af.length + 1);
      sync2Done = new Semaphore( -af.length + 1);
      syncDone = new Semaphore( -af.length + 1);
      for (int i = 0; i < syncers.length; i++) {
        syncers[i] = new ObjectSynchronizer(af[i], false,locationScope, query, 
                                            af[i].getLinkObjectMerger(),
                                            sync1Done, sync2Done, syncDone);
        pool.execute(syncers[i]);
      }
      sync1Done.acquire();
      Log.log("All requests set up", Log.INFO);
      try {
        transfer.completeDownloadBatch(locationScope);
      }
      catch (IOException ex) {
        Log.log("Yet unhandled", Log.FATALERROR, ex); // FIXME
      }
      ///sync2Done.acquire(); // Upload requests done
      ///Log.log("All threads ready to enter p3 (upload)", Log.INFO);
      //transfer.completeUploadBatch();
      syncDone.acquire(); // Completion semaphore
      Log.log("Batch download of "+af.length+" objects completed.",
              Log.INFO);
    } catch (InterruptedException x ) {
        fc.syxaw.util.Log.log("Unexpected interrupt",Log.ASSERTFAILED);
    } finally {
      transfer.completeUploadBatch();
      if( fc.syxaw.transport.Config.MEASURE_TIMES ) {
          fc.syxaw.util.Log.stat("sync","time",
                 ""+(System.currentTimeMillis()-
                         fc.syxaw.util.Log.getDebugLong(af,"sync-start",true)));
          fc.syxaw.util.Log.endContext();
      }
    }
    return TransmitStatus.NO_STATUS; // FIXME
  }

  abstract class Phase implements Runnable {
    protected boolean phaseWasSetUp=false;
    protected boolean isDone=false;

    public void dontRun() {
      phaseWasSetUp = true;
      isDone = true;
    }
    public void willRun() {
      phaseWasSetUp = true;
      isDone = false;
    }

    public void run() {
      phaseWasSetUp = true;
      isDone = false;
      doRun();
    }

    protected abstract void doRun();

    protected void checkIsSetUp() {
      if( !phaseWasSetUp ) {
        Log.log("The algorithm did not tell how to proceed",Log.ASSERTFAILED);
      }
    }
  }


  // This was previously the synchronizeObject() method
  // Due to concurrent sync of possibly 100s of objects, it has been split up
  // into phases (since we cannot have 100s of blocking threads....):
  // phase 1: init + download request
  // phase 2: download post processing + possibly upload request <-UL request!=null
  // phase 3: sync finalization
  // each phase returns TransmitStatus.NO_STATUS if the next phase is required
  // cleanup: method that cleans up any allocated resources in p1-p3

  protected class ObjectSynchronizer implements Runnable {

    // State shared between algorithm phases
    // synchronizeObject parameters
    fc.syxaw.fs.SyxawFile f;
    boolean __isDir__; // Only used for sync progress messages
    ObjectMerger merger;
    // Phase 1
    String locationScope;
    String query = null;
    BLOBStorage tempStorage; // Protected resource
    FileTransfer.DownloadRequestBase dlRequest;
    SyxawFile.Lock l; // Protected resource
    VersionHistory history;
    int currentLinkVer;
    FullMetadata originalMetaData;
    GUID linkGUID;
    boolean localDataChange;
    boolean localMetaChange;
    boolean localChanges;

    // Phase 2
    FileTransfer.UploadRequestBase ulRequest; // != null if we did an upload in p2
    int newLinkVersion;
    boolean remoteChanges;
    // Phase 3
    // End state

    // Async exec state
    int retval = TransmitStatus.NO_STATUS;
    Exception retEx = null;
    Semaphore sync1Done =  null;
    Semaphore sync2Done = null;
    Semaphore syncDone = null;
    
    // DEbug
    Semaphore releasedsync1Done =  null;
    Semaphore releasedsync2Done = null;
    Semaphore releasedsyncDone = null;

    public ObjectSynchronizer(fc.syxaw.fs.SyxawFile af,
        boolean a__isDir__, String locationScope,
        ObjectMerger amerger ) {
      this(af, a__isDir__, locationScope, null, amerger);
    }
    
    public ObjectSynchronizer(fc.syxaw.fs.SyxawFile af,
                                    boolean a__isDir__, String locationScope, String query,
                                    ObjectMerger amerger ) {
        f = af;
        __isDir__ = a__isDir__;
        merger = amerger;
        this.locationScope = locationScope;
        this.query = query;
    }
    
    public ObjectSynchronizer(fc.syxaw.fs.SyxawFile af,
        boolean a__isDir__, String locationScope, 
        ObjectMerger amerger,
       Semaphore p1Done,
       Semaphore p2Done,
       Semaphore done ) {
      this(af,a__isDir__,locationScope, amerger);
      sync1Done = p1Done;
      sync2Done = p2Done;
      syncDone = done;
    }
    
    public ObjectSynchronizer(fc.syxaw.fs.SyxawFile af,
                                    boolean a__isDir__, String locationScope, String query, 
                                    ObjectMerger amerger,
                                   Semaphore p1Done,
                                   Semaphore p2Done,
                                   Semaphore done ) {
           this(af,a__isDir__,locationScope, query, amerger);
           sync1Done = p1Done;
           sync2Done = p2Done;
           syncDone = done;
    }

    protected synchronized void releaseOnce(Semaphore s) {
      if( s == null ) {
        // OK, no semaphores inited in single object sync case.
        //Log.log("Releasing null semaphore",Log.ERROR);
        return;
      }

      if( s==sync1Done ) {
        sync1Done.release();
        releasedsync1Done = sync1Done;
        sync1Done = null;
      } else if( s==sync2Done ) {
        sync2Done.release();
        releasedsync2Done = sync2Done;
        sync2Done = null;
      } else if( s==syncDone ) {
        syncDone.release();
        releasedsyncDone = syncDone;
        syncDone = null;
      } else {
        if( s == releasedsync1Done) {
          Log.warning("Re-release of sync1Done");
        } else if( s == releasedsync2Done) {
          Log.warning("Re-release of sync2Done");
        } else if( s == releasedsyncDone) {
          Log.warning("Re-release of syncDone");
        } else {          
          Log.log("Unknown semaphore "+s,Log.ERROR);
        }
        s.release(); // To be sure
      }
    }

    // Async run methods

    public void run() {
      Log.log("Entering phase 1 (stat) for "+f.getUid(),Log.INFO);
      PooledPhase next = new PooledPhase2();
      try {        
        next.acquire();
        retval=synchronize1(next);
        next.checkIsSetUp();
      } catch (Exception x) {
        Log.log("Phase 1 exception",Log.ERROR,x);
        retEx = x;
      } finally {
        Log.log("Exiting phase 1 (stat) for "+f.getUid(),Log.INFO);
        releaseOnce(sync1Done);
        if (next.isDone)
          cleanup();
        next.release();
      }
    }

    // BUGFIX-080407-1: Next phase can start (in other thread)
    // before current phase has finished completely (post-processing after
    // aynchronizex() call)
    // FIX: Introduce mutex to prevent next phase from starting in these cases.
    abstract class PooledPhase extends Phase {
      private Semaphore previousDone = 
        new Semaphore(MAX_WORKERS > 0 ? 1 : Integer.MAX_VALUE);
      
      public void acquire() {
        try {
          previousDone.acquire();
        } catch (InterruptedException e) {
          ; // Nop
        }
      }

      public void release() {
        previousDone.release();
      }     
    }
    class PooledPhase2 extends PooledPhase {
      

      public void doRun() {
        try {
          pool.execute(new Runnable() {
            public void run() {
              Log.log("Entering phase 2 (reconcile + upload) for "+f.getUid(),Log.INFO);
              PooledPhase next = new PooledPhase3();
              acquire();
              next.acquire();
              try {
                retval = synchronize2(next);
                next.checkIsSetUp();
              }
              catch (Exception x) {
                Log.log("Phase 2 exception",Log.ERROR,x);
                retEx = x;
              } finally {
                Log.log("Exiting phase 2 (stat) for "+f.getUid(),Log.INFO);
                releaseOnce(sync2Done);
                if (next.isDone)
                  cleanup();
                next.release();
              }
            }
          });
        }
        catch (InterruptedException ex) {
          Log.log("phase 2: IEx",Log.FATALERROR,ex);
        }
      }
    }

    class PooledPhase3 extends PooledPhase {
      
      public void doRun() {
        try {
          pool.execute(new Runnable() {
            public void run() {              
              acquire();
              Log.log("Entering phase 3 (cleanup) for "+f.getUid(),Log.INFO);
              Phase next = new Phase() {
                  public void doRun() {
                    Log.log("There is no known phase 4 for this algorithm!",
                            Log.ASSERTFAILED);
                  }
                };
              try {
                retval = synchronize3(next);
                next.checkIsSetUp();
              }
              catch (Exception x) {
                Log.log("Phase 3 exception",Log.ERROR,x);
                retEx = x;
              }
              finally {
                cleanup();
                Log.log("Exiting phase 3 (cleanup) for "+f.getUid(),Log.INFO);
              }
            }
          });
        }
        catch (InterruptedException ex) {
          Log.log("phase 3: Interrupted", Log.FATALERROR, ex);
        }
      }
    }
    // Sync algo

    public int synchronize1(final Phase nextPhase) throws IOException, SynchronizationException {
      nextPhase.dontRun(); // Default action
      originalMetaData = f.getFullMetadata();
      l = f.lock();
      FileTransfer transfer = FileTransfer.getInstance();
      String link = originalMetaData.getLink();
      if (Util.isEmpty(link)) {
        return TransmitStatus.OK; // This file is not linked anywhere, do nothing
      }
      if (link != null && query != null && (getMetadata && !getData)){ 
        // FIXME: this is done only on md sync at the moment.
        int queryC = link.indexOf(SyxawFile.querySeparatorChar);
        if (queryC < 0){
          Log.log("Overriding link on " + f +": " + link + " -> " + link + query, Log.DEBUG);
          link += query;
        }
      }
      if( __isDir__ )
        syncMsg(f,TransmitStatus.NO_STATUS,0x1);
      linkGUID = new GUID(locationScope,link); // Parse GUID before we do anything else      
      history = f.getLinkVersionHistory(getMetadata && !getData);
      Log.log(f + 
          ": DV=" + f.getFullMetadata().getDataVersion() +  
          ", linkDV="+ f.getLinkDataVersion() + 
          " MV=" + f.getFullMetadata().getMetaVersion() +  
          ", linkMV="+ f.getLinkMetaVersion(), Log.INFO);
      if (getMetadata && !getData) {
        currentLinkVer = f.getLinkMetaVersion();//FIXME-versions:
      }else {
        currentLinkVer = f.getLinkDataVersion();//FIXME-versions:
      }
      // Set up temp storage for download
      tempStorage = f.createStorage("download", true);

      // Init local modflags
      localDataChange = f.isDataModified(true);
      localMetaChange = f.isMetadataModified(true);

      // FIXME-P: Branch switch unpredictably(?) requires merge
      localChanges = localDataChange | localMetaChange;
//      Log.log("===============SYNC "+f+", link="+link+", localMod="+
//              localChanges, Log.DEBUG);

      if( __isDir__ )
        syncMsg(f,TransmitStatus.NO_STATUS,0x80+1);
      // Download (which is "stat" at the same time)
      transfer.download(linkGUID, tempStorage, history, localChanges,
         new FileTransfer.DownloadCompletionHandler() {
        public void downloadComplete(FileTransfer.DownloadRequestBase dlReq) {
          dlRequest = dlReq;
          nextPhase.run();
        }
      }, getData, getMetadata);
      nextPhase.willRun();
      return TransmitStatus.NO_STATUS; // Magic continue val
    }

    public int synchronize2(final Phase nextPhase) throws IOException, SynchronizationException {
      if( __isDir__ )
       syncMsg(f,TransmitStatus.NO_STATUS,0x80+3);
      nextPhase.dontRun(); // Default action
      newLinkVersion = Constants.NO_VERSION;
      if (dlRequest.getStatus().getStatus() != TransmitStatus.OK) {
        return dlRequest.getStatus().getStatus(); // Download failed, give up
      }
      int peerVersion = dlRequest.getRemoteStatus().getVersion();
      if (peerVersion == Constants.NO_VERSION)
        throw new IOException(
            "Server must assign version to requested object");

      //
      // Process object based on remote and local stats

      boolean remoteDataChange = currentLinkVer == Constants.NO_VERSION
          || (currentLinkVer != peerVersion &&
              currentLinkVer != dlRequest.getDataRef());
      boolean remoteMetaChange = currentLinkVer == Constants.NO_VERSION
          || (currentLinkVer != peerVersion &&
              currentLinkVer != dlRequest.getMetadataRef());
      remoteChanges = remoteDataChange | remoteMetaChange;

      // Revise base version depending on new joins received
nonewbase:
      if( dlRequest.getMetadata() != null ) {
        // same meta implies no changes to joins -> no base switch...
        //if( dlRequest.getMetadataRef() != Constants.NO_VERSION )
        //  Log.log("Must always get meta when switching..",Log.ERROR);
        Version oldBase = merger instanceof ObjectMergerEx ?
                          ((ObjectMergerEx) merger).getBaseVersion() :
                          new Version(f.getLinkDataVersion(),f.getBranch());
        Version newBase = reviseBase(history,
                                     dlRequest.getMetadata().getJoins(),
                                     oldBase );
        Log.log("oldBase="+oldBase+",newBase="+newBase+",nbLocal="+
                (newBase != null ?
                 String.valueOf(newBase.getLocalVersion()) : "n/a"),Log.INFO);
        if( newBase == null || newBase.compareTo(oldBase) == 0 )
          break nonewbase;
        Log.log("Change of base!",Log.INFO);
        if( newBase.compareTo(oldBase) < 0 )
          // This occurs on up-switches, but the base change is handled by the switch
          Log.log("Does not yet handle going to an older base",Log.ASSERTFAILED);
        boolean haveMoreRecentFromBranch = newBase != null &&
          newBase.compareTo(oldBase) > 0; //!!!We use local version ordering
        FIXMEPbaseIsCurrentLocalFacet=false;
        if( newBase != null ){
          List vl = history.getFullVersions(null);
          if( vl.size() > 0 ) {
            Version last = (Version) vl.get(vl.size()-1);
            if( newBase.compareTo(last) == 0 )
              FIXMEPbaseIsCurrentLocalFacet = true;
            else if( vl.size() > 1 ) {
              // Try case newBase =x, last = x/thisDevice/y, last-1=x
              // as these versions are equivalent, that on will do as well
              Version nextToLast = (Version) vl.get(vl.size()-2);
              if (Util.equals(last.getLID(), GUID.getCurrentLocation()) &&
                  last.getAncestor().equals(nextToLast) &&
                  nextToLast.equals(newBase))
                FIXMEPbaseIsCurrentLocalFacet = true;
            }
          }
        }
        if( FIXMEPbaseIsCurrentLocalFacet ) {
          // Here, we forget changes to link facet, since our local ver
          // is already joined in. This is provided there are no
          // uncommitted changes to the local facet
          localMetaChange = f.isMetadataModified(false);
          localDataChange = f.isDataModified(false);
          Log.log("Local changes already joined in; uncommitted changes="+
                  (localMetaChange||localDataChange),Log.INFO);
        } else {
          localMetaChange |= haveMoreRecentFromBranch;
          localDataChange |= haveMoreRecentFromBranch;
        }
        localChanges = localDataChange || localMetaChange;
        if( haveMoreRecentFromBranch ) {
          if( Config.PROTO_BRANCH_FENCE )
            Log.log("Base switching code execution prohibited",Log.ASSERTFAILED);
          Log.log("Switching base to "+newBase,Log.INFO);
          if (!(merger instanceof ObjectMergerEx))
            Log.log("Must have prototype code merger", Log.ASSERTFAILED);
          ((ObjectMergerEx) merger).setBaseVersion(newBase);
        }
      }

      Integer localMetaVerO = null, localDataVerO = null;
      try {
        localMetaVerO = new Integer(
            agreeOnMeta(localMetaChange, remoteMetaChange, dlRequest,
                        currentLinkVer, merger, history, f));
        localDataVerO = new Integer(
            agreeOnData(localDataChange, remoteDataChange, dlRequest,
                        currentLinkVer, merger, history, f,
                        tempStorage ));
      }
      catch (ObjectMerger.Conflict c) {
        if (localMetaVerO != null) {
          // Meta merge succeeded, we need to undo that...
          // NOTE: To optimize, we could check if a change to the file meta really was
          // made here
          f.setMetadata(originalMetaData);
        }
        // Write conflicting data & meta + log to system conflict folder
        InputStream dataStream = //!remoteDataChange ? f.getInputStream() :
            (dlRequest.getDataRef() == Constants.NO_VERSION ?
             tempStorage.getInputStream() :
             history.getData(dlRequest.getDataRef()));
        try {
          reportConflict(f, !remoteMetaChange ? f.getMetadata() :
                         (dlRequest.getMetadataRef() == Constants.NO_VERSION ?
                          dlRequest.getMetadata() :
                          history.getMetadata(dlRequest.getMetadataRef())),
                         dataStream,
                         c.getLog(),
                         f.getLink(),
                         peerVersion);
        }
        finally {
          if( dataStream != null )
            dataStream.close();
        }
        // Nothing more to do, end sync here.
        retval = TransmitStatus.CONFLICT;
        throw new SynchronizationException("Conflicts for " +
          f.getUid().toBase64(), TransmitStatus.CONFLICT);
      }
      if ( ( (ISyxawFile) f).hasConflicts())
        // Was previously in conflict, but this sync fixed it -> clear away the conflict
        ( (ISyxawFile) f).conflictsResolved();

      // *VerO's must be inited if we get here!
      int localMetaVer = localMetaVerO.intValue();
      int localDataVer = localDataVerO.intValue();

      // Check if the agreed-upon data and meta versions form any known
      // version.
      // e.g. localDataVer=1000, localMetaVer=1005 and no data changes since 1000
      //  => newLinkversion = 1005
      // on the other hand, lDV=1005, lMV=1000 and meta changes since 1000 =>
      // => newLinkVersion=NO_VERSION, since this is an unknown combination
      if (history.versionsEqual(localDataVer, localMetaVer, false))
        newLinkVersion = localMetaVer;
      else {
        newLinkVersion = Constants.NO_VERSION;
        // This case shouldn't occur in the current implementation
        if (localDataVer != Constants.NO_VERSION &&
            localMetaVer != Constants.NO_VERSION)
          Log.log(
              "Merging returned unknown version combination: lDV=" + localDataVer + ", lMV=" + localMetaVer + " -- suspicious?",
              Log.WARNING);
      }

      // See if the agreed-upon version matches peerVersion; in that case let's
      // set newLinkversion to peerVersion. That way the sync will result in
      // identical version numbers between server and client
      if (history.versionsEqual(peerVersion, newLinkVersion, false) &&
          history.versionsEqual(peerVersion, newLinkVersion, true))
        newLinkVersion = peerVersion;

        // Sanity check for current impl, can be removed
      if (newLinkVersion != Constants.NO_VERSION &&
          newLinkVersion != peerVersion)
        Log.log("Agreed upon a version older than peerVersion -- suspicious?",
                Log.WARNING);

        // Update localChanges: We may have "forgotten" local changes by
        // overwriting them


      localChanges = newLinkVersion == Constants.NO_VERSION;

      //
      // Upload the object if it had local mods
      if (localChanges) {
        if( dlRequest.getRemoteStatus().getLock() == null )
          Log.log("Expected to have lock here; FIXME-P: In branch switching not yet...",Log.ERROR);
        //Log.log("To p3 with uploads", Log.INFO);
        if( __isDir__ )
         syncMsg(f,TransmitStatus.NO_STATUS,0x80+4);
        final InputStream uploadStream = f.getLinkInputStream();
        if( __isDir__ )
          syncMsg(f,TransmitStatus.NO_STATUS,0x80+2);
        transfer.upload(linkGUID, uploadStream,
                        merger.getMergedMetadata(),
                        localDataChange, localMetaChange, history,
                        Constants.NO_VERSION, // File is modded => has no version yet
                        dlRequest.getStatus().getVersionsAvailable(),
                        dlRequest.getRemoteStatus().getLock(),
                        new FileTransfer.UploadCompletionHandler() {
                          public void uploadComplete(FileTransfer.UploadRequestBase ulReq) {
                            ulRequest = ulReq;
                            try { uploadStream.close(); }
                            catch (IOException x) {
                              Log.log("Can't close upload stream", Log.FATALERROR);
                            }
                            nextPhase.run();
                          }
                        } );
        nextPhase.willRun();
      } else {
        //Log.log("To p3 with NO uploads", Log.INFO);
        ulRequest = null; // No upload
        nextPhase.run();
      }
      return TransmitStatus.NO_STATUS; // phase 3
    }

    public int synchronize3(Phase nextPhase) throws IOException, SynchronizationException {
      nextPhase.dontRun(); // This was the last phase (cleanup handled separately)
      if (ulRequest != null) {
        if( __isDir__ )
         syncMsg(f,TransmitStatus.NO_STATUS,0x80+5);
        Log.log("Sync upload done, stat=", Log.INFO, ulRequest.getStatus());
        if (ulRequest.getStatus().getStatus() != TransmitStatus.OK) {
          Log.log("Upload failed", Log.WARNING);
          return ulRequest.getStatus().getStatus(); // Upload failed, give up
        }
        // Use version given by server for commit
        newLinkVersion = ulRequest.getStatus().getVersion();
        // FIXME-joins: joins stuff
      if( !Version.TRUNK.equals(f.getBranch()) ) {
          MetadataImpl jmd = MetadataImpl.createFrom( f.getMetadata() );
          jmd.setJoins(new Version(newLinkVersion,
                                   f.getBranch()).addToJoins(jmd.getJoins()));
          Log.log("Client pos-ul: Updating joins to " + jmd.getJoins(), Log.INFO);
          f.setMetadata(jmd);
        }
      }
      // If there were any changes, set linkVer to the new version we're synced
      // with
      if (localChanges || remoteChanges) {
        if (newLinkVersion == Constants.NO_VERSION)
          Log.log("No new version obtained, although changes", Log.FATALERROR);
        f.commitLink(newLinkVersion, getData, getMetadata);
      } else if( ulRequest != null )
        Log.log("Uploaded, but not committing. Should not happen?!",Log.ASSERTFAILED);

      Log.log("Sync done:" + f + 
          ": DV=" + f.getFullMetadata().getDataVersion() +
          ", linkDV="+ f.getLinkDataVersion() +
          ": MV=" + f.getFullMetadata().getMetaVersion() +
          ", linkMV="+ f.getLinkMetaVersion(),  Log.INFO);
      return TransmitStatus.OK;
    }

    boolean __wasCleaned = false;

    public synchronized void cleanup() { // Cleanup any initialized resources
      syncMsg(f,retval,__isDir__ ? 0x02 : 0x03);
      Progress.oneDone();
      if (tempStorage != null)
        tempStorage.delete();
      if( l!= null )
        l.release();
      tempStorage = null;
      l = null;
      if(__wasCleaned) // Safety check
        Log.log("Was already cleaned!",Log.ASSERTFAILED);
      __wasCleaned = true;
      releaseOnce(sync1Done);
      releaseOnce(sync2Done);
      releaseOnce(syncDone);
    }


    // Return new version of data. NO_VERSION => we have new data

    protected int agreeOnData(boolean localChange, boolean remoteChange,
                              FileTransfer.DownloadRequestBase r,
                              int currentLinkVer,
                              ObjectMerger merger,
                              VersionHistory history,
                              SyxawFile f, BLOBStorage data) throws IOException,
        ObjectMerger.Conflict {
      int peerVersion = r.getStatus().getVersion();
      return merger.mergeData(localChange, remoteChange, peerVersion, 
          data, r.getDataRef());
      
    }

    protected int agreeOnMeta(boolean localChange, boolean remoteChange,
                              FileTransfer.DownloadRequestBase r,
                              int currentLinkVer,
                              ObjectMerger merger,
                              VersionHistory history,
                              SyxawFile f) throws IOException,
        ObjectMerger.Conflict {
      int peerVersion = r.getStatus().getVersion();
      return merger.mergeMetadata(localChange, remoteChange, peerVersion,
                                  r.getMetadata(), r.getMetadataRef());

    }

    protected void reportConflict(fc.syxaw.fs.SyxawFile f,
                                  Metadata md,
                                  InputStream data,
                                  InputStream clog,
                                  String link,
                                  int linkVer) {
      Log.log("Reporting conflict for " + f, Log.INFO);
      OutputStream os = null;
      try {
        // FIXME: Ugly casting due to internal separation into fs.SyxawFile
        // & hierfs.SyxawFile
        SyxawFile cdatafile =
            (SyxawFile) ( (ISyxawFile) f).getConflictingFile();
        SyxawFile clogfile =
            (SyxawFile) ( (ISyxawFile) f).getConflictLog();
        os = cdatafile.getOutputStream(false);
        if(data != null )
          Util.copyStream(data, os);
        else
          os.write("Data not available.".getBytes());
        os.close();
        os = null;
        cdatafile.setMetadata(md);
        cdatafile.setLink(link,
                          new Integer(linkVer),
                          new Boolean(false), new Boolean(false), true, true);
        if (clog != null) {
          os = clogfile.getOutputStream(false);
          Util.copyStream(clog, os);
          os.close();
          os = null;
        }
      }
      catch (IOException x) {
        // Can possibly be changed to nonfatal (the world doesn't end just
        // in case we lose the conflict data)
        Log.log("Fatal error writing conflict file", Log.FATALERROR, x);
      }
      finally {
        if (os != null)
          try {
            os.close();
          }
          catch (IOException x) {
            Log.log("Fatal file close error", Log.FATALERROR, x);
          }
      }
    }
  }

  // Sync progress messages code. ONLY FOR XFS right now

  private BoundedLinkedQueue syncmsgs =
      Boolean.getBoolean("syxaw.debug.syncprogress") == true ?
      new BoundedLinkedQueue(64) : null;
  private String[] syncmtexts = {"scan","." /*DL*/ ,"." /*UL */,
      "." /*dl-done,merge*/,"." /*commit done*/,"." /*ulDone*/ };

  private void syncMsg(SyxawFile f, int status, int phase) {
    if( syncmsgs == null )
      return;
    if( phase > 0x80 ) {
      putSyncMsg(syncmtexts[phase&0x7f]);
      return;
    }
    try {
      String msg="";
      String stat;
      switch(status) {
        case TransmitStatus.NO_STATUS: stat = "";
          break;
        case TransmitStatus.CONFLICT: stat = "CONFLICT";
          break;
        case TransmitStatus.SEE_SUBSTATUSES: stat = "Ok, see substatus";
          break;
        case TransmitStatus.OK: stat = "Ok";
          break;
        case TransmitStatus.NOT_FOUND: stat = "NOT FOUND";
          break;
        default:
          if( status < 0 )
            stat = "Error "+status;
          else
            stat ="Ok (with unknown modifier)";
      }
      if( (phase &0x01)!=0) {
        fc.syxaw.storage.hfsbase.AbstractSyxawFile sf =
            (fc.syxaw.storage.hfsbase.AbstractSyxawFile) f;
        msg+= (sf.isDirectory() ? "Directory tree " + sf.toString() :
                     (sf.getNameFor() ))+ "...";/* + sf.getFullMetadata().getLength() +
                      " bytes")));/* +
                   (Util.isEmpty(stat) ? "" :
                    (", " + stat)));*/
      }
      if( (phase &0x02)!=0) {
        long len = f.getFullMetadata().getLength();
        msg+= " "+ (len >=0 ?
                    f.getFullMetadata().getLength()+" bytes, " : "")+
            (Util.isEmpty(stat) ? "" :
                    ( stat))+".\n";
      }
      putSyncMsg(msg);
    } catch (FileNotFoundException ex) {
      ; // Deliberately empty
    }

  }

  public String getSyncMsg() throws InterruptedException {
    return syncmsgs != null ? (String) syncmsgs.take() : null;
  }

  private void putSyncMsg(String msg) {
    try {
      if (!syncmsgs.offer(msg, 0)) {
        String missed = (String) syncmsgs.take();
        Log.log("Missed message " + missed, Log.WARNING);
        syncmsgs.offer(msg, 0);
      }
    } catch (InterruptedException ex) {
      // Deliberately empty
    }
  }

  public List findBranches( GUID g ) {
    RPC r = RPC.getInstance();
    g= new GUID("buzz/"); // Assume looking for buzz/DBO for now
    final RPC.Message ms = new RPC.Message(new Proto.FindReq(g.toString(),
            GUID.getCurrentLocation()));
    String hub = (String) fc.syxaw.transport.Config.LID_HOST_MAP.get("rpc-hub");
    r.send(hub == null ? "buzz" : hub ,ms);
    List l = r.collect(new RPC.Acceptor() {
      public boolean test(fc.syxaw.proto.RPC.Message m) {
        return ms.id == m.id && m.m instanceof Proto.FindRep;
      }
    }, 2000);
    List bl = new LinkedList();
    for (Iterator i = l.iterator(); i.hasNext(); ) {
      RPC.Message m = (RPC.Message) i.next();
      bl.addAll(Version.parseJoins(((Proto.FindRep) m.m).branches));
    }
    return bl;
  }

  // Map of <branch,link> (we only use "",original_link for now)
  // Kludge to re-obtain the original LID when re-joining the trunk, as that
  // is expressed as e.g. join 1001 (no LID given)
  // perhaps full versions should include the root LID?
  private Map FIXMEPsavedLinks = new HashMap();

  public boolean FIXMEPbaseIsCurrentLocalFacet = false; // global signal for
     // file re-link to forget any local change flags
     // This kludges the following problem for a dependent object a and
     // three devices S,A,B; B** = flags on B
     // 1. S-x = A:-- = B:x- = 1
     // 2.       A:-x = B:x- = 2 (on branch co-operation)
     // 3. S-x        = B:x-     (proxy commit)
     // 4. S-A sync: A has the linkmModFlag set, but these mods are already on S
     // FIXMEPbaseIsCurrentLocalFacet signals that the linkModFlag may be forgotten,
     // since the changes it flags are already on the server
     // The problem to determine if a change flag denotes changes after a
     // set of joins will need to be solved. It probably suffices to solve the
     // problems for local full versions only.

  public void switchBranch( SyxawFile f, Version bv ) throws IOException {
    // Save (branch,link) combination for recall
    FIXMEPsavedLinks.put(f.getFullMetadata().getBranch(),f.getLink());
    ///broken if( !f.getUid().equals( UID.DBO ) )
    ///    throw new NotInPrototypeException();
    Version current = null;
    if (getMetadata && !getData) {
      current = new Version(f.getLinkMetaVersion(),f.getBranch());//FIXME-versions:
    }else {
      current = new Version(f.getLinkDataVersion(),f.getBranch());//FIXME-versions:
    }
    boolean downswitch = bv.getAncestor().equals(current);
    boolean upswitch = current.getAncestor().equals(bv);
    if( !downswitch && !upswitch ) {
      Log.log("Can't switch if base versions do not agree.",Log.WARNING);
      throw new IOException("Mismatching link versions (expected " +
                            bv.getBaseVersion() + ")");
    }
    String newLink = bv.getLID() == null ?
                     (String) FIXMEPsavedLinks.get(Version.TRUNK) :
                     (new GUID(bv.getLID(),UID.DBO)).toString();
    f.setBranch(bv.getBranch());
    // kludge: make sure the local ver for the branch we're switching onto
    // is known, i.e. switching onto x/did/y --> localVer is that of x
    if( downswitch ) {
      Version ancestor = bv.getAncestor();
      Set s = fc.syxaw.storage.hfsbase.LinkTree.FIXMEPFullVersionSet;
      for (Iterator i = s.iterator(); i.hasNext(); ) {
        Version v = (Version) i.next();
        if (v.equals(ancestor)) {
          bv.setLocalVersion(v.getLocalVersion());
          Log.log("Adding "+bv+" with localVer "+
                 bv.getLocalVersion()+ " to lHist",Log.INFO);
          s.add(bv);
          break;
        }
      }
    }
    // kludge ends
    if (upswitch) {
      // See if there are any more recent versions of the object;
      // in that case set modflags to true
      // e.g. joining 1000 and 1000/rex/1001, 1000/rex/1002 exist
      // -> signal modification so that 1000/rex branch changes will
      // be merged
      List history = f.getLinkVersionHistory(getMetadata && !getData).getFullVersions(null);
      // note: Use ix -2 to ensure there has been >=1 commit = changes after
      // the switch onto the branch
      if (history.size() > 1 &&
          ((Version) history.get(history.size() - 2)).compareTo(bv) > 0  ) {
        f.setLink(null,new Boolean(true), new Boolean(true), true, true);
      }
    }
    f.setLink(newLink, new Integer(bv.getNumber()), null, null, true, true);
    bv.setLocalVersion(f.getFullMetadata().getDataVersion());
  }

  // FIXME-P: Assumes we have all versions from the branch in the repo
  private Version reviseBase(VersionHistory vh,String joinss,
                             Version linkVersion) {
    Set joins = Version.parseJoins(joinss);
    int _dbg_jSize = joins.size();
    // BUGFIX-050913-2: reviseBase would erroneously return branch versions,
    // although we were on a never trunk version due to the fact that
    // trunk versions are not marked in the joins field.
    // FIXME-P: The true set of joins for a downloaded object
    // = joins field + dlVersion if dlVersion on trunk. It is confusing that
    // the joins field only contains non-trunk joins...?
    // We must do this since TRUNK versions are not in joins
    linkVersion.addToJoins(joins);
    // Safety check
    if( joins.size()>_dbg_jSize &&
        !Version.TRUNK.equals(linkVersion.getBranch()) )
      // Two possibilities here: 1) our linkVer > downloaded ver = we have something
      // never than the server most recent; this should be impossible
      // 2) Joins field for a branch version b does not contain b ->
      //    inconsistency in joins
      Log.log("Non-trunk join missing; join="+linkVersion+", joins="+joins,
              Log.ASSERTFAILED);
    List localLinkVers = vh.getFullVersions(null);
    Version newBase = Version.NO_VERSION; //defaultBase;
    Log.log("Joins are "+joins,Log.INFO);
    Log.log("List of link versions is "+localLinkVers,Log.INFO);

    for( Iterator j = joins.iterator();j.hasNext();){
      Version join = (Version) j.next();
      // Scan for a candidate that is 1) < or = join and 2)
      // never than the current candidate
      for (Iterator i = localLinkVers.iterator(); i.hasNext(); ) {
        Version candidate = (Version) i.next();
        if( (join.compareTo(candidate) == 0 ||
             join.compareTo(candidate) > 1 ) &&
            candidate.compareTo(newBase) > 1 )
          newBase = candidate;
      }
    }
    if( newBase.getNumber() == Constants.NO_VERSION )
      newBase = null; // No switch
    return newBase;
  }
}
// arch-tag: c2c00ea67c1a47cd031604bc8cfde7b9 *-
