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

/**
 * 
 */
package fc.syxaw.storage.hierfs;

import fc.syxaw.storage.hfsbase.ForeignMetadata;
import fc.util.log.Log;
import fc.xml.xmlr.model.StringKey;

public class MutableMetadata extends Metadata {

  public static MutableMetadata create(Metadata md) {
    MutableMetadata mm = new MutableMetadata();
    mm.id=md.id;
    mm.linkModStamp=md.linkModStamp;
    mm.localModStamp=md.localModStamp;
    mm.link=md.link; 
    mm.type=md.type;
    mm.dataVersion=md.dataVersion;
    mm.metaVersion=md.metaVersion;
    mm.metaModTime=md.metaModTime;
    mm.hash=md.hash;
    mm.linkDataVersion=md.linkDataVersion;
    mm.linkMetaVersion=md.linkMetaVersion;
    mm.dkey=md.dkey;
    mm.joins=md.joins;
    mm.metaModStamp=md.metaModStamp;
    mm.branch=md.branch;
    return mm;
  }
  
  public void setLink(String aLink) {
    link = aLink;
  }

  public void setJoins(String aJoins) {
    joins = aJoins;
  }

  public void setType(String aType) {
    type = aType;
  }

  public void setDataVersion(long aVersion) {
    dataVersion = aVersion;
  }
  
  public void setMetaVersion(long aVersion) {
    metaVersion = aVersion;
  }

  public void setMetaModTime(long aMetaModTime) {
    metaModTime = aMetaModTime;
  }

  public void setHash(byte[] aHash) {
    hash = aHash;
  }

  public void setLinkDataVersion(long aVersion) {
    linkDataVersion = aVersion;
  }
  public void setLinkMetaVersion(long aVersion) {
    linkMetaVersion = aVersion;
  }

  public void setLinkModStamp(long aLinkModStamp) {
    linkModStamp = aLinkModStamp;
  }

  public void setLocalModStamp(long aLocalModStamp) {
    localModStamp = aLocalModStamp;
  }

  public void setId(StringKey id) {
    this.id = id;
  }

  public void setDkey(String aDkey) {
    dkey = aDkey;
  }

  public void setMetaModStamp(int metaModStamp) {
    int thestamp = metaModStamp
        & (ForeignMetadata.META_EQ_LINKVER | ForeignMetadata.META_EQ_LOCALVER);
    this.metaModStamp = thestamp;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }
}
// arch-tag:  4ecb4bc7-17f3-4046-866c-616703c8c59b
