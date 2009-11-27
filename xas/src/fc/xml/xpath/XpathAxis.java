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

public enum XpathAxis {

    CHILD ("child"),
    DESCENDANT ("descendant"),
    PARENT ("parent"),
    ANCESTOR ("ancestor"),
    FOLLOWING_SIBLING ("following-sibling"),
    PRECEDING_SIBLING ("preceding-sibling"),
    FOLLOWING ("following"),
    PRECEDING ("preceding"),
    ATTRIBUTE ("attribute"),
    NAMESPACE ("namespace"),
    SELF ("self"),
    DESCENDANT_OR_SELF ("descendant-or-self"),
    ANCESTOR_OR_SELF ("ancestor-or-self");

    String name;

    XpathAxis (String name) {
	this.name = name;
    }

    public String toString () {
	return name;
    }

}

// arch-tag: 0ae6f6e9-cb20-47ee-8606-1c9341c54811
