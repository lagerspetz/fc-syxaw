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

package fc.util.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** An in input stream that reads a limited number of bytes from the underlying 
 * stream. After the limit has been reached, the stream behaves as if the
 * stream has ended. In other respects, too, the stream appears to have
 * a length that is the minimum of the given limit and the length of
 * the underlying stream. 
 */
public class LengthLimitedInputStream extends FilterInputStream {

    int left;
    
    /** Construct a new limited stream.
     * 
     * @param in underlying stream
     * @param limit maximum number of bytes that are readable form the stream
     */
    
    public LengthLimitedInputStream(InputStream in, int limit) {
	super(in);
	left = limit;
    }

    /** @inheritDoc */
    public int available() throws IOException {
	return Math.min( left, super.available() );
    }

    /** @inheritDoc */
    public int read() throws IOException {
	if( left <= 0)
	    return -1;
	left--;
	return in.read();
    }

    /** @inheritDoc */
    public int read(byte[] buf, int off, int len) throws IOException {
	if( left <= 0) // BUGFIX-20070228-1: Contract requires -1 at EOF, not 0 bytes read
	    return -1;
	len = left < len ? left : len;
	int read = in.read(buf, off, len);
	left -= read >-1 ? read : left; 
	return read;
    }

    /** @inheritDoc */
    public int read(byte[] buf) throws IOException {
	return read(buf,0,buf.length);
    }

    /** @inheritDoc */
    public long skip(long len) throws IOException {
	len = len > left ? left : len;
	return in.skip(len);
    }
}
// arch-tag: beb570c7-0583-466e-aa16-c2a169dd0fd5
//