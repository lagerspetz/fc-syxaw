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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import fc.syxaw.util.Util;

public class SimpleContainer implements ProcessContainer {

  LabeledOutputStream clis = new LabeledOutputStream(System.out);
  LabeledOutputStream logs = null;

  Set processes = new HashSet();
  Map processByName = new HashMap();
  FrameworkCommandProcessor fcp = new FrameworkCommandProcessor(this);

  public SimpleContainer() {
    logs = new LabeledOutputStream(System.err);
    clis = new LabeledOutputStream(System.out);
  }

  public SimpleContainer(LabeledOutputStream logs, LabeledOutputStream clis) {
    this.logs = logs;
    this.clis = clis;
  }

  public void rexec(PrintStream pw, String name, String cmd) {
    Process p = (Process) processByName.get(name);
    p.cp.exec(cmd,true,true);
  }

  public void addInstance(SyxawProcess sp) {
    processes.add(sp);
    String fname = Util.format(sp.getName(),-6,' ');
    OutputStream os = clis.getLabeledStream("["+fname+"] ");
    OutputStream los = logs.getLabeledStream("["+fname+"]");
    sp.setStderr(los);
    sp.setStdout(los);
    SocketCommandProcessor cp = new SocketCommandProcessor(os,
            new SocketCommandProcessor.SyncOutputStream(os),
            sp.getHost(), sp.getCLIPort());
    processByName.put(sp.getName(),new Process(sp,cp));
  }

  public void killInstance(String name) {
    Process p = (Process) processByName.get(name);
    p.cp.exec("shutdown",true,false);
    try{ Thread.sleep(1000); } catch ( InterruptedException ex ) {};
    p.cp.kill();
    processByName.remove(name);
    processes.remove(p.sp);
  }

  public void breakCommandWait(PrintStream pw, String name) {
    Process p = (Process) processByName.get(name);
    p.cp.breakCommandWait();
  }

  public void setTitle(String title) {
    // Nop so far
  }

  private static class Process {
    public SyxawProcess sp;
    public SocketCommandProcessor cp;
    public Process(SyxawProcess sp, SocketCommandProcessor cp) {
      this.sp=sp;
      this.cp=cp;
    }
  }

  public Collection getInstanceNames() {
    return new HashSet(processByName.keySet());
  }

  public static void main(String[] args) {
    LabeledOutputStream logs = new LabeledOutputStream(System.err);
    fc.syxaw.util.Log.setOut( new PrintStream( logs.getLabeledStream("[master] ")));
    SyxawProcess.initLidMap();
    SimpleContainer pc = new SimpleContainer();
    OutputStream os = pc.clis.getLabeledStream("[master] ");
    pc.fcp.exec("cleanimage buzz",os);
    pc.fcp.exec("create buzz",os);
    try {
      pc.fcp.exec(":buzz zang", os);
    } catch ( Throwable t ) {
      System.err.println("GOT EX: "+t.getMessage());
    }
    pc.fcp.exec("kill buzz",os);
    pc.fcp.exec("cleanimage buzz",os);
    pc.fcp.exec("create buzz",os);
    pc.fcp.exec("kill buzz",os);
  }
}

//arch-tag: abd6f7ff-529f-4fa8-ae7f-95d0ba7ef61c
