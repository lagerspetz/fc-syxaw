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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Collection;
import java.util.LinkedList;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListModel;

import fc.syxaw.fs.GUID;
import fc.syxaw.proto.RPC;
import fc.syxaw.protocol.Proto;
import fc.util.log.Log;

public class GUI extends JFrame implements ProcessContainer {

  private LinkedList instanceList = new LinkedList();

  private ListModel peerNames = new AbstractListModel() {
    public int getSize() {
      return SyxawProcess.NAMES.length;
    }

    public Object getElementAt(int index) {
      return SyxawProcess.NAMES[index];
    }
  };

  private GUI(boolean dummy) {
    try {
      jbInit();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public GUI() {
    this(false);
    // Add files under run menu
    try {
      File f = new File("scripts");
      File[] list = f.listFiles();
      java.util.Arrays.sort(list);
      for( int i=0;i<list.length;i++) {
        if( !list[i].isFile() || list[i].getName().endsWith("~") ||
                list[i].getName().indexOf('#') != -1 )
          continue;
        JMenuItem mi = new JMenuItem(list[i].getName());
        mi.addActionListener(miRun.getActionListeners()[0]);
        miRun.add(mi);
      }
    } catch (Exception ex) {
      Log.log("Can't read scripts",Log.ERROR,ex);
    }
    (new FindRelay()).start();
    setSize(1000,800);
    setTitle("Syxaw test framework");
    setVisible(true);
  }

  private void jbInit() throws Exception {
    this.getContentPane().setLayout(borderLayout1);
    this.setJMenuBar(mnuBar);
    jMenu1.setText("File");
    miFileExit.setText("Exit");
    miFileExit.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        miFileExit_actionPerformed(e);
      }
    });
    lstNames.setModel(peerNames);
    lstNames.setSelectedIndex(0);
    jButton2.setText("Test");
    jButton2.setVisible(false);
    jButton2.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        jButton2_actionPerformed(e);
      }
    });
    miRun.setText("Run script");
    miRun.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        jMenuItem1_actionPerformed(e);
      }
    });
    this.getContentPane().add(jSplitPane1, java.awt.BorderLayout.CENTER);
    jPanel1.setLayout(verticalFlowLayout1);
    jTextField1.setText("sarge");
    jTextField1.setColumns(16);
    jButton1.setToolTipText("");
    jButton1.setText("CreateClean");
    jButton1.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        jButton1_actionPerformed(e);
      }
    });
    jSplitPane1.add(jPanel1, JSplitPane.LEFT);
    jPanel1.add(jTextField1);
    jPanel1.add(jButton1);
    jPanel1.add(lstNames);
    jPanel1.add(jButton2);
    jSplitPane1.add(jTabbedPane1, JSplitPane.RIGHT);
    jTabbedPane1.add(localCLI, "Controller CLI");
    mnuBar.add(jMenu1);
    jMenu1.add(miRun);
    jMenu1.add(miFileExit);
    jSplitPane1.setDividerLocation(150);
  }

  BorderLayout borderLayout1 = new BorderLayout();
  JSplitPane jSplitPane1 = new JSplitPane();
  JPanel jPanel1 = new JPanel();
  JTabbedPane jTabbedPane1 = new JTabbedPane();
  VerticalFlowLayout verticalFlowLayout1 = new VerticalFlowLayout(
          VerticalFlowLayout.TOP);
  JTextField jTextField1 = new JTextField();
  JButton jButton1 = new JButton();
  JMenuBar mnuBar = new JMenuBar();
  JMenu jMenu1 = new JMenu();
  JMenuItem miFileExit = new JMenuItem();
  JList lstNames = new JList();
  JButton jButton2 = new JButton();
  FrameworkCommandProcessor fcp = new FrameworkCommandProcessor(this);
  LocalCLIPanel localCLI = new LocalCLIPanel(fcp);
  JMenu miRun = new JMenu();

  public void jButton1_actionPerformed(ActionEvent e) {
    String name = lstNames.getSelectedValue().toString();
    lstNames.setSelectedIndex(lstNames.getSelectedIndex()+1);
    localCLI.exec("cleanimage "+name);
    localCLI.exec("create --debug " + name );
  }


  public void addTab(JComponent c, String name) {
    jTabbedPane1.add(c, name);
  }

  public void rexec( PrintStream pw, String device, String cmd ) {
    int i = jTabbedPane1.indexOfTab(device);
    if (i > -1) {
      ((SyxawPanel) jTabbedPane1.getComponentAt(i)).exec(cmd);
      pw.println("Executed '" + cmd + "' at " + device);
      return;
    }
    pw.println("Unknown remote device: "+device);
  }

  public void breakCommandWait(PrintStream pw, String device ) {
    int i = jTabbedPane1.indexOfTab(device);
    if( i > -1 )
      ((SyxawPanel) jTabbedPane1.getComponentAt(i)).breakCommandWait();
    else
      pw.println("Unknown remote device: "+device);
  }

  public void miFileExit_actionPerformed(ActionEvent e) {
    Log.log("Exit by GUI",Log.INFO);
    System.exit(0);
  }

  public void jButton2_actionPerformed(ActionEvent e) {
    /*RPC.Message m =
          RPC.getInstance().call("buzz",  new RPC.Message("Ping"));
    Log.log("RPC Call returned "+m.m,Log.INFO);*/
  }

  public void jMenuItem1_actionPerformed(ActionEvent e) {
    try {
      String file = null;
      if( e.getSource() == miRun )
        file = "default";
      else
        file = ((JMenuItem) e.getSource()).getText();
      BufferedReader br =
             new BufferedReader(
              new InputStreamReader(new FileInputStream(
              "scripts/"+file)));
      for( String cmd = null; (cmd = br.readLine()) != null;) {
        localCLI.exec(cmd);
      }
    } catch ( IOException ex ) {
      Log.log("Script read error: ",Log.WARNING,ex);
    }
  }

  public void addInstance(SyxawProcess p) {
    SyxawPanel sp = new SyxawPanel(p);
    addTab(sp,p.getName());
    instanceList.add(p.getName());
  }

  public void killInstance(String name) {
    throw new UnsupportedOperationException("GUI Can't yet kill instances.");
  }

  public Collection getInstanceNames() {
    return instanceList;
  }

  // Incoming msg spread to all devices...
  public class FindRelay extends Thread {

    public void run() {
      while (true) {
        RPC.Message m = RPC.getInstance().waitFor(new RPC.Acceptor() {
          public boolean test(fc.syxaw.proto.RPC.Message m) {
            return m.m instanceof Proto.FindReq || m.m instanceof Proto.FindRep;
          }
        });
        if( m.m instanceof Proto.FindReq ) {
          String forbidden = m.src;
          for (int i = 1; i < jTabbedPane1.getTabCount(); i++) {
            String dst = jTabbedPane1.getTitleAt(i);
//            Log.log("========Relaying everyone but "+forbidden,Log.INFO);
            if (!dst.equals(forbidden)) {
              Log.log("Relaying find rq "+m.id+" to "+dst,Log.INFO);
              m.src = GUID.getCurrentLocation();
              RPC.getInstance().send(dst, m);
            }
          }
        } else {
          Log.log("Relaying find rep "+m.id+" to "+((Proto.FindRep) m.m).fdest,Log.INFO);
          RPC.getInstance().send(((Proto.FindRep) m.m).fdest, m);
        }
      }
    }

  }
}

// arch-tag: ca557b64-2032-49f4-a1ca-76635e9ac685
