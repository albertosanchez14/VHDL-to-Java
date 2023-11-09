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

package ece351.f.parboiled;

import java.lang.invoke.MethodHandles;

import org.parboiled.Rule;

import ece351.common.ast.AndExpr;
import ece351.common.ast.AssignmentStatement;
import ece351.common.ast.ConstantExpr;
import ece351.common.ast.Constants;
import ece351.common.ast.Expr;
import ece351.common.ast.NotExpr;
import ece351.common.ast.OrExpr;
import ece351.common.ast.VarExpr;
import ece351.f.ast.FProgram;
import ece351.util.CommandLine;

// Parboiled requires that this class not be final
public /*final*/ class FParboiledParser extends FBase implements Constants {

	public static Class<?> findLoadedClass(String className) throws IllegalAccessException {
        try {
            return MethodHandles.lookup().findClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Class<?> loadClass(byte[] code) throws IllegalAccessException {
        return MethodHandles.lookup().defineClass(code);
    }
	public static void main(final String[] args) {
    	final CommandLine c = new CommandLine(args);
    	final String input = c.readInputSpec();
    	final FProgram fprogram = parse(input);
    	assert fprogram.repOk();
    	final String output = fprogram.toString();
    	
    	// if we strip spaces and parens input and output should be the same
    	if (strip(input).equals(strip(output))) {
    		// success: return quietly
    		return;
    	} else {
    		// failure: make a noise
    		System.err.println("parsed value not equal to input:");
    		System.err.println("    " + strip(input));
    		System.err.println("    " + strip(output));
    		System.exit(1);
    	}
    }
	
	private static String strip(final String s) {
		return s.replaceAll("\\s", "").replaceAll("\\(", "").replaceAll("\\)", "");
	}
	
	public static FProgram parse(final String inputText) {
		final FProgram result = (FProgram) process(FParboiledParser.class, inputText).resultValue;
		assert result.repOk();
		return result;
	}

	@Override
	public Rule Program() {
		return Sequence(
			push(new FProgram()),
			OneOrMore(Formula()), 
			EOI);
	}
	
	public Rule Formula() {
        return Sequence(
			Var(),
			push(new VarExpr((String)match())),
			W0(), 
			"<=", 
			W0(), 
			Expr(),
			swap(),
			W0(), 
			";", 
			W0(),
			push(new AssignmentStatement((VarExpr)pop(), (Expr)pop())),
			swap(),
			push(((FProgram)pop()).append((AssignmentStatement)pop())));
    }

    public Rule Expr() {
        return Sequence(
			W0(),
			Term(), 
			W0(), 
			ZeroOrMore(Sequence(
				OR(), 
				W0(), 
				Term(), 
				W0(), 
				swap(),
				push(new OrExpr((Expr)pop(), (Expr)pop()))))
			);
    }

    public Rule Term() {
        return Sequence(
			W0(),
			Factor(),
			W0(), 
			ZeroOrMore(Sequence(
				AND(), 
				W0(), 
				Factor(),
				W0(),
				swap(),
				push(new AndExpr((Expr)pop(), (Expr)pop())))));
    }

    public Rule Factor() {
        return FirstOf(
			Sequence(
				NOT(),
				W0(),
				Factor(),
				push(new NotExpr((Expr)pop()))),
			Sequence(
				"(", 
				W0(), 
				Expr(),
				W0(), 
				")"),
			Sequence(
				Var(),
				push(match()),
				push(new VarExpr((String)pop()))),
			Sequence(
				Constant(),
				push(ConstantExpr.make((String)pop()))));
    }

    public Rule Var() {
        return Sequence(FirstOf(CharRange('a', 'z'), CharRange('A', 'Z')), ZeroOrMore(FirstOf(CharRange('a', 'z'), CharRange('A', 'Z'), CharRange('0', '9'), '_')));
    }

    public Rule Constant() {
        return Sequence("'",CharRange('0', '1'), push(match()),"'");
    }
}
