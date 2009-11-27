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

public class XpathLocation implements Iterable<XpathStep> {

    private boolean absolute;
    private List<XpathStep> steps;

    public XpathLocation (boolean absolute) {
	this.absolute = absolute;
	this.steps = new ArrayList<XpathStep>();
    }

    public XpathLocation (boolean absolute, List<XpathStep> steps) {
	this.absolute = absolute;
	this.steps = new ArrayList<XpathStep>(steps);
    }

    public void addStep (XpathStep step) {
	steps.add(step);
    }

    public boolean isAbsolute () {
	return absolute;
    }

    public int stepNumber () {
	return steps.size();
    }

    public Iterator<XpathStep> iterator () {
	return steps.iterator();
    }

    public String toString () {
	StringBuilder result = new StringBuilder();
	if (absolute) {
	    result.append("/");
	}
	for (XpathStep step : steps) {
	    result.append(step);
	    result.append("/");
	}
	result.setLength(result.length() - 1);
	return result.toString();
    }

}

// arch-tag: c64ca74f-9361-4fce-97b1-38ec96617dc0
