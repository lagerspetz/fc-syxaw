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

package fc.syxaw.storage.hfsbase;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fc.syxaw.fs.Constants;
import fc.syxaw.query.QueryProcessor;
import fc.syxaw.query.XmlrId;

public class QueryStreamFactory {

  private static final Map PROCESSORS;

  static {
    Map p = new HashMap();;
    p.put("xmlr-id/",new XmlrId());
    PROCESSORS = p;
  }
  
  public static void addQueryProcessor(String prefix, QueryProcessor processor){
    if (processor != null && prefix != null && !prefix.equals("") && !PROCESSORS.containsKey(prefix))
      PROCESSORS.put(prefix, processor);
  }

  public InputStream getInputStream(fc.syxaw.fs.SyxawFile f, String query,
                                    boolean linkFacet) throws
          FileNotFoundException {
    Map.Entry qp = getProcessor(query);
    if (qp != null)
      return ((QueryProcessor) qp.getValue()).getInputStream(f,
              query.substring(((String) qp.getKey()).length()),
                                   linkFacet);
    throw new FileNotFoundException(f.toString() +
                                    AbstractSyxawFile.querySeparator + query);
  }

  public OutputStream getOutputStream(fc.syxaw.fs.SyxawFile f, String query,
                                      boolean linkFacet) throws
          FileNotFoundException {
    Map.Entry qp = getProcessor(query);
    if (qp != null)
      return ((QueryProcessor) qp.getValue()).getOutputStream(f,
              query.substring(((String) qp.getKey()).length()),
                                   linkFacet);
    throw new FileNotFoundException(f.toString()+
                                    AbstractSyxawFile.querySeparator +query);
  }

  public long getLength(fc.syxaw.fs.SyxawFile f, String query,
                                      boolean linkFacet) throws
          FileNotFoundException {
    /*Map.Entry qp = getProcessor(query);
    if (qp != null)
      return ((QueryProcessor) qp.getValue()).getLength(f,
              query.substring(((String) qp.getKey()).length()),
                                   linkFacet);*/
    return Constants.NO_SIZE;
  }

  private Map.Entry getProcessor(String query) {
    for( Iterator i=PROCESSORS.entrySet().iterator();i.hasNext();) {
      Map.Entry qp = (Map.Entry) i.next();
      if(query.startsWith((String) qp.getKey()))
        return qp;
    }
    return null;
  }


}

// arch-tag: bcc86aa5-d5c0-4f33-b5fa-295fb0abee57
