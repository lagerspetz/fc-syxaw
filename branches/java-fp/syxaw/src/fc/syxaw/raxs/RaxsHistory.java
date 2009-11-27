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

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import fc.raxs.RandomAccessXmlStore;
import fc.raxs.VersionHistory;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.proto.Version;

public class RaxsHistory implements fc.syxaw.fs.VersionHistory {

  VersionHistory rh;
  
  public RaxsHistory(VersionHistory rh) {
    this.rh = rh;
  }

  public int getCurrentVersion() {
    return toSyxaw( rh.getCurrentVersion() );
  }

  public FullMetadata getMetadata(int version) {
    return null;
  }

  public InputStream getData(int version) {
    return rh.getData( toRaxs(version) );
  }

  public int getPreviousData(int version) {
    return rh.getPreviousData(toRaxs(version));
  }

  public int getPreviousMetadata(int version) {
    return rh.getPreviousData(toRaxs(version));
  }

  public List getFullVersions(String branch) {
    //!A assert branch == null || Version.CURRENT_BRANCH.equals(branch) ||
    //!A  Version.TRUNK.equals(branch) : "Other branch "+branch+" not coded.";
    List /*!5 <Integer> */ raxsList = rh.listVersions();
    LinkedList /*!5 <Version> */ verlist = new LinkedList /*!5 <Version> */();
    for( Iterator i=raxsList.iterator();i.hasNext();)
      verlist.add(new Version( toSyxaw( ((Integer) i.next()).intValue() ),Version.TRUNK));
    return verlist;
  }

  public boolean onBranch() {
    return false;
  }

  public boolean versionsEqual(int v1, int v2, boolean meta) {
    return rh.versionsEqual(toRaxs(v1),toRaxs(v2));
  }
  
  protected int toRaxs(int sver) {
    return sver == Constants.NO_VERSION ? RandomAccessXmlStore.NO_VERSION :
      sver;
  }
  
  protected int toSyxaw(int rver) {
    return rver == RandomAccessXmlStore.NO_VERSION ? Constants.NO_VERSION :
      rver;
  }
}

// arch-tag: 3cb663f2-91b5-44c0-886a-d7dabbc3801e

