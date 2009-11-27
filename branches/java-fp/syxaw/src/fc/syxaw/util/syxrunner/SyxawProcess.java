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
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fc.syxaw.fs.Config;
import fc.syxaw.util.Util;
import fc.util.log.Log;

public class SyxawProcess  {

  private static final String ARG_QUOTE =
      System.getProperty("os.name").toLowerCase().startsWith("windows") ?
      "\"" : "";

  public static final int BASE_PORT = 40000;

  public static Map NEW_PROCESS_PROPS = new HashMap();

  public static final String  NAMES[] = {"buzz","rex","bo","hamm","slink","potato",
  "woody","sarge","etch","sid"};

  protected OutputStream stdout=System.out;
  protected OutputStream stderr=System.err;
  protected String host;
  protected int port;
  protected int cliport;
  protected String name;
  protected File root;
  protected boolean debug=false;
  protected static int debugPort = 10000;

  public SyxawProcess(String host, int port, String name, File groupRoot) {
    root = new File(groupRoot,name);
    if( !root.exists() && !root.mkdir() )
      Log.log("Can't make folder "+root,Log.FATALERROR);
    this.host = host;
    this.port = port;
    this.cliport = port+1000;
    this.name = name;
  }

  // Dummy for remote processes
  public SyxawProcess(String host, int port, String name ) {
      this.host = host;
      this.port = port;
      this.name = name;
  }

  public void start() {
    String cmd = NEW_PROCESS_PROPS.containsKey("syxrunner.jvmargs") ?
        NEW_PROCESS_PROPS.get("syxrunner.jvmargs").toString()+" " : "";
    //A! assert (cmd+=" -ea")!=null; // Enable assertions if runner has assertions on
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
            " -Dsyxaw.syncd=true" +
            " -Dsyxaw.http.listenport=" + getPort() +
            " -Dsyxaw.http.requestport=" + (BASE_PORT );
    {
      cmd += " -Dsyxaw.http.lidmap=";
      cmd += NEW_PROCESS_PROPS.containsKey("syxaw.http.lidmap") ?
              (String) NEW_PROCESS_PROPS.get("syxaw.http.lidmap") :
              System.getProperty("syxaw.http.lidmap");
      cmd += " -Dsyxaw.storageprovider=";
      cmd += NEW_PROCESS_PROPS.containsKey("syxaw.storageprovider") ?
                  (String) NEW_PROCESS_PROPS.get("syxaw.storageprovider") :
                  Config.STORAGE_PROVIDER;
    }
    cmd += " -Dsyxaw.nfsd=false" +
            " -Dsyxaw.cli=tcp" +
            " -Dsyxaw.cliserver.port=" + getCLIPort() +
            " -Dsyxaw.cliserver.escapes=true" +
            " -Dsyxaw.debug.dirmod=false" +
            " -Dsyxaw.rootfolder=." + //root +
            " -Dsyxaw.loglevel=" + System.getProperty("syxaw.loglevel")+" "+
            " -Dsyxaw.syncdependent=true" +
            " -Dsyxaw.xmldelta=false";
      for( Iterator i=NEW_PROCESS_PROPS.entrySet().iterator();i.hasNext();) {
        Map.Entry e = (Map.Entry) i.next();
        cmd += " -D"+e.getKey()+"="+e.getValue();
      }
    // Copy fc properties
    for (Iterator i = System.getProperties().entrySet().iterator();i.hasNext();) {
      Map.Entry e = (Map.Entry) i.next();
      String key = e.getKey().toString(); 
      if (key.startsWith("fc")) {
        cmd += " -D"+key+"="+e.getValue().toString();
      }
    }
    execInSubJava(System.getProperty("syxrunner."+getName()+".java",null),
        cmd, root );
  }

  public static void initLidMap() {
    if( Util.isEmpty(System.getProperty("syxaw.http.lidmap")) ) {
      String lidmap = "";
      for (int i = 0; i < NAMES.length; i++) {
        lidmap += (i > 0 ? ";" : "") +
                NAMES[i] + ":" + "localhost" + ":" + (BASE_PORT + i);
      }
      lidmap +=";rpc-hub:localhost:39000";
      System.setProperty("syxaw.http.lidmap",lidmap);
      Log.log("Generated lidmap "+lidmap,Log.INFO);
    } else
      Log.log("Using lidmap "+System.getProperty("syxaw.http.lidmap"),Log.INFO);

  }

  protected String getClassPath() {
    return System.getProperty("java.class.path");
  }

  void execInSubJava(String java, String cmd, File home) {
    try {
      String javaHome = System.getProperty("java.home");
      if( debug && javaHome.toLowerCase().endsWith("jre") )
        javaHome = javaHome.substring(0,javaHome.length()-4); // Strip jre part
      cmd = (java != null ? java :
              javaHome +
            "/bin/java -cp " + ARG_QUOTE + getClassPath() +
             ARG_QUOTE ) + " " + cmd;
      cmd +=" "+ System.getProperty("syxrunner."+getName()+".main",
                                    //"fc.dessy.DessyMain");
                                    "fc.syxaw.fs.Syxaw"); // FIXME: change main here
      Log.log("PRE_INIT "+getName()+": Spawning " + cmd, Log.INFO);
      /*Log.log(
              "PRE_INIT: REMEMBER TO STOP BY File/Exit to kill all child processes!",
              Log.WARNING);*/
      final Process syxaw = Runtime.getRuntime().exec(cmd,null,home);
      // Pipe process stderr, stdout
      new Thread() {
        public void run() {
          try {
            Util.copyStream(syxaw.getErrorStream(), getStderr());
            getStderr().close();
          } catch (IOException ex) {
          }
        }
      }.start();
      // Pipe process stderr, stdout
      new Thread() {
        public void run() {
          try {
            Util.copyStream(syxaw.getInputStream(), getStdout());
            getStdout().close();
          } catch (IOException ex) {
          }
        }
      }.start();
      Runtime.getRuntime().addShutdownHook(new Thread() {
        public void run() {
          syxaw.destroy();
        }
      });
      //syxaw.waitFor();
    } catch (Exception ex) {
      Log.log("Can't spawn Syxaw process", Log.ERROR, ex);
    }

  }


  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public int getCLIPort() {
    return cliport;
  }

  public String getName() {
    return name;
  }

  public OutputStream getStdout() {
    return stdout;
  }

  public OutputStream getStderr() {
    return stderr;
  }

  public boolean isDebug() {
    return debug;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setCLIPort(int port) {
    this.cliport = port;
  }


  public void setName(String name) {
    this.name = name;
  }

  public void setStdout(OutputStream stdout) {
    this.stdout = stdout;
  }

  public void setStderr(OutputStream stderr) {
    this.stderr = stderr;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

}

// arch-tag: fe60fdff-c41a-4c6c-a4fb-a816523bdda4
