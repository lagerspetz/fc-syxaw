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

package fc.syxaw.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Random;

import fc.syxaw.util.Util;
import fc.util.StringUtil;
import fc.util.io.DelayedInputStream;
import fc.util.log.Log;

public class CliUtil {

  /** String tokenizer that splits by whitespace. Supports quotes (",') to from
   words. Supports \ escapes, also inside "" (e.g. \" = the quote sign;
   note that '\' = \, e.g. no \ escapes inside ' (just like bash))
   This implementation is memory-savy in that it does not use temp dynamic
   arrays, instead two passes are used to count tokens and then fill in the
   array. */

  public static final String[] tokenize(String line) {
    char quoteCh = '\u0000'; // Set in state 2
    String result[] = null;
    StringBuffer token = new StringBuffer();
    int tc = 0;
    for (int pass = 0; pass < 2; pass++) {
      if (pass == 1) {
        result = new String[tc];
        tc = 0;
      }
      int state = 0; // 0=ws, 1=text, 2=inquote, 101=escape-text, 102=escape-quote
      for (int ix = 0; ix < line.length(); ix++) {
        char ch = line.charAt(ix);
        boolean ws = Character.isWhitespace(ch);
        if (state == 0) {
          if (ws)
            continue;
          if (ch != '"' && ch != '\'' && ch != '\\') {
            if (pass > 0)
              token.append(ch);
            state = 1;
          } else if (ch == '\\') {
            state = 101;
          } else {
            quoteCh = ch;
            state = 2;
          }
        } else if (state == 1) {
          if (ws) {
            tc = addToken(pass, tc, result, token);
            state = 0;
          } else if (pass > 0)
            token.append(ch);
        } else if (state == 2) { // Comment
          if (ch == quoteCh) {
            tc = addToken(pass, tc, result, token);
            state = 0;
          } else if (ch == '\\' && quoteCh != '\'') {
            state = 102;
          } else if (pass > 0)
            token.append(ch);
        } else { // Escape states
          if (pass > 0)
            token.append(ch);
          state -= 100; // Only reachable from comment
        }
      }
      if (state > 1 && pass > 0)
        Log.log("Unclosed quote/escape", Log.WARNING);
      if (state > 0)
        tc = addToken(pass, tc, result, token);
    }
    //Log.log("Tokenized \n" + line + "\n as " + tokens, Log.INFO);
    return result;
  }

  private static final int addToken(int pass, int tc, String[] ta, StringBuffer token) {
    if (pass > 0) {
      ta[tc] = token.toString();
      token.setLength(0);
    }
    return tc + 1;
  }

  public static final String[] hasOpt(String[] args, String opt,
                                      Util.ObjectHolder valHolder) throws
          ParseException {
    String nopt=opt.length()==1 ? null : "--no"+opt;
    opt=(opt.length()==1 ? "-" : "--")+opt;
    int findix = -1;
    int argc=0;
    boolean isOpt;
    for( int i=0;i < args.length && findix == -1; i++) {
      if( (isOpt=args[i].equals(opt)) ||
            (args[i].equals(nopt) && !(isOpt=false)) ) {
        findix = i;
        if( valHolder.get()==null || valHolder.get() instanceof Boolean ) {
          valHolder.set(new Boolean(isOpt));
        } else if (valHolder.get() instanceof String) {
          if (args.length > findix +1) {
            valHolder.set(args[i+1]);
            i++;
            argc++;
          }
        } else if (valHolder.get() instanceof Integer) {
          if (args.length > findix +1) {
            try {
              valHolder.set( Integer.parseInt( args[i+1] ));
            } catch (NumberFormatException ex) {
              throw new ParseException("Invalid integer " + args[i+1], 0); 
            }
            i++;
            argc++;
          }
        } else {
          throw new ParseException("Unknown opt type " +
                                   valHolder.get().getClass().getName(),0);
        }
      }
    }    
    if( findix > -1 ) {
      // Remove found args
      String[] argsn = new String[args.length-(argc+1)];
      System.arraycopy(args,0,argsn,0,findix);
      System.arraycopy(args,findix+1+argc,argsn,findix,args.length-(findix+1+argc));
      return argsn;
    }
    return args;
  }

  public static class Recipe {

    private Random rnd;
    private long len;
    private long seed;
    private File f;
    
    public Recipe(String s) throws ParseException {
      if( s.startsWith("::") ) {
        f = new File(s.substring(2));
        if( !f.exists() || !f.canRead())
          throw new ParseException("Bad file recipe (cannot access file) "+
              f.getAbsolutePath(),0);
        return;
      }
      try {
        String[] parts = StringUtil.split(s,':');
        seed = Long.parseLong(parts[0],16);
        rnd = new Random(seed);
        len = parts.length > 1 ? Long.parseLong(parts[1],10) : 1024;
      } catch( Exception ex ) {
        throw new ParseException("bad recipe",0);
      }
    }

    public long getLength() {
      return f!= null ? f.length() : len;
    }

    public InputStream getStream() {
      if( f != null ) {
        try {
          return new FileInputStream(f);
        } catch (FileNotFoundException e) {
          Log.log("Cannot open file", Log.FATALERROR, e);
        }
      }
      return new DelayedInputStream() {
        protected void stream(OutputStream out) throws IOException {
          byte[] buf = new byte[1024];
          if( seed < 256 ) {
            for( int i=0;i<buf.length;i++)
              buf[i]=(byte) seed;
          }
          for(long left=len,chlen=0;left>0;left-=chlen) {
            chlen = Math.min(left,buf.length);
            if( seed > 255 )
              rnd.nextBytes(buf);
            out.write(buf,0,(int) chlen);
          }
        }
      };
    }
  }

  /*
  public static void main(String[] args) {
    String s =  "    one     two \"with space\"  \"with a \\\" inside\" "+
                "'\"in dbl quotes\"' \"'in single quotes'\"  \\' '\\'";
    String[] t = tokenize( s );
    Log.log("Tokenized "+s+" as ",Log.INFO,t);
  }*/

}

// arch-tag: 798c4770-f311-444c-86d9-2aef37e0e111
