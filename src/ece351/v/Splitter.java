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

import java.util.LinkedHashSet;
import java.util.Set;

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
import ece351.v.ast.DesignUnit;
import ece351.v.ast.IfElseStatement;
import ece351.v.ast.Process;
import ece351.v.ast.VProgram;

/**
 * Process splitter.
 */
public final class Splitter extends PostOrderExprVisitor {
	private final Set<String> usedVarsInExpr = new LinkedHashSet<String>();

	public static void main(String[] args) {
		System.out.println(split(args));
	}
	
	public static VProgram split(final String[] args) {
		return split(new CommandLine(args));
	}
	
	public static VProgram split(final CommandLine c) {
		final VProgram program = DeSugarer.desugar(c);
        return split(program);
	}
	
	public static VProgram split(final VProgram program) {
		VProgram p = Elaborator.elaborate(program);
		final Splitter s = new Splitter();
		return s.splitit(p);
	}

	private VProgram splitit(final VProgram program) {
		VProgram result = new VProgram();
		// Loop over the design units
		for (final DesignUnit du : program.designUnits) {
			if (du.arch == null) continue;
			// Loop over the statements in the architecture
			Architecture newArch = du.arch;
			newArch = newArch.varyStatements(ImmutableList.of());
			for (final Statement statement : du.arch.statements) {
				// Determine if the statement is a process
				if (statement instanceof Process) {
					final Process process = (Process) statement;
					for (final Statement stmt : process.sequentialStatements) {
						// Determine if the process needs to be split into multiple processes
						if (stmt instanceof IfElseStatement) {
							// Split the process if there are if/else statements so that the if/else statements only assign values to one pin
							final IfElseStatement ifElseStmt = (IfElseStatement) stmt;
							ImmutableList<Statement> newStatements = splitIfElseStatement(ifElseStmt);
							for (final Statement s : newStatements) {
								newArch = newArch.appendStatement(s);
							}
						} else {
							// Just append the statement if it is not an if/else statement
							Process newProcess = new Process()
															.setSensitivityList(process.sensitivityList)
															.setStatements(ImmutableList.of(stmt));
							newArch = newArch.appendStatement(newProcess);
						}
					}
				// Not a process so just append the statement
				} else {
					newArch = newArch.appendStatement(statement);
				}
			}
			result = result.append(new DesignUnit(newArch, du.entity));
		}
		return result;
	}
	
	// You do not have to use this helper method, but we found it useful
	
	private ImmutableList<Statement> splitIfElseStatement(final IfElseStatement ifStmt) {
		ImmutableList<Statement> result = ImmutableList.of();
		// loop over each statement in the ifBody
		for (final AssignmentStatement if_as : ifStmt.ifBody) {
			// loop over each statement in the elseBody
			for (final AssignmentStatement else_as : ifStmt.elseBody) {
				// check if outputVars are the same
				if (if_as.outputVar.equals(else_as.outputVar)) {
					// initialize/clear this.usedVarsInExpr
					this.usedVarsInExpr.clear();
					// call traverse a few times to build up this.usedVarsInExpr
					traverseExpr(if_as.expr);
					traverseExpr(else_as.expr);
					traverseExpr(ifStmt.condition);
					// build sensitivity list from this.usedVarsInExpr
					ImmutableList<String> sensitivityList = ImmutableList.of();
					for (final String s : this.usedVarsInExpr) {
						sensitivityList = sensitivityList.append(s);
					}
					// build the resulting list of split statements
					IfElseStatement newIfElse = new IfElseStatement(ImmutableList.of(else_as), ImmutableList.of(if_as), ifStmt.condition);
					result = result.append(new Process()
											.setSensitivityList(sensitivityList)
											.appendStatement(newIfElse));
				}
				// Do not cover the case where the outputVars are not the same
			}
		}
		// return result
		return result;
	}

	@Override
	public Expr visitVar(final VarExpr e) {
		this.usedVarsInExpr.add(e.identifier);
		return e;
	}

	// no-ops
	@Override public Expr visitConstant(ConstantExpr e) { return e; }
	@Override public Expr visitNot(NotExpr e) { return e; }
	@Override public Expr visitAnd(AndExpr e) { return e; }
	@Override public Expr visitOr(OrExpr e) { return e; }
	@Override public Expr visitXOr(XOrExpr e) { return e; }
	@Override public Expr visitNAnd(NAndExpr e) { return e; }
	@Override public Expr visitNOr(NOrExpr e) { return e; }
	@Override public Expr visitXNOr(XNOrExpr e) { return e; }
	@Override public Expr visitEqual(EqualExpr e) { return e; }
	@Override public Expr visitNaryAnd(NaryAndExpr e) { return e; }
	@Override public Expr visitNaryOr(NaryOrExpr e) { return e; }

}
