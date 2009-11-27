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

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import fc.raxs.ParseException;
import fc.xml.xas.Comment;
import fc.xml.xas.EndTag;
import fc.xml.xas.Item;
import fc.xml.xas.ItemSource;
import fc.xml.xas.ItemTarget;
import fc.xml.xas.Qname;
import fc.xml.xas.StartTag;
import fc.xml.xas.TransformSource;
import fc.xml.xas.transform.DataItems;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.KeyIdentificationModel;
import fc.xml.xmlr.model.TreeModels;
import fc.xml.xmlr.model.XasCodec;
import fc.xml.xmlr.xas.PeekableItemSource;
import fc.xml.xmlr.xas.XasSerialization;

public class VersionMap {

  public static final String MAP_NS = "http://www.hiit.fi/fc/syxaw/version-map";
  public static final Qname MAP_ROOT_TAG = new Qname(MAP_NS,"version-map");
  public static final Qname MAP_ENTRY_TAG = new Qname(MAP_NS,"version");
  public static final Qname MAP_LINKVER_ATTR = new Qname("","link-version");
  public static final Qname MAP_VER_ATTR = new Qname("","version");

  public static final XasCodec XAS_CODEC = new MapCodec();
  
  protected SortedMap /*!5 <Integer,Integer> */ verMap = new TreeMap /*!5 <Integer,Integer> */ ();
  
  public static VersionMap read(ItemSource is) throws IOException {
    RefTree t = XasSerialization.readTree( new TransformSource(
        is, new DataItems() ), 
        TreeModels.xasItemTree().swapCodec(XAS_CODEC));
    return (VersionMap) t.getRoot().getContent();
  }
  
  public void write(ItemTarget t) throws IOException {
    RefTree rt = RefTrees.getRefTree(this);
    XasSerialization.writeTree(rt, t, XAS_CODEC);
  }
  
  private static class MapCodec implements XasCodec {

    private static int CODEC_VER = 1;
    
    public Object decode(PeekableItemSource is, 
        KeyIdentificationModel kim) throws IOException {
      VersionMap vm = new VersionMap();
      StartTag entryTag = new StartTag(MAP_ENTRY_TAG);
      EndTag entryETag = new EndTag(MAP_ENTRY_TAG);
      Item ri = is.peek();
      if( !(new StartTag(MAP_ROOT_TAG)).equals(ri) )
        throw new ParseException("Expected ",new StartTag(MAP_ROOT_TAG),is);
      for( Item i = is.next(); (i=is.next()).equals(entryTag); ) {
        StartTag st = (StartTag) i;
        String linkVer = st.getAttributeValue(MAP_LINKVER_ATTR).toString();
        String version = st.getAttributeValue(MAP_VER_ATTR).toString();
        if( linkVer == null || version == null)
          throw new ParseException("Missing key or value attribute for ",st,is);
        try {
          vm.add( Integer.valueOf( linkVer ), Integer.valueOf( version ) );
        } catch (NumberFormatException e) {
          throw new ParseException("Bad integer value in ("
              +linkVer+","+version+")",st,is);
        }
        if( !entryETag.equals( is.next() ) )
          throw new ParseException("Expected end tag ",entryETag);
      }
      if( !(new StartTag(MAP_ROOT_TAG)).equals(is.peek()) )
        throw new ParseException("Expected ",new EndTag(MAP_ROOT_TAG),is);
      return vm;
    }

    public void encode(ItemTarget t, RefTreeNode n, StartTag context) throws IOException {
      //!A assert n.getContent() instanceof VersionMap;
      VersionMap vm = (VersionMap) n.getContent();
      t.append(new Comment("Syxaw version map version "+CODEC_VER));
      t.append(new StartTag(MAP_ENTRY_TAG, context));
      for( Iterator i = vm.entrySet().iterator(); i.hasNext(); ) {
        Map.Entry /*!5<Integer,Integer>  */ e = (Map.Entry) i.next();
        StartTag st = new StartTag(MAP_ENTRY_TAG, context);
        st.addAttribute(MAP_LINKVER_ATTR, String.valueOf( e.getKey() ));
        st.addAttribute(MAP_VER_ATTR, String.valueOf( e.getValue() ));
        EndTag et = new EndTag(st.getName());
        t.append(st);
        t.append(et);
      }
      t.append(new EndTag(MAP_ENTRY_TAG)); // Not really needed
    }
  }


  public void add(Integer linkver, Integer ver) {
    verMap.put(linkver,ver);
  }


  public Set /*!5 <Map.Entry<Integer,Integer>> */ entrySet() {
    return verMap.entrySet();
  }

}

// arch-tag: c5055e77-2160-4195-a106-172ecbeacc0a
