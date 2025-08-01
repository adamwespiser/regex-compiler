package com.wespiser.regexcompiler.algorithms;

import com.wespiser.regexcompiler.Pattern;
import com.wespiser.regexcompiler.RegexCompileException;
import com.wespiser.regexcompiler.Parser;
import java.util.Map;

/**
 * Naive DFA-based implementation using our existing RegexToDFA converter
 * The Naive algorithm converts the regex into a deterministic finite state machine using Thompson's
 * construction followed by subset construction. It first parses the regex into an abstract syntax tree,
 * then converts it to an NFA (Nondeterministic Finite Automaton) using Thompson's method, and finally
 * determinizes it using the powerset construction. The resulting DFA has exactly one transition per
 * input character from each state, making matching very efficient with O(n) time complexity where n is
 * the input length. Space complexity is O(2^m) in the worst case where m is the number of NFA states,
 * though practical patterns often result in much smaller DFAs. The strategy provides guaranteed
 * linear-time matching by pre-computing all possible state transitions, eliminating backtracking
 * entirely.
 */
public class Naive implements Pattern {
    private String patternString;
    private RegexToDFA.DFA dfa;
    private Map<String, Object> stats;
    private long compileTime;
    
    @Override
    public Pattern compile(String regex) throws RegexCompileException {
        long startTime = System.nanoTime();
        
        try {
            Naive compiled = new Naive();
            compiled.patternString = regex;
            
            RegexToDFA converter = new RegexToDFA();
            compiled.dfa = converter.convertRegexToDFAOptimized(regex);
            
            compiled.compileTime = System.nanoTime() - startTime;
            compiled.stats = Map.of(
                "compileTimeNs", compiled.compileTime,
                "stateCount", compiled.dfa.states.size(),
                "alphabetSize", compiled.dfa.alphabet.size(),
                "implementation", "Naive"
            );
            
            return compiled;
            
        } catch (Parser.RegexParseException e) {
            throw new RegexCompileException("Failed to compile regex: " + regex, e);
        }
    }
    
    @Override
    public boolean matches(String input) {
        return dfa.accepts(input);
    }
    
    @Override
    public boolean find(String input) {
        // For find, we need to try matching at each position
        for (int i = 0; i <= input.length(); i++) {
            for (int j = i; j <= input.length(); j++) {
                if (matches(input.substring(i, j))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public String pattern() {
        return patternString;
    }
    
    @Override
    public String getImplementationName() {
        return "Naive DFA-based (Deterministic Finite Automaton)";
    }
    
    @Override
    public Map<String, Object> getCompilationStats() {
        return stats;
    }
}