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

//$Id: LockManager.java,v 1.7 2004/11/30 14:27:31 ctl Exp $

package fc.syxaw.fs;

import java.util.HashMap;
import java.util.Map;

import fc.syxaw.util.Cache;
import fc.syxaw.util.LruCache;
import fc.syxaw.util.SynchronizedCache;
import fc.util.log.Log;

import EDU.oswego.cs.dl.util.concurrent.ClockDaemon;
import EDU.oswego.cs.dl.util.concurrent.Mutex;

/** Class for managing soft-state locks. Syxaw uses soft-state locks to
 * lock objects during the synchronization cycle. See the documentation
 * for {@link LockManager.Lock} for a description of the semantics of
 *  the locks.
 */

public class LockManager {
  
  protected Map locksByToken = new HashMap();
  protected Map locksByUID = new HashMap();
  protected ClockDaemon expirationDaemon = new ClockDaemon();
  
  protected Cache expiredByUID = 
    new SynchronizedCache( new LruCache(Cache.TEMP_LOW,16) );
  
  /** Create a new instance.
   */
  
  public LockManager() {
  }
  
  /** Acquire lock for a file. If a lock in the soft-lock or mutex-lock
   * states exist for the file, that lock is returned; otherwise a new
   * lock is created. Note that the creation of a new lock for a file
   * permanently expires any other lock for the file.
   *
   * @param f SyxawFile to obtain a lock for
   * @return lock for the file
   */
  
  public synchronized Lock newLock(SyxawFile f) {
    Lock l = (Lock) locksByUID.get(f.getUid());
    if( l == null ) {
      l = new Lock(f);
      // New lock for this file -> old lock cannot be resurrected
      // Also note that we can't resurrect a lock here, as then
      // somebody could use the expired token to access the new lock!
      expiredByUID.remove(f.getUid());
    }
    return l;
  }
  
  /** Get lock by token. Retrieve a lock by its token. The operation
   * may (but might not!) resurrect a timed-out lock token if
   * no new locks have been issued for the file.
   *
   * @param token lock token
   * @param f expected file of the lock. This parameter is a precaution to
   *  test that the right lock is associated with the right file; if the
   *  file of the retrieved lock does not match, an failed assertion will
   *  occur.
   * @return retrieved lock
   */
  public Lock get(String token, SyxawFile f) {
    UID id = f.getUid();
    Lock l = (Lock) locksByToken.get(token);
    if( l == null ) {
      l = (Lock) expiredByUID.get(id); // Recycle old lock if possible
      if( l != null && l.getToken().equals(token) ) {
        // Resurrect lock
        expiredByUID.remove(id);
        locksByUID.put(id,l);
        locksByToken.put(l.getToken(),l);
        Log.log("Resurrected expired lock "+l.getToken(),Log.INFO);
      }
    }
    if( l!= null && !f.getUid().equals(l.f.getUid()) )
      Log.log("Invalid lock token for file",Log.ASSERTFAILED);
    return l;
  }
  
  private static long lc=1000000l;
  
  /** A soft-state lock for a file. The lock may be in three states: released,
   * soft-locked and exclusively locked.
   * <ul>
   * <li>When the lock is released, other locks for the file may be
   * obtained.</li>
   * <li>When the lock is soft-locked, no other locks for the file
   * may be soft-locked or exclusively locked.
   * <li>When the lock is exclusively held, the access to the file
   * is granted only to the holder of the exclusive lock.
   * </ul>
   * The lock will automatically be released from the soft-locked
   * state after {@link Config#LOCK_EXPIRATION_TIME LOCK_EXPIRATION_TIME}
   * milliseconds. Exclusive access to the lock is attempted for up
   * to {@link Config#LOCK_ATTEMPT_TIME LOCK_ATTEMPT_TIME} milliseconds.
   */
  public class Lock {
    
    private SyxawFile.Lock flock=null;
    private SyxawFile f;
    private String token;
    private Object expirationToken=null;
    private Mutex mutex = new Mutex();
    
    private Lock(SyxawFile f) {
      this.f = f;
      token="L"+String.valueOf(lc++);
      locksByToken.put(getToken(),this);
      locksByUID.put(f.getUid(),this);
    }
    
    /** Acquire exclusive access to the lock. As a side effect, the
     * file of the lock is locked for exclusive access.
     *
     * @return token to release the exclusive lock
     */
    public synchronized Object acquireExclusive() {
      boolean hasLock=false;
      try {
        hasLock = mutex.attempt(Config.LOCK_ATTEMPT_TIME);
      } catch (InterruptedException x) {
        // Delib empty
      }
      if( !hasLock ) {
        Log.log("Can't mutex " + f.getUid(), Log.FATALERROR);
        return null;
      }
      
      if( expirationToken != null ) {
        expirationDaemon.cancel(expirationToken);
        expirationToken = null;
      }
      flock = f.lock();
      //Log.log("AcquiredX "+getToken()+" for "+f.getUid(),Log.INFO);
      return mutex;
    }
    
    /** Release exclusive acces to the lock. As a side effect, exclusive
     * access to the file of the lock is released.
     *
     * @param l token returned by {@link #acquireExclusive() acquireExclusive}
     */
    public synchronized void releaseExclusive(Object l) {
      //Log.log("ReleaseX "+getToken()+" for "+f.getUid(),Log.INFO);
      releaseExclusive(l,true);
    }
    
    protected synchronized void releaseExclusive(Object l, boolean timedRelease) {
      if( l!=mutex)
        throw new IllegalArgumentException("Wrong token passed back");
      mutex.release();
      flock.release();
      flock=null;
      if( expirationToken !=null )
        Log.log("Should not have an expiration token here",Log.ASSERTFAILED);
      if(timedRelease )
        expirationToken = expirationDaemon.executeAfterDelay(Config.
            LOCK_EXPIRATION_TIME, new Runnable() {
          public void run() {
            release(true, null);
          }
        });
    }
    
    /** Release lock fully. Releases exclusive locking and soft-state
     * locking,
     * @param mex token returned by {@link #acquireExclusive()
     * acquireExclusive}, or <code>null</code> of the lock is not held
     * exclusively.
     */
    public synchronized void release(Object mex) {
      release(false,mex);
    }
    
    protected synchronized void release(boolean expired, Object mex) {
      if( mex == null ) {
        mex=acquireExclusive();
      }
      releaseExclusive(mex,false);
      Log.log("Release "+getToken()+" for "+f.getUid()+
          (expired ? " by expiration.":""),Log.INFO);
      locksByToken.remove(getToken());
      locksByUID.remove(f.getUid());
      expiredByUID.put(f.getUid(),this);
    }
    
    /** Get string token for the lock.
     * @return lock token
     */
    
    public String getToken() {
      return token;
    }
  }
  
  /** Lock that stores exclusive access token internally. This is
   * a convenience class that stores the token returned when acquiring
   * exclusive access.
   */
  
  
  public static class PrivateLock {
    
    Object mutex = null;
    Lock lock=null;
    
    /** Create a new private lock.
     *
     * @param l Lock to create the lock for
     */
    
    public PrivateLock(Lock l) {
      lock=l;
    }
    
    public Object acquireExclusive() {
      return mutex=lock.acquireExclusive();
    }
    
    /** Release lock fully. Releases exclusive locking and soft-state
     * locking,
     */
    
    public void release() {
      lock.release(mutex);
    }
    
    /** Release exclusive acces to the lock. As a side effect, exclusive
     * access to the file of the lock is released.
     */
    
    public void releaseExclusive() {
      lock.releaseExclusive(mutex);
    }
    
    /** Get exclusive access token.
     *
     * @return exclusive access token, or <code>null</code> if none
     *  acquired.
     */
    public Object getExclusiveToken() {
      if( mutex == null )
        throw new IllegalStateException("Lock not exclusively held.");
      return mutex;
    }
  }
}
//arch-tag: 457afeea52f1f3b70ac5db42ea5600fd *-
