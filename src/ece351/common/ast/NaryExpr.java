/* *********************************************************************
 * ECE351 
 * Department of Electrical and Computer Engineering 
 * University of Waterloo 
 * Term: Fall 2021 (1219)
 *
 * The base version of this file is the intellectual property of the
 * University of Waterloo. Redistribution is prohibited.
 *
 * By pushing changes to this file I affirm that I am the author of
 * all changes. I affirm that I have complied with the course
 * collaboration policy and have not plagiarized my work. 
 *
 * I understand that redistributing this file might expose me to
 * disciplinary action under UW Policy 71. I understand that Policy 71
 * allows for retroactive modification of my final grade in a course.
 * For example, if I post my solutions to these labs on GitHub after I
 * finish ECE351, and a future student plagiarizes them, then I too
 * could be found guilty of plagiarism. Consequently, my final grade
 * in ECE351 could be retroactively lowered. This might require that I
 * repeat ECE351, which in turn might delay my graduation.
 *
 * https://uwaterloo.ca/secretariat-general-counsel/policies-procedures-guidelines/policy-71
 * 
 * ********************************************************************/

package ece351.common.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.parboiled.common.ImmutableList;

import ece351.util.Examinable;
import ece351.util.Examiner;

/**
 * An expression with multiple children. Must be commutative.
 */
public abstract class NaryExpr extends Expr {

	public final ImmutableList<Expr> children;

	public NaryExpr(final Expr... exprs) {
		Arrays.sort(exprs);
		ImmutableList<Expr> c = ImmutableList.of();
		for (final Expr e : exprs) {
			c = c.append(e);
		}
    	this.children = c;
	}
	
	public NaryExpr(final List<Expr> children) {
		final ArrayList<Expr> a = new ArrayList<Expr>(children);
		Collections.sort(a);
		this.children = ImmutableList.copyOf(a);
	}

	/**
	 * Each subclass must implement this factory method to return
	 * a new object of its own type. 
	 */
	public abstract NaryExpr newNaryExpr(final List<Expr> children);

	/**
	 * Construct a new NaryExpr (of the appropriate subtype) with 
	 * one extra child.
	 * @param e the child to append
	 * @return a new NaryExpr
	 */
	public NaryExpr append(final Expr e) {
		return newNaryExpr(children.append(e));
	}

	/**
	 * Construct a new NaryExpr (of the appropriate subtype) with 
	 * the extra children.
	 * @param list the children to append
	 * @return a new NaryExpr
	 */
	public NaryExpr appendAll(final List<Expr> list) {
		final List<Expr> a = new ArrayList<Expr>(children.size() + list.size());
		a.addAll(children);
		a.addAll(list);
		return newNaryExpr(a);
	}

	/**
	 * Check the representation invariants.
	 */
	public boolean repOk() {
		// programming sanity
		assert this.children != null;
		// should not have a single child: indicates a bug in simplification
		assert this.children.size() > 1 : "should have more than one child, probably a bug in simplification";
		// check that children is sorted
		int i = 0;
		for (int j = 1; j < this.children.size(); i++, j++) {
			final Expr x = this.children.get(i);
			assert x != null : "null children not allowed in NaryExpr";
			final Expr y = this.children.get(j);
			assert y != null : "null children not allowed in NaryExpr";
			assert x.compareTo(y) <= 0 : "NaryExpr.children must be sorted";
		}
        // Note: children might contain duplicates --- not checking for that
        // ... maybe should check for duplicate children ...
		// no problems found
		return true;
	}

	/**
	 * The name of the operator represented by the subclass.
	 * To be implemented by each subclass.
	 */
	public abstract String operator();
	
	/**
	 * The complementary operation: NaryAnd returns NaryOr, and vice versa.
	 */
	abstract protected Class<? extends NaryExpr> getThatClass();
	

	/**
     * e op x = e for absorbing element e and operator op.
     * @return
     */
	public abstract ConstantExpr getAbsorbingElement();

    /**
     * e op x = x for identity element e and operator op.
     * @return
     */
	public abstract ConstantExpr getIdentityElement();


	@Override 
    public final String toString() {
    	final StringBuilder b = new StringBuilder();
    	b.append("(");
    	int count = 0;
    	for (final Expr c : children) {
    		b.append(c);
    		if (++count  < children.size()) {
    			b.append(" ");
    			b.append(operator());
    			b.append(" ");
    		}
    		
    	}
    	b.append(")");
    	return b.toString();
    }


	@Override
	public final int hashCode() {
		return 17 + children.hashCode();
	}

	@Override
	public final boolean equals(final Object obj) {
		if (!(obj instanceof Examinable)) return false;
		return examine(Examiner.Equals, (Examinable)obj);
	}
	
	@Override
	public final boolean isomorphic(final Examinable obj) {
		return examine(Examiner.Isomorphic, obj);
	}
	
	private boolean examine(final Examiner e, final Examinable obj) {
		// basics
		if (obj == null) return false;
		if (!this.getClass().equals(obj.getClass())) return false;
		final NaryExpr that = (NaryExpr) obj;
		
		// if the number of children are different, consider them not equivalent
		// since the n-ary expressions have the same number of children and they are sorted, just iterate and check
		// supposed to be sorted, but might not be (because repOk might not pass)
		// if they are not the same elements in the same order return false
		// no significant differences found, return true
		if (this.children.size() != that.children.size()) {
			return false;
		}
		for (int i = 0; i < this.children.size(); i++) {
			if (!e.examine(this.children.get(i), that.children.get(i))) {
				return false;
			}
		}
		return true;
		// TODO: longer code snippet
	}

	
	@Override
	protected final Expr simplifyOnce() {
		assert repOk();
		final Expr result = 
				simplifyChildren().
				mergeGrandchildren().
				foldIdentityElements().
				foldAbsorbingElements().
				foldComplements().
				removeDuplicates().
				simpleAbsorption().
				subsetAbsorption().
				singletonify();
		assert result.repOk();
		return result;
	}
	
	/**
	 * Call simplify() on each of the children.
	 */
	private NaryExpr simplifyChildren() {
		// note: we do not assert repOk() here because the rep might not be ok
		// the result might contain duplicate children, and the children
		// might be out of order
		
		if (this.children.size() == 0) {
			return this;
		}
		
		// int index = 0;
		// while (index < this.children.size()) {
		// 	// Expr child = this.children.get(index);
		//  	// child = child.simplifyOnce();
		//  	// this.children.set(index, child);
		// 	NaryExpr a = this.filter(OrExpr.class, true);
		// 	index++;
		// }

		// NaryExpr a = this.filter(OrExpr.class, true);
		// a = a.simplifyOnce();
		
		// if (this.children.contains(OrExpr.class)) {
		// 	NaryExpr a = this.filter(NaryExpr.class, false);
		// 	//a = a.simplifyOnce();
		// 	children.add(a);
		// }
		List<Expr> children = new LinkedList<Expr>();
		for (int i = 0; i < this.children.size(); i++) {
			Expr child = this.children.get(i);
			child = child.simplifyOnce();
			children.add(i, child);
		}
		return newNaryExpr(children); // TODO: replace this stub
	}

	
	private NaryExpr mergeGrandchildren() {
		// extract children to merge using filter (because they are the same type as us)
			// if no children to merge, then return this (i.e., no change)
			// use filter to get the other children, which will be kept in the result unchanged
			// merge in the grandchildren
			// assert result.repOk():  this operation should always leave the AST in a legal state
		
		List<Expr> children = new LinkedList<Expr>();
		if (this instanceof NaryOrExpr) {
			NaryExpr a = this.filter(NaryOrExpr.class, true);
			for (int i = 0; i < a.children.size(); i++) {
				NaryOrExpr child = (NaryOrExpr) a.children.get(i);
				for (int j = 0; j < child.children.size(); j++) {
					Expr grandchild = child.children.get(j);
					children.add(grandchild);
				}
			}
			NaryExpr b = this.filter(NaryOrExpr.class, false);
			for (int i = 0; i < b.children.size(); i++) {
				Expr child = b.children.get(i);
				children.add(child);
			}
			return newNaryExpr(children);
		}
		if (this instanceof NaryAndExpr) {
			NaryExpr a = this.filter(NaryAndExpr.class, true);
			for (int i = 0; i < a.children.size(); i++) {
				NaryAndExpr child = (NaryAndExpr) a.children.get(i);
				for (int j = 0; j < child.children.size(); j++) {
					Expr grandchild = child.children.get(j);
					children.add(grandchild);
				}
			}
			NaryExpr b = this.filter(NaryAndExpr.class, false);
			for (int i = 0; i < b.children.size(); i++) {
				Expr child = b.children.get(i);
				children.add(child);
			}
			return newNaryExpr(children);
		}
		return this; // TODO: clean this code
	}


    private NaryExpr foldIdentityElements() {
    	// if we have only one child stop now and return self
		if (this.children.size() == 1) {
			return this;
		}
    	// we have multiple children, remove the identity elements
		NaryExpr a = this.filter(this.getIdentityElement(), Examiner.Equals, false);
		if (this.children.contains(this.getIdentityElement())) {
			return a;
		}
		// all children were identity elements, so now our working list is empty
		if (a.children.size() == 0) {
			// return a new list with a single identity element
			ImmutableList<Expr> l = ImmutableList.of(this.getIdentityElement());
			return newNaryExpr(l);
		}
		return this;
		// normal return
		// TODO: replace this stub
    	// do not assert repOk(): this fold might leave the AST in an illegal state (with only one child)
    }

    private NaryExpr foldAbsorbingElements() {
		// absorbing element: 0.x=0 and 1+x=1
		if (this.children.contains(this.getAbsorbingElement())) {
			// absorbing element is present: return it
			return newNaryExpr(ImmutableList.of(this.getAbsorbingElement()));
		}
			// absorbing element is present: return it
			// not so fast! what is the return type of this method? why does it have to be that way?
			// no absorbing element present, do nothing
		return this; // TODO: replace this stub
    	// do not assert repOk(): this fold might leave the AST in an illegal state (with only one child)
	}

	private NaryExpr foldComplements() {
		// collapse complements
		// !x . x . ... = 0 and !x + x + ... = 1
		NaryExpr a = this.filter(NotExpr.class, true);
		if (a.children.size() == 1) {
			// x op !x = absorbing element
			NotExpr element = (NotExpr)a.children.get(0);
			if (this.children.contains(element.expr)) {
				return newNaryExpr(ImmutableList.of(this.getAbsorbingElement()));
			}
		}
		// find all negations
		// for each negation, see if we find its complement
				// found matching negation and its complement
				// return absorbing element	
		// no complements to fold
		return this; // TODO: replace this stub
    	// do not assert repOk(): this fold might leave the AST in an illegal state (with only one child)
	}

	private NaryExpr removeDuplicates() {
		// remove duplicate children: x.x=x and x+x=x
		ImmutableList<Expr> l = ImmutableList.of();
		for (int i = 0; i < this.children.size(); i++) {
			Expr child = this.children.get(i);
			if (this.children.contains(child) && !l.contains(child)) {
				l = l.append(child);
			}
		}
		// since children are sorted this is fairly easy
		if (l.size() > 0) {
			// removed some duplicates
			return newNaryExpr(l);
		} else {
			// no changes
			return this;
		} // TODO: replace this stub
    	// do not assert repOk(): this fold might leave the AST in an illegal state (with only one child)
	}

	private NaryExpr simpleAbsorption() {
		// (x.y) + x ... = x ...
		// check if there are any conjunctions that can be removed
		List<Expr> l = new LinkedList<Expr>(children);
		NaryExpr a;
		if (this instanceof NaryAndExpr) {
			a = this.filter(NaryOrExpr.class, true);
		} else if (this instanceof NaryOrExpr) {
			a = this.filter(NaryAndExpr.class, true);
		} else {
			return this;
		}
		for (int i = 0; i < this.children.size(); i++) {
			Expr child = this.children.get(i);
			// Check if the child is a subset of any other child
			for (int j = 0; j < a.children.size(); j++) {
				NaryExpr child2 = (NaryExpr)a.children.get(j);
				if (child instanceof NaryExpr) {
					NaryExpr child3 = (NaryExpr)child;
					int count = child3.children.size();
					for (Expr child_min : child3.children) {
						if (child2.children.contains(child_min)) {
							count -= 1;
						}
					}
					if (count == 0 && child3.children.size() < child2.children.size()) {
						l.remove(child2);
					}
				} else {
					for (int n = 0; n < a.children.size(); n++) {
						NaryExpr child5 = (NaryExpr)a.children.get(j);
						if (child5.children.contains(child)) {
							l.remove(child5);
						} 
					}
				}
			}			
		}
		if (l.size() != children.size()) {
			return newNaryExpr(l);
		} else {
			return this;
		}
		// TODO: replace this stub
    	// do not assert repOk(): this operation might leave the AST in an illegal state (with only one child)
	}

	private NaryExpr subsetAbsorption() {
		// check if there are any conjunctions that are supersets of others
		// e.g., ( a . b . c ) + ( a . b ) = a . b
		List<Expr> l = new LinkedList<Expr>(children);
		if (this instanceof NaryOrExpr) {
			for (int i = 0; i < this.children.size(); i++) {
				Expr child = this.children.get(i);
				// Check if the child is a subset of any other child
				if (child instanceof NaryAndExpr) {
					NaryAndExpr child3 = (NaryAndExpr)child;
					NaryExpr a = child3.filter(NaryOrExpr.class, true);
					if (a.children.size() > 0) {
						for (int j = 0; j < a.children.size(); j++) {
							NaryExpr child2 = (NaryExpr)a.children.get(j);
							int count = child2.children.size();
							for (int n = 0; n < child2.children.size(); n++) {
								Expr child4 = child2.children.get(n);
								if (this.children.contains(child4)) {
									count -= 1;
								}
							}
							if (count == 0) {
								l.remove(child);
							}
						}
					}
					// } else {
					// 	for (int j = 0; j < child3.children.size(); j++) {
					// 		Expr child2 = child3.children.get(j);
					// 		if (this.children.contains(child2)) {
					// 			l.remove(child);
					// 		}
					// 	} 
					// }
				}
				// for (int j = 0; j < this.children.size(); j++) {
				// 	Expr child2 = this.children.get(j);
				// 	if (child2 instanceof NaryAndExpr) {
				// 		// NaryAndExpr child3 = (NaryAndExpr)child2;
				// 		NaryExpr a = this.filter(NaryOrExpr.class, true);
				// 		if (child3.children.contains(child)) {
				// 			l.remove(child);
				// 		} 
				// 	}
				// }			
			}
			return newNaryExpr(l);
		}
		return this; // TODO: replace this stub
    	// do not assert repOk(): this operation might leave the AST in an illegal state (with only one child)
	}

	/**
	 * If there is only one child, return it (the containing NaryExpr is unnecessary).
	 */
	private Expr singletonify() {
		// if we have only one child, return it
		// having only one child is an illegal state for an NaryExpr
			// multiple children; nothing to do; return self
		if (this.children.size() == 1) {
			return this.children.get(0);
		}
		return this; // TODO: replace this stub
	}

	/**
	 * Return a new NaryExpr with only the children of a certain type, 
	 * or excluding children of a certain type.
	 * @param filter
	 * @param shouldMatchFilter
	 * @return
	 */
	public final NaryExpr filter(final Class<? extends Expr> filter, final boolean shouldMatchFilter) {
		ImmutableList<Expr> l = ImmutableList.of();
		for (final Expr child : children) {
			if (child.getClass().equals(filter)) {
				if (shouldMatchFilter) {
					l = l.append(child);
				}
			} else {
				if (!shouldMatchFilter) {
					l = l.append(child);
				}
			}
		}
		return newNaryExpr(l);
	}

	public final NaryExpr filter(final Expr filter, final Examiner examiner, final boolean shouldMatchFilter) {
		ImmutableList<Expr> l = ImmutableList.of();
		for (final Expr child : children) {
			if (examiner.examine(child, filter)) {
				if (shouldMatchFilter) {
					l = l.append(child);
				}
			} else {
				if (!shouldMatchFilter) {
					l = l.append(child);
				}
			}
		}
		return newNaryExpr(l);
	}

	public final NaryExpr removeAll(final List<Expr> toRemove, final Examiner examiner) {
		NaryExpr result = this;
		for (final Expr e : toRemove) {
			result = result.filter(e, examiner, false);
		}
		return result;
	}

	public final boolean contains(final Expr expr, final Examiner examiner) {
		for (final Expr child : children) {
			if (examiner.examine(child, expr)) {
				return true;
			}
		}
		return false;
	}

}
