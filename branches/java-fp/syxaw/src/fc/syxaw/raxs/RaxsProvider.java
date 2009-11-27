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

package fc.syxaw.raxs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.corba.se.impl.orbutil.closure.Constant;

import fc.raxs.RandomAccessXmlStore;
import fc.raxs.RaxsConfiguration;
import fc.syxaw.fs.BLOBStorage;
import fc.syxaw.fs.ChainedObjectProvider;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.FullMetadataImpl;
import fc.syxaw.fs.SyxawFile;
import fc.syxaw.storage.hfsbase.AbstractSyxawFile;
import fc.util.IOUtil;
import fc.util.log.Log;

public class RaxsProvider extends ChainedObjectProvider {

  /** MIME type for RAXS stores. The RAXS store MIME type is 
   * <code>{@value}</code>. */
  public static final String MIME_TYPE="application/xml+raxs";

  public RaxsProvider() {
  }

  /*!5 @Override */
  public FullMetadata getFullMetadata(SyxawFile f) throws FileNotFoundException {
    FullMetadataImpl md = 
      FullMetadataImpl.createFrom(chained.getFullMetadata(f));
    // Need to set the length, as the chained length is that of the directory 
    md.setLength(getContentFile(f).length());
    return md;
  }

  /*!5 @Override */
  public InputStream getInputStream(SyxawFile sf, boolean link)
      throws FileNotFoundException {
    return new FileInputStream(getContentFile(sf));
  }

  /*!5 @Override */
  public OutputStream getOutputStream(SyxawFile sf, boolean append, boolean link)
      throws FileNotFoundException {
    return new DeltaOutputStream(sf, getRaxs(sf), Constants.NO_VERSION, append);
  }
  
  protected File getContentFile(SyxawFile sf) throws FileNotFoundException {
    RaxsConfiguration cf;
    try {
      cf = getConfiguration(sf);
      return cf.getStoreFile(); 
    } catch (FileNotFoundException e) {
      throw e;
    } catch (IOException e) {
      Log.error(e);
      FileNotFoundException fne = new FileNotFoundException();
      fne.initCause(e);
      throw fne;
    }
  }
  
  /*!5 @Override */
  public fc.syxaw.fs.Repository getRepository(SyxawFile sf) throws FileNotFoundException {
    return new Repository(getRaxs(sf));
  }

  protected RandomAccessXmlStore getRaxs(SyxawFile sf) throws FileNotFoundException {
    try {
      return new RandomAccessXmlStore(getConfiguration(sf));
    } catch (FileNotFoundException e) {
      throw e;
    } catch (IOException e) {
      Log.error(e);
      FileNotFoundException fne = new FileNotFoundException();
      fne.initCause(e);
      throw fne;
    }
  }
  
  protected RaxsConfiguration getConfiguration(SyxawFile sf) throws IOException {
    File f = new File( ((AbstractSyxawFile) sf).getAbsolutePath() );
    RaxsConfiguration cf = RandomAccessXmlStore.getConfiguration(f, 
        RaxsProvider.MIME_TYPE);
    return cf;
  }

  /*!5 @Override */
  public void rebindStorage(SyxawFile sf, BLOBStorage s, boolean link)
      throws IOException {
    OutputStream out = null;
    try {
      out = getOutputStream(sf, false, link);
      InputStream in = s.getInputStream();
      IOUtil.copyStream(in, out);      
      s.delete();
      in.close();
    } finally {
      if( out != null) {
        out.close();
      }
    }
  }
}

// arch-tag: 1be29e9a-d783-4d36-afda-e2821269f3ef
