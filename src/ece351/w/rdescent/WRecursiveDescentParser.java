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

package ece351.w.rdescent;

import java.util.LinkedList;
import java.util.List;

import org.parboiled.common.ImmutableList;

import ece351.util.Lexer;
import ece351.w.ast.WProgram;
import ece351.w.ast.Waveform;

public final class WRecursiveDescentParser {
    private final Lexer lexer;

    public WRecursiveDescentParser(final Lexer lexer) {
        this.lexer = lexer;
    }

    public static WProgram parse(final String input) {
    	final WRecursiveDescentParser p = new WRecursiveDescentParser(new Lexer(input));
        return p.parse();
    }

    public WProgram parse() {
    	// STUB: return null;
        WProgram program = new WProgram();
        program = program.append(waveform());
        while (!lexer.inspectEOF()) {
            program = program.append(waveform());
        }
        lexer.consumeEOF();
        return program;
    }

    public Waveform waveform() {
        String token = id();
        if (lexer.inspect(":")) {
            lexer.consume(":");
        } else {
            throw new IllegalArgumentException();
        }
        ImmutableList<String> bits = bits();
        if (lexer.inspect(";")) {
            lexer.consume(";");
        } else {
            throw new IllegalArgumentException();
        }
        Waveform waveform = new Waveform(bits, token);
        return waveform;
    }

    public String id() {
        // Id
            String token = lexer.debugState();
            if (lexer.inspectID()) {
                lexer.consume(token);
            } else {
                throw new IllegalArgumentException();
            }
            return token;
        }
    
    public void digit() {
    // Digits
        String digits[] = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
        if (lexer.inspect(digits)) {
            lexer.consume(lexer.debugState());
        } else {
            throw new IllegalArgumentException();
        }
    }

    public ImmutableList<String> bits() {
    // Bits
        ImmutableList<String> bits = ImmutableList.of();
        while (lexer.inspect("0", "1")) {
            if (lexer.inspect("0")) {
                bits = bits.append("0");
                lexer.consume("0");
            }
            else if (lexer.inspect("1")) {
                bits = bits.append("1");
                lexer.consume("1");
            } else {
                throw new IllegalArgumentException();
            }
        }
        return bits;
    }
}
