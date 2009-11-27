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
import java.io.IOException;
import java.io.OutputStream;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

import fc.util.log.Log;

import bsh.util.JConsole;

public class SyxawPanel extends JPanel implements Runnable {

  SyxawProcess syxaw=null;
  SocketCommandProcessor cp=null;
  OutputStream cmdout = null;
  boolean waitForMarkers = true;

  public SyxawPanel() {
    try {
      jbInit();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public SyxawPanel(SyxawProcess p) {
    this();
    syxaw = p;
    cmdout = new DiagStream(Color.blue);
    cp = new SocketCommandProcessor(new DiagStream(Color.magenta),
                         new SocketCommandProcessor.SyncOutputStream( cmdout),
                                    syxaw.getHost(), syxaw.getCLIPort());

    (new Thread(this)).start();
    OutputStream logs = new TextAreaOutputStream( logPane );
    p.setStderr(logs);
    p.setStdout(logs);
  }

  public void breakCommandWait() {
    cp.breakCommandWait();
    consolePane.print("**Broke CLI wait**",Color.red);
  }

  public void setMarkerWait(boolean wait ) {
    waitForMarkers = wait;
  }

  private class DiagStream extends OutputStream {

    Color c;

    public DiagStream( Color c) {
      this.c = c;
    }

    public void write( int achar ) {
      consolePane.print(String.valueOf(achar),c);
    }
    public void write( byte[] buf, int off, int len ) {
      consolePane.print(new String(buf,off,len),c);
    }

  }

  static class TextAreaOutputStream extends OutputStream {
      JTextArea area;

      public TextAreaOutputStream(JTextArea area) {
          this.area = area;
      }

      public void write(int b) throws IOException {
          area.append(String.valueOf(b) );
          area.setCaretPosition(area.getText().length());
      }

      public void write(byte[] b) throws IOException {
          area.append(new String(b) );
          area.setCaretPosition(area.getText().length());

      }

      public void write(byte[] b, int off, int len) throws IOException {
          area.append(new String(b,off,len) );
          area.setCaretPosition(area.getText().length());

      }

  }

  public void exec(String cmd) {
    long now = System.currentTimeMillis();
    cp.exec(cmd, true, true);
    //Log.log("Begin cmd wait",Log.INFO);
    Log.log("Command done in " + (System.currentTimeMillis() - now) + "ms",
            Log.DEBUG);
  }

  public void run() {
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader ( consolePane.getInputStream() ) );
    String cmd = null;
    //consolePane.println("-- Syxaw GUI CLI --");
    //consolePane.println("\"You're on your own!\"");
    try {
      while( ( cmd = promptAndRead(r) ) != null ) {
        try {
          long now=System.currentTimeMillis();
          cp.exec(cmd,false,true);
          cmdout.flush();
          if( waitForMarkers )
            consolePane.print("cmd "+(System.currentTimeMillis()-now)+"> ",Color.MAGENTA);
        } catch (Exception e) {
          consolePane.println("Shell excepted.");
          e.printStackTrace();
        }
      }
    } catch (java.io.IOException e) {
        consolePane.println("IOException while interfacing with consolePane");
    }
  }

  protected String promptAndRead( java.io.BufferedReader r) throws java.io.IOException {
//    console.print("> ");
//    System.out.print("> ");
//    System.out.flush();
    String cmd=r.readLine();
    // Undo beanshell hack of adding ; to empty lines
    return ";".equals(cmd) ? "" : cmd;
  }

  // Jb code
  private void jbInit() throws Exception {
    this.setLayout(borderLayout1);
    splitter.setOrientation(JSplitPane.VERTICAL_SPLIT);
    splitter.add(consolePane, JSplitPane.LEFT);
    JScrollPane jScrollPane1 = new JScrollPane();
    jScrollPane1.getViewport().add(logPane,java.awt.BorderLayout.CENTER);
    splitter.add(jScrollPane1, JSplitPane.RIGHT);
    this.add(splitter, java.awt.BorderLayout.CENTER);
    splitter.setDividerLocation(500);
    logPane.setEditable(false);
  }

  JSplitPane splitter = new JSplitPane();
  BorderLayout borderLayout1 = new BorderLayout();
  JTextArea logPane = new JTextArea();
  JConsole consolePane = new JConsole();

}

// arch-tag: 60e9846d-4a77-4f01-afd7-88a371271c14
