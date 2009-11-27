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

package fc.xml.xas;

import java.io.IOException;
import java.util.LinkedList;

/**
 * An identity transform. This class is useful as a base class for further
 * transforms to be used with the Pipes and Filters processing model of XAS. For
 * a subclass it is sufficient to override the {@link #append(Item)} method to
 * put the transformed items into {@link #queue}, from which the
 * {@link #hasItems()} and {@link #next()} methods provide them to further
 * stages in processing.
 */
public class IdentityTransform implements ItemTransform {

    /**
         * A queue of available items.
         */
    protected LinkedList queue = new LinkedList();

    public boolean hasItems () {
	return queue.size() > 0;
    }

    public Item next () throws IOException {
	return (Item) queue.remove(0);
    }

    public void append (Item item) throws IOException {
	queue.add(item);
    }

}

// arch-tag: 46954f05-dc57-45ad-a80b-67063c6f1e11
