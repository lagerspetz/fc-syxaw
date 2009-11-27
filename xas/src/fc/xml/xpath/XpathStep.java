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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fc.xml.xas.Qname;

public class XpathStep implements Iterable<XpathExpression> {

    private Kind kind;
    private XpathAxis axis;
    private XpathNode node;
    private Qname name;
    private List<XpathExpression> predicates;

    private XpathStep (Kind kind, XpathAxis axis, XpathNode node, Qname name,
		       List<XpathExpression> predicates) {
	this.kind = kind;
	this.axis = axis;
	this.node = node;
	this.name = name;
	this.predicates = predicates;
    }

    public static XpathStep createNodeTest (XpathAxis axis, XpathNode node) {
	return new XpathStep(Kind.NODE, axis, node, null,
			     new ArrayList<XpathExpression>());
    }

    public static XpathStep createNodeTest (XpathAxis axis, XpathNode node,
					    List<XpathExpression> predicates) {
	return new XpathStep(Kind.NODE, axis, node, null,
			     new ArrayList<XpathExpression>(predicates));
    }

    public static XpathStep createNameTest (XpathAxis axis, Qname name) {
	return new XpathStep(Kind.NAME, axis, null, name,
			     new ArrayList<XpathExpression>());
    }

    public static XpathStep createNameTest (XpathAxis axis, Qname name,
					    List<XpathExpression> predicates) {
	return new XpathStep(Kind.NAME, axis, null, name,
			     new ArrayList<XpathExpression>(predicates));
    }

    public void addPredicate (XpathExpression predicate) {
	predicates.add(predicate);
    }

    public Kind getKind () {
	return kind;
    }

    public XpathAxis getAxis () {
	return axis;
    }

    public XpathNode getNode () {
	return node;
    }

    public Qname getName () {
	return name;
    }

    public Iterator<XpathExpression> iterator () {
	return predicates.iterator();
    }

    public String toString () {
	StringBuilder result = new StringBuilder();
	result.append(axis);
	result.append("::");
	if (node != null) {
	    result.append(node);
	} else {
	    result.append(name.toString());
	}
	for (XpathExpression predicate : predicates) {
	    result.append("[");
	    result.append(predicate);
	    result.append("]");
	}
	return result.toString();
    }

    public enum Kind {
	NODE,
	NAME;
    }

}

// arch-tag: 9e8ceb55-96fb-4818-862d-b21e5cb2b07c
