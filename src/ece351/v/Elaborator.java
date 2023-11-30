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

package ece351.v;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.parboiled.common.ImmutableList;

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
import ece351.common.ast.Statement;
import ece351.common.ast.VarExpr;
import ece351.common.ast.XNOrExpr;
import ece351.common.ast.XOrExpr;
import ece351.common.visitor.PostOrderExprVisitor;
import ece351.util.CommandLine;
import ece351.v.ast.Architecture;
import ece351.v.ast.Component;
import ece351.v.ast.DesignUnit;
import ece351.v.ast.IfElseStatement;
import ece351.v.ast.Process;
import ece351.v.ast.VProgram;

/**
 * Inlines logic in components to architecture body.
 */
public final class Elaborator extends PostOrderExprVisitor {

	private final Map<String, String> current_map = new LinkedHashMap<String, String>();
	
	public static void main(String[] args) {
		System.out.println(elaborate(args));
	}
	
	public static VProgram elaborate(final String[] args) {
		return elaborate(new CommandLine(args));
	}
	
	public static VProgram elaborate(final CommandLine c) {
        final VProgram program = DeSugarer.desugar(c);
        return elaborate(program);
	}
	
	public static VProgram elaborate(final VProgram program) {
		final Elaborator e = new Elaborator();
		return e.elaborateit(program);
	}

	private VProgram elaborateit(final VProgram root) {

		// our ASTs are immutable. so we cannot mutate root.
		// we need to construct a new AST that will be the return value.
		// it will be like the input (root), but different.
		VProgram result = new VProgram();
		int compCount = 0;

		// iterate over all of the designUnits in root.
		for (final DesignUnit du: root.designUnits) {
			// for each one, construct a new architecture.
			Architecture a = du.arch.varyComponents(ImmutableList.<Component>of());
			// this gives us a copy of the architecture with an empty list of components.
			// now we can build up this Architecture with new components.
			if (du.arch.components.size() > 0) {
				for (Component c: du.arch.components) {
					compCount++;
				// In the elaborator, an architectures list of signals, and set of statements may change (grow)
					for (DesignUnit du2: result.designUnits) {
						//populate dictionary/map	
						if (c.entityName.equals(du2.entity.identifier)) {
							int i;
							//add input signals, map to ports
							for (i = 0; i < du2.entity.input.size(); i++) {
								current_map.put(du2.entity.input.get(i), c.signalList.get(i));
							}
							//add output signals, map to ports
							for (int j = 0; j < du2.entity.output.size(); j++, i++) {
								current_map.put(du2.entity.output.get(j), c.signalList.get(i));
							}
							//add local signals, add to signal list of current designUnit
							if (du2.arch.signals.size() > 0) {
								for (String s: du2.arch.signals) {
									current_map.put(s, "comp" + compCount + "_" + s);
									a = a.appendSignal("comp" + compCount + "_" + s);
								}
							}
							//loop through the statements in the architecture body
							for (Statement s: du2.arch.statements) {
								// make the appropriate variable substitutions for signal assignment statements
								// i.e., call changeStatementVars
								if (s instanceof AssignmentStatement) {
									a = a.appendStatement(changeStatementVars((AssignmentStatement) s));
								}
								// make the appropriate variable substitutions for processes (sensitivity list, if/else body statements)
								// i.e., call expandProcessComponent
								else if (s instanceof Process) {
									a = a.appendStatement(expandProcessComponent((Process) s));
								}
							}
						}
					}
				}
			}
			// append this new architecture to result
			result = result.append(du.setArchitecture(a));
		}
		assert result.repOk();
		return result;
	}
	
	// you do not have to use these helper methods; we found them useful though
	private Process expandProcessComponent(final Process process) {
		if (process.sequentialStatements.size() > 0) {
			Process p = new Process();
			for (Statement s: process.sequentialStatements) {
				if (s instanceof IfElseStatement) {
					IfElseStatement i = changeIfVars((IfElseStatement) s);
					p = p.appendStatement(i);
				}
				if (s instanceof AssignmentStatement) {
					AssignmentStatement a = changeStatementVars((AssignmentStatement) s);
					p = p.appendStatement(a);
				}
			}
			for (String s: process.sensitivityList) {
				p = p.appendSensitivity(current_map.get(s));
			}
			return p;
		}
		else {
			return process;
		}
	}
	
	// you do not have to use these helper methods; we found them useful though
	private  IfElseStatement changeIfVars(final IfElseStatement s) {
		ImmutableList<AssignmentStatement> elseBody = ImmutableList.of();
		ImmutableList<AssignmentStatement> ifBody = ImmutableList.of();
		for (AssignmentStatement a: s.elseBody) {
			elseBody = elseBody.append(changeStatementVars(a));
		}
		for (AssignmentStatement a: s.ifBody) {
			ifBody = ifBody.append(changeStatementVars(a));
		}
		return new IfElseStatement(elseBody, ifBody, traverseExpr(s.condition));
	}

	// you do not have to use these helper methods; we found them useful though
	private AssignmentStatement changeStatementVars(final AssignmentStatement s){
		AssignmentStatement a = traverseAssignmentStatement(s);
		return new AssignmentStatement(current_map.get(a.outputVar.toString()), a.expr);
	}
	
	
	@Override
	public Expr visitVar(VarExpr e) {
		if (current_map.containsKey(e.identifier)) {
			return new VarExpr(current_map.get(e.identifier));
		}
		return e;
	}
	
	// do not rewrite these parts of the AST
	@Override public Expr visitConstant(ConstantExpr e) { return e; }
	@Override public Expr visitNot(NotExpr e) { return e; }
	@Override public Expr visitAnd(AndExpr e) { return e; }
	@Override public Expr visitOr(OrExpr e) { return e; }
	@Override public Expr visitXOr(XOrExpr e) { return e; }
	@Override public Expr visitEqual(EqualExpr e) { return e; }
	@Override public Expr visitNAnd(NAndExpr e) { return e; }
	@Override public Expr visitNOr(NOrExpr e) { return e; }
	@Override public Expr visitXNOr(XNOrExpr e) { return e; }
	@Override public Expr visitNaryAnd(NaryAndExpr e) { return e; }
	@Override public Expr visitNaryOr(NaryOrExpr e) { return e; }
}
