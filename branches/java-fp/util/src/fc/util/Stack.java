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

import java.util.LinkedList;

/** A non-vector based stack.
 */ 
/* @author ctl
 *
 * @param <T>
 */

public class Stack /*!5 <T> */ {
    
    LinkedList /*!5 <T>*/ stack = new LinkedList/*!5 <T> */();
    
    public final void push(Object /*!5T*/ o) {
	stack.addFirst(o); // BUGFIX-20061017-3: Push to wrong end of queue
    }

    public final Object /*!5T */ pop() {
	return stack.remove(0);
    }
    
    public final Object /*!5T */ peek() {
	return stack.get(0);
    }
    
    public final boolean isEmpty() {
	return stack.isEmpty();
    }
    
    public final void clear() {
	stack.clear();
    }
}

// arch-tag: fc15cebe-7c97-4fba-9366-76a3f1cf6548
