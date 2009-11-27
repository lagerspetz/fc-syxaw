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

package fc.syxaw.util.syxrunner;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fc.util.log.Log;

public class SyxawN95Process extends SyxawProcess {

  protected Map properties = new HashMap();

  public SyxawN95Process(String host, int port, String name, File groupRoot) {
    super(host, port, name, groupRoot);
  }

  public void setProperties( Map props ) {
    properties = props;
  }

  protected String getClassPath() {
    return "C:\\Symbian\\7.0s\\S80_DP2_0_PP_SDK_b2\\epoc32\\wins\\c\\ppro-apps"+
            "\\s80-syxaw-compl.jar";
  }

  public void start() {
    String cmd = "";
    if( debug ) {
      cmd += "-Xdebug -Xnoagent -Djava.compiler=NONE " +
              "-Xrunjdwp:transport=dt_socket,address=localhost:" + debugPort +
              ",suspend=n,server=y";
      Log.log("Debug port for "+getName()+"is "+debugPort,Log.INFO);
      debugPort ++;
    }
    if( false ) {
      cmd += " -Dhttp.proxyHost=angmar" +
             " -Dhttp.noProxyHosts=" +
             " -Dhttp.proxyPort=50000";
    }
    cmd +=
            " -Dsyxaw.deviceid=" + getName() +
            " -Dsyxaw.cleanfs=true" +
            " -Dsyxaw.initfs=true" +
            " -Dsyxaw.rmiapi=false" +
            " -Dsyxaw.syncd=false" +
            " -Dsyxaw.http.requestport=23902";
    if (!properties.containsKey("syxaw.http.lidmap") ) {
      cmd +=" -Dsyxaw.http.lidmap=";
      for (int i = 0; i < NAMES.length; i++)
        cmd += (i > 0 ? ";" : "") + NAMES[i] + ":localhost:" + (BASE_PORT + i);
    }
      cmd +=" -Dsyxaw.nfsd=false" +
            " -Dsyxaw.cli=rdz" +
            " -Dsyxaw.cli.rendezvous=localhost:23902" +
            " -Dsyxaw.debug.dirmod=false" +
            " -Dsyxaw.rootfolder=." + //root +
            " -Dsyxaw.loglevel=99" +
            " -Dsyxaw.syncdependent=true" +
            " -Dsyxaw.debug.syncprogress=true " +
            " -Dsyxaw.xmldelta=false";

      for( Iterator i=properties.entrySet().iterator();i.hasNext();) {
        Map.Entry e = (Map.Entry) i.next();
        cmd += " -D"+e.getKey()+"="+e.getValue();
      }
      cmd +=" fc.pp.syxaw.fs.Syxaw";
    execInSubJava(System.getProperty("syxrunner."+getName()+".java",null),cmd,
        root);
  }

}

// arch-tag: 64f7fa44-c66a-4980-b858-dbf94302560c
