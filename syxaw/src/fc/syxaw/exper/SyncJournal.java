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

package fc.syxaw.exper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import fc.syxaw.storage.hfsbase.ServerConfig;
import fc.syxaw.tool.CliUtil;
import fc.syxaw.util.Util.ObjectHolder;
import fc.util.log.Log;

public class SyncJournal {
  
  private static int lapCount =  0;

  public static void touchfiles(PrintStream pw, String[] args) throws ParseException, 
    IOException {
    ObjectHolder<Integer> seed = new ObjectHolder<Integer>(new Random()
        .nextInt());
    ObjectHolder<Boolean> autolap = new ObjectHolder<Boolean>(false);
    ObjectHolder<Integer> ppm = new ObjectHolder<Integer>(0);
    ObjectHolder<String> dir = new ObjectHolder<String>(ServerConfig.ROOT_FOLDER);
    args = CliUtil.hasOpt(args, "seed", seed);
    args = CliUtil.hasOpt(args, "autolap", autolap);
    args = CliUtil.hasOpt(args, "ppm", ppm);
    args = CliUtil.hasOpt(args, "dir", dir);
    Random rnd = new Random(seed.get()+(autolap.get() ? lapCount : 0));
    lapCount++;
    pw.print("Lap "+lapCount+": modding with ppm " + ppm.get() + ":");
    String[] fnames = (new File(dir.get())).list();
    Arrays.sort(fnames); // Make this deterministic by having a strict ordering
    for (String fname: fnames) {
      File f = new File(dir.get(),fname);
      if (f.isHidden() || !f.isFile() || !f.canWrite() || 
           f.getName().startsWith(".syxaw")) {
        continue;
      }
      if (rnd.nextInt(1000000) < ppm.get()) {
        //f.setLastModified(System.currentTimeMillis());
        FileOutputStream fout = new FileOutputStream(f.getPath(),true);
        //fout.write(("mod: "+new Date()+"\n").getBytes());
        fout.write(0); // Add 0 byte to file..should not break zip
        fout.close();
        Log.info("Modded " + f);
        pw.print(" " + f);
        
      }
    }
    pw.println();
  }
  
}
// arch-tag: 04da4f4a-c26b-435e-900f-757524c65610
//
