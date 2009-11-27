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

package fc.util;

import java.util.NoSuchElementException;

/*
 * Note: when implementing new methods, choose their names and semantics from
 * java.util.Queue or java.util.List if those contain appropriate ones.
 */
public class RingBuffer {

    private static final int DEFAULT_CAPACITY = 32;

    private int length;
    private int head; // Points at next to read 
    private int tail; // Points at insert position
    private Object[] buffer;

    public RingBuffer () {
	this(DEFAULT_CAPACITY);
    }

    public RingBuffer (int initialCapacity) {
	this.buffer = new Object[initialCapacity];
	this.head = this.tail = this.length = 0;
    }

    private void extend (int multiplier) {
	Object[] newBuffer = new Object[multiplier * buffer.length];
	if (head > tail) {
	    // In this case we need to stuff int newBuffer
	    // head...buffer[last]buffer[0]...buffer[tail-1]
	    // BUGFIX-20071015-1: This case was broken, now fixed
	    int chunk1Len = buffer.length - head;
	    System.arraycopy(buffer, head, newBuffer, 0, chunk1Len);
	    System.arraycopy(buffer, 0, newBuffer, chunk1Len, tail);
	    head = 0;
	    tail = chunk1Len + tail;
	} else {
	    System.arraycopy(buffer, head, newBuffer, head, tail - head);
	}
	buffer = newBuffer;
    }

    private void extend () {
	extend(2);
    }

    private boolean isFull () {
	return (tail + 1) % buffer.length == head;
    }

    private void increaseHead (int amount) {
	head += amount;
	head %= buffer.length;
	length -= amount;
    }

    private void increaseTail (int amount) {
	tail += amount;
	tail %= buffer.length;
	length += amount;
    }

    public boolean offer (Object o) {
	if (isFull()) {
	    extend();
	}
	buffer[tail] = o;
	increaseTail(1);
	return true;
    }

    public Object poll () {
	if (length == 0) {
	    return null;
	}
	Object result = buffer[head];
	buffer[head] = null;
	increaseHead(1);
	return result;
    }

    public Object remove () {
	if (length == 0) {
	    throw new NoSuchElementException("Buffer empty");
	} else {
	    return poll();
	}
    }

    public Object peek () {
	if (length == 0) {
	    return null;
	} else {
	    return buffer[head];
	}
    }

    public Object element () {
	if (length == 0) {
	    throw new NoSuchElementException("Buffer empty");
	} else {
	    return buffer[head];
	}
    }

    public int size () {
	return length;
    }

    public boolean isEmpty () {
	return length == 0;
    }

    public Object get (int index) {
	if (index < 0 || index >= length) {
	    throw new IndexOutOfBoundsException("Index " + index
		    + " out of range [0," + (tail - head) + "]");
	}
	return buffer[(head + index) % buffer.length];
    }

    public Object set (int index, Object element) {
	if (index < 0) {
	    throw new IndexOutOfBoundsException("Index " + index
		    + " is negative");
	}
	if (index >= buffer.length) {
	    int multiplier = 2;
	    while (index >= multiplier * buffer.length) {
		multiplier *= 2;
	    }
	    extend(multiplier);
	}
	int location = (head + index) % buffer.length;
	Object result = buffer[location];
	buffer[location] = element;
	return result;
    }

    public void removeFirst (int amount) {
	if (amount < 0) {
	    throw new IllegalArgumentException("Amount " + amount
		    + " is negative");
	}
	if (amount > length) {
	    throw new NoSuchElementException("Cannot remove " + amount
		    + " elements, only " + length + " exist");
	}
	while (amount > 0) {
	    buffer[head] = null;
	    increaseHead(1);
	    amount -= 1;
	}
    }

}

// arch-tag: 81081ec5-6d3d-4719-9da4-c2d75a08fff7
