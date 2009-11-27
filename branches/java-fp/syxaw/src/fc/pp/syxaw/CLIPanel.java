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

package fc.pp.syxaw;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import fc.syxaw.storage.hfsbase.ServerConfig;
import fc.util.log.Log;

// No javadoc needed
public class CLIPanel extends Panel {
  public CLIPanel() {
    try {
      jbInit();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void jbInit() throws Exception {
    this.setLayout(borderLayout1);
    txtCLI.setEditable(false);
    btnSync.setActionCommand("sync");
    btnSync.setLabel("Sync");
    btnSync.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        btnSync_actionPerformed(e);
      }
    });
    btnRun.setActionCommand("run");
    btnRun.setLabel("Run:");
    btnRun.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        btnRun_actionPerformed(e);
      }
    });
    textField1.setColumns(25);
    pnlcmd.setLayout(flowLayout1);
    lblResources.setText("0000000/0000000");
    flowLayout1.setAlignment(FlowLayout.LEFT);
    this.add(pnlcmd, java.awt.BorderLayout.NORTH);
    pnlcmd.add(btnSync);
    pnlcmd.add(btnRun);
    pnlcmd.add(textField1);
    pnlcmd.add(lblResources);
    this.add(txtCLI, java.awt.BorderLayout.CENTER);
    (new OutputWriter()).start();
    //textField1.setText("ln alchemy.hiit.fi/ "+ServerConfig.ROOT_FOLDER);
  }

  Panel pnlcmd = new Panel();
  BorderLayout borderLayout1 = new BorderLayout();
  TextArea txtCLI = new TextArea();
  Button btnSync = new Button();
  Button btnRun = new Button();
  TextField textField1 = new TextField();
  FlowLayout flowLayout1 = new FlowLayout();

  public void btnSync_actionPerformed(ActionEvent e) {
    execCmd( "sync " + ServerConfig.ROOT_FOLDER_FILE.getAbsolutePath() );
  }

  protected void execCmd(String s) {
    try {
      toCLI.write((s+"\n").getBytes());
    } catch (IOException ex) {
      Log.log("Cant run cmd: "+s,Log.ERROR,ex);
    }
  }

  public void btnRun_actionPerformed(ActionEvent e) {
      execCmd( textField1.getText() );
  }

  protected PipedOutputStream toCLI=new PipedOutputStream();
  protected PipedInputStream fromCLI=new PipedInputStream();

  protected byte[] readBuf = new byte[512];
  Label lblResources = new Label();

  public InputStream getCLIInputStream() throws IOException {
    return new PipedInputStream(toCLI);
  }

  public OutputStream getCLIOutputStream() throws IOException {
    return new PipedOutputStream(fromCLI);
  }

  public class OutputWriter extends Thread {
    public void run() {
      try {
        while (true) {
          //txtCLI.setCaretPosition(Integer.MAX_VALUE); // Broken on N9500
          if (fromCLI.available() == 0) {
            long maxmem = Runtime.getRuntime().totalMemory();
            long freemem = Runtime.getRuntime().freeMemory();
            String memstat = ""+(maxmem-freemem)/1024+"/"+maxmem/1024+"";
            lblResources.setText(memstat);
            try { Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
            //Log.log("CLI Wait...",Log.INFO);
          }
          for (int len = -1; (len = fromCLI.read(readBuf, 0,
                                                 Math.min(fromCLI.available(),
                  readBuf.length))) > 0; ) {
            String s = txtCLI.getText()+new String(readBuf, 0, len);
            if( s.length() > 500 )
              s = s.substring(s.length()-500);
            //Log.log("Read "+s,Log.INFO);
            txtCLI.setText(s);
          }
        }
      } catch (IOException ex1) {
        Log.log("Error reading CLI output",Log.ERROR,ex1);
      }
    }
  }
}

//arch-tag: 20050802102343ctl
