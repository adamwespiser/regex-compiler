package com.wespiser.regexcompiler;

import java.util.Map;

/**
 * DFA-based implementation using our existing RegexToDFA converter
 */
class DFARegexPattern implements Pattern {
    private String patternString;
    private RegexToDFA.DFA dfa;
    private Map<String, Object> stats;
    private long compileTime;
    
    @Override
    public Pattern compile(String regex) throws RegexCompileException {
        long startTime = System.nanoTime();
        
        try {
            DFARegexPattern compiled = new DFARegexPattern();
            compiled.patternString = regex;
            
            RegexToDFA converter = new RegexToDFA();
            compiled.dfa = converter.convertRegexToDFAOptimized(regex);
            
            compiled.compileTime = System.nanoTime() - startTime;
            compiled.stats = Map.of(
                "compileTimeNs", compiled.compileTime,
                "stateCount", compiled.dfa.states.size(),
                "alphabetSize", compiled.dfa.alphabet.size(),
                "implementation", "DFA"
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
        return "DFA-based (Deterministic Finite Automaton)";
    }
    
    @Override
    public Map<String, Object> getCompilationStats() {
        return stats;
    }
}