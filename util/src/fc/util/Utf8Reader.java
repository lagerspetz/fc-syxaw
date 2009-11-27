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

// $Id: Utf8Reader.java,v 1.3 2006/01/19 16:08:56 ctl Exp $

package fc.util;

import java.io.IOException;
import java.io.Reader;

import fc.util.log.Log;

/** Reader that keeps track of byte and char offsets when decoding utf-8. */

public class Utf8Reader extends Reader {

    /** Size of position mapping window as 2**n */
    public static final int MAP_BITS = 10;
    private static final int MAP_SIZE = 1<<MAP_BITS;
    private static final int MAP_MASK = MAP_SIZE-1;
    private static final int MAX_CHUNK = Integer.MAX_VALUE;

    private int charPos=0, streamPos=0; // Pos at beginning of next
    private int[] streamMap=new int[MAP_SIZE];
    private SeekableInputStream in;
    private Character supplementaryTailChar = null;

    private byte[] buffer = new byte[MAP_SIZE];
    private int bufLen=buffer.length, left=0,offset=bufLen;
    private long currentStreamPos=0; // True stream position, even after seek 
    
    
    public static int count = 0;

    public Utf8Reader( SeekableInputStream in ) {
        this.in = in;
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
        int olen = len;
        len=len>MAX_CHUNK ? MAX_CHUNK : len;
        for(;len>0;len--) {
            { // --BEGIN-INLINED-nextChar()
                if (supplementaryTailChar != null) {
                    cbuf[off++] = supplementaryTailChar.charValue();
		    supplementaryTailChar = null;
		    continue;
		}
		streamMap[charPos & MAP_MASK] = streamPos;
		// int b = in.read();
		// --INLINED-BUFFERED-READ
		if (left <= 0) {
		    left = in.read(buffer, 0, bufLen);
		    if (left < 0)
			return olen-len;
	            currentStreamPos+=left;
		    offset = 0;
		    // Log.info("Chunked in ",Debug.toPrintable(buffer, 0,
                        // left));
		}
		left--;
		int b = buffer[offset++] & 0xff;
		// --END-INLINED-BUFFERED-READ
		int mask = 0x80, extra = -1;
		streamPos++;
		while ((b & mask) != 0x0) {
		    mask >>= 1;
		    extra++;
		    if (extra > 5)
			throw new IOException();
		}
		b &= (mask - 1);
		for (int b2 = -1; extra > 0; extra--) {
		    // --INLINED-BUFFERED-READ
		    if (left <= 0) {
			left = in.read(buffer, 0, bufLen);
			if (left < 0)
			    return olen-len;
			currentStreamPos+=left;
			offset = 0;
			// Log.info("Chunked-2 in ",Debug.toPrintable(buffer, 0,
                        // left));
		    }
		    left--;
		    b2 = buffer[offset++] & 0xff;
		    // --END-INLINED-BUFFERED-READ
		    if (b2 == -1)
			throw new IOException();
		    streamPos++;
		    b = (b << 6) + (b2 & 0x3f);
		}
		if (b > 0xffff) {
		    // Handle codepoint beyond 0xffff the Java way = make 2
                        // chars
		    int bp = b -= 0x10000;
		    b = (0xd800 + (bp >> 10));
		    supplementaryTailChar = new Character(
			    (char) (0xdc00 + (bp & 0x3FF)));
		    charPos++;
		    streamMap[charPos & MAP_MASK] = -1; // Map pos of
                                                        // supplementary char to
                                                        // -1
		}
		charPos++;
		// BUGFIX: streampos was not set for charpos, only for offsets
		// before it
		streamMap[charPos & MAP_MASK] = streamPos;
		// System.out.println("nextchar="+(char) b);
		cbuf[off++] = (char) b;
	    } // --END-INLINED-nextChar()
            /*ch = nextChar();
            if( ch < 0 )
                break;
            cbuf[off++] = (char) ch;*/
        }
        return olen-len;
    }

    public void close() throws IOException {
        in.close();
    }

    public int read() throws IOException {
        return nextChar();
    }

    public int getStreamPos(int readerPos) throws IOException {
        int offset = readerPos-charPos;
        if( readerPos < 0 || offset > 0 || -offset > MAP_MASK )
            throw new IOException("Cannot map window position "+(-offset));
// assert (readerPos) <= streamMap[readerPos&MAP_MASK]
// : String.valueOf(readerPos) + ", " + charPos + "\n"
// + java.util.Arrays.toString(streamMap);
        return streamMap[(MAP_SIZE+offset+charPos)&MAP_MASK];
    }

    /*
         * public void seek( long pos ) throws IOException { in.seek(pos); }
         */
    
    // Note: this simple utf-8 decoder may have security risks. At the very
        // least,
    // it won't recognize overly long encoding
    private final int nextChar() throws IOException {
        if( supplementaryTailChar != null ) {
         int b = supplementaryTailChar.charValue();
         supplementaryTailChar = null;
         return b;
        }
        streamMap[charPos&MAP_MASK]=streamPos;
        //int b = in.read(); 
        //--INLINED-BUFFERED-READ
        if( left <= 0) {
            left=in.read(buffer, 0, bufLen);
            if( left < 0 )
        	return left;
            currentStreamPos+=left;
            offset=0;
            //Log.info("Chunked in ",Debug.toPrintable(buffer, 0, left));
        }
        left --;
        int b=buffer[offset++]&0xff;         
        // --END-INLINED-BUFFERED-READ
        int mask=0x80, extra=-1;
        streamPos++;
        while( (b & mask) != 0x0 ) {
            mask >>= 1;
            extra++;
            if( extra > 5 )
                throw new IOException();
        }
        b&=(mask-1);
        for(int b2=-1;extra>0;extra--) {
            //--INLINED-BUFFERED-READ
            if( left <= 0) {
                left=in.read(buffer, 0, bufLen);
                if( left < 0 )
            		return left;
                currentStreamPos+=left;
                offset=0;
                //Log.info("Chunked-2 in ",Debug.toPrintable(buffer, 0, left));
            }
            left --;
            b2=buffer[offset++]&0xff;
            // --END-INLINED-BUFFERED-READ
            if( b2 == -1 )
                throw new IOException();
            streamPos++;
            b=(b<<6)+(b2&0x3f);
        }
       if( b > 0xffff ) {
           // Handle codepoint beyond 0xffff the Java way = make 2 chars
           int bp = b -= 0x10000;
           b = (0xd800 + (bp >> 10));
           supplementaryTailChar = new Character((char) (0xdc00 + (bp & 0x3FF)));
           charPos++;
           count++;
           streamMap[charPos&MAP_MASK]=-1; // Map pos of supplementary char to -1
       }
        charPos++;
	count++;
	//BUGFIX: streampos was not set for charpos, only for offsets
	//before it
        streamMap[charPos&MAP_MASK]=streamPos;
        //System.out.println("nextchar="+(char) b);
        return b;
    }

    // Seek to streamPos in infile. This becomes streampos (and charpos) 0
    public void newCharZero(long newStreamPos) throws IOException {
      if( newStreamPos < currentStreamPos && newStreamPos >= (currentStreamPos-left)  ) {
	    int cut = left - (int) (currentStreamPos -newStreamPos);
	    offset+=cut;
	    left-=cut;
	    //Log.debug("Optimized reader seek!");
      } else { 
	  in.seek(newStreamPos);
	  currentStreamPos=newStreamPos;
	  left=0;
	  offset=bufLen;
      }
      this.streamPos=0;
      this.charPos=0;
      supplementaryTailChar=null;
    }
    
    /*
    public static void main(String[] _) throws IOException {
	// Should do something like (§=extended plane char 0x10026)
	// Char array is [a, ?, ?, b]
	// Decoded string of len 4 prints as a§b
	// Stream map[charPos]=bytespos is [0, 1, -1, 5]

	class BinSeek extends java.io.ByteArrayInputStream implements SeekableInputStream {

	    public BinSeek(byte[] buf) {
		super(buf);
	    }

	    public void seek(long pos) throws IOException {
	    }
	    
	}
	byte[] bytes = new byte[] { 'a', (byte) 0xf0, (byte) 0x90, (byte) 0x80,
		(byte) 0xa6, 'b' };
	//byte[] bytes = new byte[] { 'a', '&', 'b' };

	SeekableInputStream bin = new BinSeek (bytes);
	Utf8Reader r = new Utf8Reader(bin);
	char[] chars = new char[4];
	r.read(chars);
	String s = new String(chars);
	System.out.println("Char array is "+Debug.toString(chars));
	System.out.println("Decoded string of len "+s.length()+" prints as "+s);
	System.out.println("Stream map[charPos]=bytespos is "+
		Debug.toString(Debug.box(r.streamMap),0,4,2));
	
    }*/
    
}
// arch-tag: e4a052b6-4739-4da3-9860-02302093fa09

