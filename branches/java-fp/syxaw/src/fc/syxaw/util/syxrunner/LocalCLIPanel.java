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

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.OutputStream;

import javax.swing.JPanel;

import fc.syxaw.tool.CommandProcessor;

import EDU.oswego.cs.dl.util.concurrent.Channel;
import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import bsh.util.JConsole;

public class LocalCLIPanel extends JPanel {

  private CommandProcessor cp = null;

  public LocalCLIPanel() {
    try {
      jbInit();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public LocalCLIPanel(CommandProcessor cp) {
    this();
    this.cp = cp;
    (new CommandPusher()).start();
    (new CommandPuller()).start();
  }

  private void jbInit() throws Exception {
    this.setLayout(borderLayout1);
    this.add(console, java.awt.BorderLayout.CENTER);
  }

  private class CommandPusher extends Thread {
    public void run() {
      java.io.BufferedReader r = new java.io.BufferedReader(
              new java.io.InputStreamReader(console.getInputStream()));

      String cmd;
      try {
        while ((cmd = promptAndRead(r)) != null) {
          try { cmdqueue.put(cmd); } catch (InterruptedException ex) {}
        }
      } catch (java.io.IOException e) {
        console.println("IOException while interfacing with console");
      }
    }
    protected String promptAndRead(java.io.BufferedReader r) throws java.io.
            IOException {
      String cmd = null;
      cmd = r.readLine();
      // Undo beanshell hack of adding ; to empty lines
      return ";".equals(cmd) ? "" : cmd;
    }

  }

  private  class CommandPuller extends Thread {

    //CommandProcessor cp = FrameworkCommandProcessor.getInstance();

    public void run() {

      String cmd = null;
      console.println("Framework CLI");
      //console.println("\"You're on your own!\"");
      OutputStream cout = new OutputStream() {
        public void write(int achar) {
          console.print(String.valueOf(achar), Color.blue);
        }

        public void write(byte[] buf, int off, int len) {
          console.print(new String(buf, off, len), Color.blue);
        }

      };
      try {
        while ((cmd = (String) cmdqueue.take()) != null) {
          try {
            cp.exec(cmd, cout);
          } catch (Exception e) {
            console.println("Shell excepted.");
            e.printStackTrace();
          }
        }
      } catch (InterruptedException e) {
        console.println("IOException while interfacing with console");
      }

    }

  }

  public void exec(String cmd) {
    console.print(cmd+"\n",Color.MAGENTA);
    try { cmdqueue.put(cmd); } catch (InterruptedException ex) {}
  }

  Channel cmdqueue = new LinkedQueue(); // new BoundedBuffer(2);
  JConsole console = new JConsole();
  BorderLayout borderLayout1 = new BorderLayout();
}

// arch-tag: 1520822e-a40f-4208-8aaf-aad492d90ec5
