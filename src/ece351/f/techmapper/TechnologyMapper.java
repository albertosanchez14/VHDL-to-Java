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

package ece351.f.techmapper;

import java.io.PrintWriter;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.parboiled.common.ImmutableList;

import kodkod.util.collections.IdentityHashSet;
import ece351.common.ast.AndExpr;
import ece351.common.ast.AssignmentStatement;
import ece351.common.ast.ConstantExpr;
import ece351.common.ast.EqualExpr;
import ece351.common.ast.Expr;
import ece351.common.ast.NAndExpr;
import ece351.common.ast.NOrExpr;
import ece351.common.ast.NaryAndExpr;
import ece351.common.ast.NaryOrExpr;
import ece351.common.ast.NotExpr;
import ece351.common.ast.OrExpr;
import ece351.common.ast.VarExpr;
import ece351.common.ast.XNOrExpr;
import ece351.common.ast.XOrExpr;
import ece351.common.visitor.PostOrderExprVisitor;
import ece351.f.FParser;
import ece351.f.analysis.ExtractAllExprs;
import ece351.f.ast.FProgram;
import ece351.util.Examiner;

public final class TechnologyMapper extends PostOrderExprVisitor {

	/** Where we will write the output to. */
	private final PrintWriter out;
	
	/**
	 * Table of substitutions for common subexpression elimination. Note that
	 * this IdentityHashMap has non-deterministic iteration ordering. The staff
	 * solution iterates over substitutions.values(), but does not print those
	 * results directly: instead it stores those results in the nodes TreeSet
	 * (below) which sorts them before they are printed. When computing edges
	 * the staff solution uses this table only for lookups and does not iterate
	 * over the contents.
	 */
	private final IdentityHashMap<Expr,Expr> substitutions = new IdentityHashMap<Expr,Expr>();

	/**
	 * The set of nodes in our circuit diagram. (We'll produce a node for each
	 * .) We could just print the nodes directly to the output stream instead of
	 * building up this set, but then we might output the same node twice, and
	 * we might get a nonsensical order. The set uniqueness property ensure that
	 * we will ultimately print each node exactly once. TreeSet gives us
	 * deterministic iteration order: alphabetical.
	 */
	private final SortedSet<String> nodes = new TreeSet<String>();
	
	/**
	 * The set of edges in our circuit diagram. We could just print the edges
	 * directly to the output stream instead of building up this set, but then
	 * we might output the same edge twice. The set uniqueness property ensure
	 * that we will ultimately print each edge exactly once. LinkedHashSet gives
	 * us deterministic iteration order: insertion order. We need insertion
	 * order here because the elements will be inserted into this set by the
	 * post order traversal of the AST.
	 */
	private final Set<String> edges = new LinkedHashSet<String>();
	
	public TechnologyMapper(final PrintWriter out) {
		this.out = out;
	}
	
	public TechnologyMapper() {
		 this(new PrintWriter(System.out));
	}
	
	public static void main(final String arg) {
		main(new String[]{arg});
	}
	public static void main(final String[] args) {
		render(FParser.parse(args), new PrintWriter(System.out));
	}
	
	/**
	 * Translate an FProgram to Graphviz format.
	 */
	public static void render(final FProgram program, final PrintWriter out) {
		final TechnologyMapper tm = new TechnologyMapper(out);
		tm.render(program);
	}

	public void render(final FProgram program) {
		render(program, Examiner.Isomorphic);
	}

	/** Where the real work happens. */
	public void render(final FProgram program, final Examiner examiner) {
		header(out);
		
		// build a set of all of the exprs in the program
		Set<Expr> exprs = ExtractAllExprs.allExprs(program);
		
		// simplify by the morgan rules
		// for (Expr e : exprs) {
		// 	// -a or -b = -(a and b)
		// 	if (e instanceof OrExpr) {
		// 		Expr left = ((OrExpr) e).left;
		// 		Expr right = ((OrExpr) e).right;
		// 		if (left instanceof NotExpr && right instanceof NotExpr) {
		// 			Expr newExpr = new NotExpr(new AndExpr(((NotExpr) left).expr, ((NotExpr) right).expr));
		// 			substitutions.put(e, newExpr);
		// 		}
		// 	}
		// 	// -a or -b = -(a and b)
		// 	if (e instanceof NaryOrExpr) {
		// 		ImmutableList<Expr> children = ImmutableList.of();
		// 		for (final Expr c : ((NaryOrExpr) e).children) {
		// 			if (c instanceof NotExpr) {
		// 				Expr a = ((NotExpr) c).expr;
		// 				children = children.append(a);
		// 			}
		// 		}
		// 		if (children.size() == ((NaryOrExpr) e).children.size()) {
		// 			NaryAndExpr newExpr = new NaryAndExpr(children);
		// 			// substitutions.put(e, new NotExpr(newExpr));
		// 			exprs.remove(e);
		// 			exprs.add(new NotExpr(newExpr));
		// 		}
		// 	}
		// 	// -a and -b = -(a or b)
		// 	if (e instanceof AndExpr) {
		// 		Expr left = ((AndExpr) e).left;
		// 		Expr right = ((AndExpr) e).right;
		// 		if (left instanceof NotExpr && right instanceof NotExpr) {
		// 			Expr newExpr = new NotExpr(new OrExpr(((NotExpr) left).expr, ((NotExpr) right).expr));
		// 			substitutions.put(e, newExpr);
		// 		}
		// 	}
		// 	// -a and -b = -(a or b)
		// 	if (e instanceof NaryAndExpr) {
		// 		NaryOrExpr newExpr = new NaryOrExpr();
		// 		for (final Expr c : ((NaryAndExpr) e).children) {
		// 			if (c instanceof NotExpr) {
		// 				newExpr.append(((NotExpr) c).expr);
		// 			}
		// 		}
		// 		if (newExpr.children.size() == ((NaryAndExpr) e).children.size()) {
		// 			// substitutions.put(e, newExpr);
		// 			exprs.remove(e);
		// 			exprs.add(newExpr);
		// 		}
		// 	}
		// }
		
		// build substitutions by determining equivalences of exprs
		int i = 0;
		int j = 0;
		for (final Expr e1 : exprs) {
			for (final Expr e2 : exprs) {
				if (i != j) {
					if (examiner.examine(e1, e2)) {
						substitutions.put(e1, e2);
					}
				} else {
					substitutions.put(e1, e1);
				}
				j++;
			}
			i++;
			j = 0;
		}
		for (final Expr e : substitutions.values()) {
			traverseExpr(e);
		}
		// create nodes for output vars
		for (final AssignmentStatement astmt : program.formulas) {
			//visitVar(astmt.outputVar);
			edge(astmt.expr, astmt.outputVar);
		}
		// attach images to gates
		// ../../gates/not_noleads.png
		// ../../gates/or_noleads.png
		// ../../gates/and_noleads.png
		// compute edges
		// print nodes
		for (final String n : nodes) {
			out.println(n);
		}
		// print edges
		for (final String e : edges) {
			out.println(e);
		}		
		// TODO: longer code snippet
		

		// print footer
		footer(out);
		out.flush();
		
		// release memory
		substitutions.clear();
		nodes.clear();
		edges.clear();
	}

	
	private static void header(final PrintWriter out) {
		out.println("digraph g {");
		out.println("    // header");
		out.println("    rankdir=LR;");
		out.println("    margin=0.01;");
		out.println("    node [shape=\"plaintext\"];");
		out.println("    edge [arrowhead=\"diamond\"];");
		out.println("    // circuit ");
	}

	private static void footer(final PrintWriter out) {
		out.println("}");
	}

    /**
     * ConstantExpr follows the Singleton pattern, so we don't need
     * to look in the substitution table: we already know there are
     * only ConstantExpr objects in existence, one for True and one
     * for False.
     */
	@Override
	public Expr visitConstant(final ConstantExpr e) {
		node(e.serialNumber(), e.toString());
		return e;
	}

	@Override
	public Expr visitVar(final VarExpr e) {
		final Expr e2 = substitutions.get(e);
		assert e2 != null : "no substitution for " + e + " " + e.serialNumber();
		node(e2.serialNumber(), e2.toString());
		return e;
	}

	@Override
	public Expr visitNot(final NotExpr e) {
		edge(e.expr, e);
		return e;
	}

	@Override
	public Expr visitAnd(final AndExpr e) {
		// TODO: short code snippet
		final Expr e2 = substitutions.get(e);
		assert e2 != null : "no substitution for " + e + " " + e.serialNumber();
		node(e2.serialNumber(), e2.serialNumber(), "../../gates/and_leads.png");
		edge(e.left, e2);
		edge(e.right, e2);
		return e;
	}

	@Override
	public Expr visitOr(final OrExpr e) {
		// TODO: short code snippet
		final Expr e2 = substitutions.get(e);
		assert e2 != null : "no substitution for " + e + " " + e.serialNumber();
		node(e2.serialNumber(), e2.serialNumber(), "../../gates/or_leads.png");
		edge(e.left, e2);
		edge(e.right, e2);
		return e;
	}
	
	@Override public Expr visitNaryAnd(final NaryAndExpr e) {
		// TODO: short code snippet
		final Expr e2 = substitutions.get(e);
		assert e2 != null : "no substitution for " + e + " " + e.serialNumber();
		node(e2.serialNumber(), e2.serialNumber(), "../../gates/and_noleads.png");
		for (final Expr c : e.children) {
			edge(c, e2);
		}
		return e;
	}

	@Override public Expr visitNaryOr(final NaryOrExpr e) { 
		// TODO: short code snippet
		final Expr e2 = substitutions.get(e);
		assert e2 != null : "no substitution for " + e + " " + e.serialNumber();
		node(e2.serialNumber(), e2.serialNumber(), "../../gates/or_noleads.png");
		for (final Expr c : e.children) {
			edge(c, e2);
		}
		return e;
	}


	private void node(final String name, final String label) {
		nodes.add("    " + name + "[label=\"" + label + "\"];");
	}

	private void node(final String name, final String label, final String image) {
		nodes.add(String.format("    %s [label=\"%s\", image=\"%s\"];", name, label, image));
	}

	private void edge(final Expr source, final Expr target) {
		edge(substitutions.get(source).serialNumber(), substitutions.get(target).serialNumber());
	}
	
	private void edge(final String source, final String target) {
		edges.add("    " + source + " -> " + target + " ;");
	}
	
	@Override public Expr visitXOr(final XOrExpr e) { throw new IllegalStateException("TechnologyMapper does not support " + e.getClass()); }
	@Override public Expr visitNAnd(final NAndExpr e) { throw new IllegalStateException("TechnologyMapper does not support " + e.getClass()); }
	@Override public Expr visitNOr(final NOrExpr e) { throw new IllegalStateException("TechnologyMapper does not support " + e.getClass()); }
	@Override public Expr visitXNOr(final XNOrExpr e) { throw new IllegalStateException("TechnologyMapper does not support " + e.getClass()); }
	@Override public Expr visitEqual(final EqualExpr e) { throw new IllegalStateException("TechnologyMapper does not support " + e.getClass()); }

}
