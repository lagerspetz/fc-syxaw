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
import java.util.List;

public class XpathPath {

    private XpathLocation location;
    private XpathPrimary primary;
    private List<XpathExpression> predicates;

    private XpathPath (XpathLocation location, XpathPrimary primary,
		       List<XpathExpression> predicates) {
	this.location = location;
	this.primary = primary;
	this.predicates = predicates;
    }

    public static XpathPath createLocation (XpathLocation location) {
	return new XpathPath(location, null, null);
    }

    public static XpathPath createFilter (XpathPrimary primary) {
	return new XpathPath(null, primary, new ArrayList<XpathExpression>());
    }

    public static XpathPath createFilter (XpathPrimary primary,
					  List<XpathExpression> predicates) {
	return new XpathPath(null, primary,
			     new ArrayList<XpathExpression>(predicates));
    }

    public static XpathPath createFilter (XpathPrimary primary,
					  XpathLocation location) {
	return new XpathPath(location, primary,
			     new ArrayList<XpathExpression>());
    }

    public static XpathPath createFilter (XpathPrimary primary,
					  List<XpathExpression> predicates,
					  XpathLocation location) {
	return new XpathPath(location, primary,
			     new ArrayList<XpathExpression>(predicates));
    }

    public void addPredicate (XpathExpression predicate) {
	predicates.add(predicate);
    }

    public void addLocation (XpathLocation location) {
	if (this.location == null) {
	    this.location = location;
	}
    }

    public boolean hasLocation () {
	return location != null;
    }

    public XpathLocation getLocation () {
	return location;
    }

    public boolean isPrimary () {
	return primary != null;
    }

    public XpathPrimary getPrimary () {
	return primary;
    }

    public String toString () {
	if (primary == null) {
	    return location.toString();
	} else {
	    StringBuilder result = new StringBuilder();
	    result.append(primary);
	    for (XpathExpression predicate : predicates) {
		result.append("[");
		result.append(predicate);
		result.append("]");
	    }
	    if (location != null) {
		result.append(location);
	    }
	    return result.toString();
	}
    }

}

// arch-tag: 20c316c6-c3d9-47e3-a6bb-467a6f5f32d2
