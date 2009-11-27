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

package fc.xml.xmlr;

import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fc.util.IOExceptionTrap;

// Automatically dereferencing reftree
// NOTE: asserts will enable fancy checking for use of stale objects; this
// consumes some memory though, so beware.

/** A reftree that transparently de-references reference nodes. 
 */

public class DerefRefTree implements IdAddressableRefTree {

  protected int modCount=0;
  
  // the _modCount* things are part of an assert-based system for keeping track 
  // of use of proxied nodes after an edit. We use a map of modCounts rather
  // than a modCount member for the proxy nodes in order to avoid storage
  // overhead when asserts are disabled. We use _MODCOUNT_KEYMASK to limit the
  // no of nodes monitored at a time, since it seems to be impossible to 
  // clear the refs from the hashmap efficiently (using a finalizer, even if
  // only containing a single assert) is dead slow. 
  
  protected Map /*!5 <Integer,Integer> */ _modCounts;
  protected int _MODCOUNT_KEYMASK = 4096-1; // keep max 4k objects
  
  protected IdAddressableRefTree refTree;
  protected IdAddressableRefTree backingTree;
  protected IOExceptionTrap trap=null;
  protected KeyMap km=null;
  
  /** Create instance. The tree will transparently resolve any references
   * in <i>refTree</i> to the corresponding nodes in <i>backingTree</i>.
   * <i>Transparent resolving</i> means that whenever a node that is present
   * through a reference in <i>refTree</i> is accessed, a non-reference node
   * equal to the referenced node in <i>backingTree</i> is returned.
   * <p> 
   * A key map can be provided to map references between <i>refTree</i> 
   * and <i>backingTree</i>.
   * 
   * @param refTree Tree to dereference
   * @param backingTree Target tree of references in <i>refTree</i>
   * @param km Key map
   */
  public DerefRefTree(IdAddressableRefTree refTree,
      IdAddressableRefTree backingTree, KeyMap km) {
    //A! assert (_modCounts= new HashMap<Integer,Integer>())!=null;
    this.refTree = refTree;
    this.backingTree = backingTree;
    this.km = km;
  }

  /** @inheritDoc */
  public RefTreeNode getRoot() {
    RefTreeNode rr = refTree.getRoot(); 
    if( rr == null )
      return new BackProxyNode(backingTree.getRoot());
    if( rr.isTreeRef() ) {
      return new BackProxyNode( getExistingBackNode(rr) );
    }
    return new FrontProxyNode(rr);
  }

  /** @inheritDoc */
  public RefTreeNode getNode(Key id) {
    RefTreeNode rr = refTree.getNode(id);
    if( rr == null ) {
      Key bk = km.getBackKey(id);
      rr = isDeleted(bk) ? null : backingTree.getNode(bk);
      return rr == null ? null : new BackProxyNode(rr);
    } else if ( rr.isTreeRef() ) {
      return new BackProxyNode(getExistingBackNode(rr));      
    } else
      return new FrontProxyNode(rr);
  }

  /** @inheritDoc */  
  public boolean contains(Key id) {
    if( refTree.contains(id) )
      return true;
    Key bk = km.getBackKey(id);
    return backingTree.contains(bk) && !isDeleted(bk);
  }

  /** @inheritDoc */
  public Key getParent(Key nid) throws NodeNotFoundException {
    if( refTree.contains(nid))
      return refTree.getParent(nid);
    Key bk = km.getBackKey(nid);
    Key bpk = isDeleted(bk) ? null : backingTree.getParent(bk);
    return km.getFrontKey(bpk);
  }

  /** @inheritDoc */
  public Iterator /*!5 <Key> */ childIterator(Key nid) throws NodeNotFoundException {
    RefTreeNode n = refTree.getNode(nid);
    if( n != null && !n.isTreeRef() )
      return refTree.childIterator(nid);
    // Use back key + key translation
    Key bk = km.getBackKey(nid);
    if( isDeleted(bk) || !backingTree.contains(bk) )
      throw new NodeNotFoundException(nid);
    final Iterator bci = backingTree.childIterator(bk);
    return new Iterator() {

      public boolean hasNext() {
        return bci.hasNext();
      }

      public Object next() {
        return km.getFrontKey((Key) bci.next()) ;
      }

      public void remove() {
        bci.remove();
      }
      
    }; 
  }

  protected RefTreeNode getExistingBackNode(RefTreeNode m) {
    Reference r = ((RefTreeNode) m).getReference();
    if( r == null )
      return null;
    RefTreeNode bn = ((RefTreeNode) backingTree.getNode(r.getTarget()));
    if( bn == null )
      trap(new NodeNotFoundException(r.getTarget()));
    return bn;
  }
  
  protected boolean isDeleted(Key bk) {
    return false;
  }
  
  protected void trap(Exception e) {
    if( !(e instanceof IOException) ) {
      IOException ee = new IOException("Trapped exception "+e);
      e = ee;
    }
    if(trap != null) 
      trap.trap((IOException) e );
    else 
      throw new IOExceptionTrap.RuntimeIOException((IOException) e);
    
  }

  // FrontNodes for all ordinary and noderef nodes in reftree
  protected class FrontProxyNode implements RefTreeNode {

    RefTreeNode n;
    
    public FrontProxyNode(RefTreeNode n) {
      //A! assert _modCounts.put(System.identityHashCode(this)&_MODCOUNT_KEYMASK,
      //A!    modCount)==null || true;
      //A! assert !n.isTreeRef();
      //A! assert n != null;
      this.n = n;
    }

    public Reference getReference() {
      //A! assert checkModCount() == true;
      if( n.isNodeRef() ) {
        RefTreeNode bn = getExistingBackNode(n);
        return ((RefTreeNode) bn).getReference();
      }
      return ((RefTreeNode) n).getReference();
    }

    public Key getId() {
      //A! assert checkModCount() == true;
      return n.getId();
    }

    public RefTreeNode getParent() {
      //A! assert checkModCount() == true;      
      RefTreeNode p = n.getParent();
      return p != null ? new FrontProxyNode( p ) : null; // Parent is always a frontnode 
    }

    public Object getContent() {
      //A! assert checkModCount() == true;
      if( n.isNodeRef() ) {
        RefTreeNode bn = getExistingBackNode(n);
        Object c = ((RefTreeNode) bn).getContent();
        return c;
      }      
      return n.getContent();
    }

    public Iterator getChildIterator() {
      //A! assert checkModCount() == true;
      final Iterator ri = n.getChildIterator();
      return new Iterator() {
        public boolean hasNext() {
          return ri.hasNext();
        }

        public Object next() {
          RefTreeNode c = (RefTreeNode) ri.next();
          if( c.isTreeRef() ) {
            RefTreeNode bn = getExistingBackNode(c);
            return new BackProxyNode(bn);
          } else
            return new FrontProxyNode(c);
        }

        public void remove() {
          ri.remove();
        }
      };
    }

    public boolean isReference() {
      //A! assert checkModCount() == true;
      return isNodeRef() || isTreeRef();
    }

    public boolean isTreeRef() {
      //A! assert checkModCount() == true;
      return false; // Front nodes are never treerefs (by definition)!
    }

    public boolean isNodeRef() {
      //A! assert checkModCount() == true;
      return getReference() instanceof NodeReference;
    }   

    public boolean equals(Object o) {
      //A! assert checkModCount() == true;
      return n.isNodeRef() ? getExistingBackNode(n).equals(o) : n.equals(o);
    }

    public int hashCode() {
      //A! assert checkModCount() == true;
      return n.isNodeRef() ? getExistingBackNode(n).hashCode() : n.hashCode();
    }
    
    public String toString() {
      return "[FP] "+n.toString();
    }

    protected boolean checkModCount() {
      Integer count = (Integer) _modCounts.get(
          new Integer( System.identityHashCode(this)&_MODCOUNT_KEYMASK  ) );
      ////A! assert count != null : "Missing modcount";
      // We can't do the above check because quick object recycling sometimes
      // causes collisions
      // In the case of collision, a too high modcount will be returned, and
      // a potential concurrent update is missed... well, these asserts are
      // probabilistic guards anyway
      if( count != null && count.intValue() != modCount )
        throw new ConcurrentModificationException(
            "This node object has become stale due to an edit.");
      return true;
    }
    
    // DO NOT ENABLE: Risk of insane performance penalty even if asserts 
    // disabled (Apparently adding a finalizer to a short-live object 
    // is a real performance-killer)
    /*protected void finalize() {
      //A! assert modCounts.remove(System.identityHashCode(this))!=null || true;
    }*/
  }
  
  protected class BackProxyNode extends FrontProxyNode {

    public BackProxyNode(RefTreeNode n) {
      super(n);
    }

    public Reference getReference() {
      return ((RefTreeNode) n).getReference();
    }

    public Key getId() {
      //A! assert checkModCount() == true;
      return km.getFrontKey(n.getId());
    }

    public RefTreeNode getParent() {
      // BUGFIX 150906-1: Broken getParent()
      // This one is tricky; we have two cases
      // 1) this node is available as a ref in frontree
      //    --> look up parent in FRONT keyspace, and reurn frontproxy to that
      // 2) the parent of this is available as a ref in frontree -->
      //    just BackProxy n.getParent() 
      //A! assert checkModCount() == true;
      Key fk = km.getFrontKey(n.getId());
      if( refTree.contains(fk) ) {
        //A! assert refTree.getNode(fk).isTreeRef();
        RefTreeNode p = refTree.getNode(fk).getParent();
        return p == null ? null : new FrontProxyNode(p);
      } else {
        RefTreeNode p = n.getParent();
        return p == null ? null : new BackProxyNode(p);
      }
    }

    public Object getContent() {
      //A! assert checkModCount() == true;
      return n.getContent();
    }

    public Iterator getChildIterator() {
      // NOTE: No tricks, since going downwards cannot jump trees
      //A! assert checkModCount() == true;
      final Iterator ri = n.getChildIterator();
      return new Iterator() {
        public boolean hasNext() {
          return ri.hasNext();
        }

        public Object next() {
          return new BackProxyNode( (RefTreeNode) ri.next());
        }

        public void remove() {
          ri.remove();
        }
      };
    }

    public boolean isReference() {
      //A! assert checkModCount() == true;
      return n.isReference();
    }

    public boolean isTreeRef() {
      //A! assert checkModCount() == true;
      return n.isTreeRef();
    }

    public boolean isNodeRef() {
      //A! assert checkModCount() == true;
      return n.isNodeRef();
    }

    public String toString() {
      return "[BP] "+n.toString();
    }
    
  }

  /** Get trap for I/O errors.
   * 
   * @return trap, or <code>null</code> if none
   */
  public IOExceptionTrap getTrap() {
    return trap;
  }

  /** Set trap for I/O errors.
   */

  public void setTrap(IOExceptionTrap trap) {
    this.trap = trap;
  }
  
}
// arch-tag: 3558aac9-e4fa-4b0a-974a-cc5fcad5c399
