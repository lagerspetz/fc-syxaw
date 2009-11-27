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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Read-only map with un-listable keys. Any attempts to modify the
 * map, or list the keys (or corresponding values) will result in an
 * {@link java.lang.UnsupportedOperationException}. On the bright side,
 * it allows you to easily implement an efficient lookup table by only 
 * overriding one method ({@link #lookup(String)}).
 */

public abstract class AbstractDictionary implements Map {

    public AbstractDictionary() {
	
    }
    
    protected abstract String lookup(String key);
    
    /** Not supported.
     */
    
    public void clear() {
	throw new UnsupportedOperationException();
    }

    /** Not supported.
     */

    public Object /*!5 String */ put(/*!5 String */ Object key, /*!5 String */ Object value) {
	throw new UnsupportedOperationException();
    }

    /** Not supported.
     */

    public void putAll(Map /*!5 <? extends String, ? extends String> */ m) {
	throw new UnsupportedOperationException();
    }

    /** Not supported.
     */

    public /*!5 String */ Object remove(Object key) {
	throw new UnsupportedOperationException();
    }

    /** Not supported.
     */

    public int size() {
	throw new UnsupportedOperationException();
    }

    /** Not supported.
     */

    public boolean isEmpty() {
	throw new UnsupportedOperationException();
    }

    public boolean containsKey(Object key) {
	return key instanceof String && lookup((String)key)!=null;
    }

    /** Not supported.
     */
    public boolean containsValue(Object value) {
	throw new UnsupportedOperationException();
    }

    public Object /*!5 String  */ get(Object key) {
	return key instanceof String ? lookup((String) key) : null;
    }

    /** Not supported.
     */
    
    public Set /*!5 <String> */ keySet() {
	throw new UnsupportedOperationException();
    }

    /** Not supported.
     */    
    public Collection /*!5 <String> */ values() {
	throw new UnsupportedOperationException();
    }

    /** Not supported.
     */
    public Set /*!5 <Entry<String, String>> */ entrySet() {
	throw new UnsupportedOperationException();
    }

}
// arch-tag: 5c283bab-3129-47df-b837-cd71081b211e
