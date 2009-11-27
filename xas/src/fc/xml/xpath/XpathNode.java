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

package fc.xml.xpath;

public enum XpathNode {

    COMMENT ("comment"),
    TEXT ("text"),
    PROCESSING_INSTRUCTION ("processing-instruction"),
    NODE ("node");

    String name;

    XpathNode (String name) {
	this.name = name;
    }

    public String getName () {
	return name;
    }

    public String toString () {
	return name + "()";
    }

}

// arch-tag: 9c2c99b6-32b7-4b9c-89ea-5cff7eaab9a1
