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

// $Id: BLOBStorageImpl.java,v 1.2 2004/12/02 11:01:05 ctl Exp $
package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import fc.syxaw.fs.BLOBStorage;
import fc.util.log.Log;

/** Generic BLOBStorage implementation for hierarchical file
 * systems.
 */


public class BLOBStorageImpl extends BLOBStorage {

  private boolean isTemporary;
  private File f;
  private OutputStream out=null;

  public BLOBStorageImpl( File storefile, boolean aisTemporary ) throws IOException {
    f = storefile;
    isTemporary = aisTemporary;
  }

  public BLOBStorageImpl( OutputStream out, boolean aisTemporary ) throws IOException {
    this.out = out;
    f = null;
    isTemporary = aisTemporary;
  }

  public InputStream getInputStream() {
    if( out != null )
      return null;
    try {
      InputStream is =
       new java.io.FileInputStream(f) {
        public void close() throws IOException {
          super.close();
          fc.syxaw.util.Log.freeres(this);
        }
      };
      if( is == null )
        throw new IOException("No input stream obtained");
      fc.syxaw.util.Log.allocres(is,f.toString());
      return is;
    } catch (IOException x ) {
      Log.log("BLOBStorage bombed",Log.FATALERROR,x);
    }
    return null;
  }

  public OutputStream getOutputStream(boolean append) {
    if( out != null )
      return out;
    try {
      OutputStream out =
      new java.io.FileOutputStream(f.toString(),append) {
        public void close() throws IOException {
          super.close();
          fc.syxaw.util.Log.freeres(this);
        }
      };
      if( out == null )
        throw new IOException("No output stream obtained");
      fc.syxaw.util.Log.allocres(out,f.toString());
      return out;
    } catch (IOException x ) {
      Log.log("BLOBStorage bombed",Log.FATALERROR,x);
    }
    return null;
  }

  void fileWasRebound() {
    f=null;
  }

  public void delete() {
    if( out != null )
      return;
    if( f!= null && f.exists() && !f.delete() )
        //BUGFIX 05-08-15: f does not necessarily exist, check that too!
      Log.log("Can't delete temp file",Log.ERROR);
    f= null;
  }

  protected void finalize() {
    if( out != null )
      return;
    if( f!=null && isTemporary && f.exists() ) {
      if (!f.delete())
        Log.log("Can't delete orphaned temp file "+f, Log.ERROR);
      else
        Log.log("Deleted orphaned temp file "+f, Log.WARNING);
    }
  }
}
// arch-tag: 0724a299151b89aec231b078341d12fc *-
