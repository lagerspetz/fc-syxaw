package fc.util.bytedb;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;

import fc.util.log.Log;

/**
 * Encapsulates Sdbm as a generic byte storage system.
 * Handles exceptions of Sdbm so that classes using this need not worry about them.
 * If you need exceptions, use Sdbm directly.
 * @author Eemil Lagerspetz
 */
public class SdbmByteDb implements ByteDb{
  Sdbm db = null;
  File f = null;
  public static final int PAIRMAX = Sdbm.PAIRMAX;
  
  public ByteDb create(File f, boolean cached) {
    return new SdbmByteDb(f, cached); 
  }
  public SdbmByteDb(){
  }
  
  public SdbmByteDb(File f, boolean cached){
    try {
      this.f = f;
      db = new Sdbm(f, "db", "rw");
    } catch (IOException e) {
     Log.log("Could not create Sdbm in " + f, Log.FATALERROR);
    }
  }
  
  public void delete(byte[] key) {
    try {
      db.remove(key);
    } catch (IOException e) {
      Log.log("Could not delete value for key " + key, Log.ERROR);
    }
  }

  public byte[] lookup(byte[] key) {
    try {
      return db.get(key);
    } catch (IOException e) {
      Log.log("Could not look up " + key, Log.ERROR);
    }
    return null;
  }

  public void update(byte[] key, byte[] value) {
   try {
    db.put(key, value);
  } catch (SdbmException e) {
    Log.log("Could not write key " + key + " value " + value, Log.ERROR);
  } catch (IOException e) {
    Log.log("Could not write key " + key + " value " + value, Log.ERROR);
    e.printStackTrace();
  } 
  }
  public void close() {
    try {
      db.close();
    } catch (IOException e) {
      Log.log("Could not close Sdbm at " + f, Log.ERROR);
    }
  }
  public Enumeration keys() {
    return db.keys();
  }
  
}
