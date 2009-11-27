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

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LabeledOutputStream extends FilterOutputStream {

  private String[] filters;
  private int filtersMc=0;

  public LabeledOutputStream( OutputStream out ) {
    super(out);
  }

  public OutputStream getLabeledStream(String label) {
    return new LineLabelStream(label.getBytes());
  }

  public void setFilters(String[] filters) {
    this.filters=filters;
    filtersMc++;
  }

  private class LineLabelStream extends OutputStream {

    byte[] label;
    int flushes=0;
    int lastMc=0;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();

    public LineLabelStream(byte[] label ) {
      super();
      this.label = label;
    }

    public void write(byte[] b) throws IOException  {
      write(b,0,b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException  {
      for( int bix = off,bstart=bix;;bstart=bix+1 ) {
        bix=breakIx(b,bstart,off+len);
        if( bix >= 0) {
          bos.write(b, bstart, (bix - bstart+1));
          shipLine();
        } else {
          bos.write(b, bstart, (off + len) - bstart);
          break;
        }
      }
    }

    public void write(int b) throws IOException {
      bos.write(b);
      if( b== 0x0a )
        shipLine();
    }

    private int breakIx( byte[] b, int first, int max ) {
      for (int i = first; i < max; i++) {
        if (b[i] == 0x0a)
          return i;
      }
      return -1;
    }

    private void shipLine() throws IOException {
      if( lastMc < filtersMc && -filtersMc < lastMc ) {
        boolean matches = true; // Default
        String labelS = new String(label);
        for( int i=0;i<filters.length;i++) {
          String re = filters[i].substring(1);
          //System.err.println("matching "+labelS+" against "+re);
          if( labelS.matches( re ) ) {
            matches = filters[i].charAt(0) == '+';
            break;
          }
        }
        lastMc = filtersMc * ( matches ? 1 : -1);
        //System.err.println("Lastmc = "+lastMc);
      }
      if( lastMc < 0 )
        return;
      synchronized( LabeledOutputStream.this.out ) {
        LabeledOutputStream.this.write(label);
        bos.writeTo(LabeledOutputStream.this.out);
        bos.reset();
        if( flushes > 0 )
          LabeledOutputStream.this.flush();
        flushes =0;
      }
    }

    public void flush() throws IOException {
      flushes++;
    }

    public void close() throws IOException {
      if( bos.size() > 0 )
        shipLine();
      bos.write("{{Stream closed}}\n".getBytes());
      shipLine();
    }
  }
}

// arch-tag: 0a35241a-a2bc-48e4-8cc5-ece084eae5c2
