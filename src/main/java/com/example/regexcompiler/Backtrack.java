package com.example.regexcompiler;

import java.util.Map;

/**
 * Backtracking implementation (recursive approach)
 */
class Backtrack implements Pattern {
    private String patternString;
    private Parser.RegexNode ast;
    private Map<String, Object> stats;
    private long compileTime;
    
    @Override
    public Pattern compile(String regex) throws RegexCompileException {
        long startTime = System.nanoTime();
        
        try {
            Backtrack compiled = new Backtrack();
            compiled.patternString = regex;
            
            Parser parser = new Parser();
            compiled.ast = parser.parse(regex);
            
            compiled.compileTime = System.nanoTime() - startTime;
            compiled.stats = Map.of(
                "compileTimeNs", compiled.compileTime,
                "implementation", "Backtracking"
            );
            
            return compiled;
            
        } catch (Parser.RegexParseException e) {
            throw new RegexCompileException("Failed to compile regex: " + regex, e);
        }
    }
    
    @Override
    public boolean matches(String input) {
        return backtrackMatch(ast, input, 0) == input.length();
    }
    
    @Override
    public boolean find(String input) {
        for (int i = 0; i <= input.length(); i++) {
            int matched = backtrackMatch(ast, input, i);
            if (matched > i) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Recursive backtracking matcher
     * @return position after match, or -1 if no match
     */
    private int backtrackMatch(Parser.RegexNode node, String input, int pos) {
        if (node instanceof Parser.CharNode) {
            Parser.CharNode charNode = (Parser.CharNode) node;
            if (pos < input.length() && input.charAt(pos) == charNode.c) {
                return pos + 1;
            }
            return -1;
            
        } else if (node instanceof Parser.ConcatNode) {
            Parser.ConcatNode concat = (Parser.ConcatNode) node;
            int leftResult = backtrackMatch(concat.left, input, pos);
            if (leftResult == -1) return -1;
            return backtrackMatch(concat.right, input, leftResult);
            
        } else if (node instanceof Parser.AlternationNode) {
            Parser.AlternationNode alt = (Parser.AlternationNode) node;
            int leftResult = backtrackMatch(alt.left, input, pos);
            if (leftResult != -1) return leftResult;
            return backtrackMatch(alt.right, input, pos);
            
        } else if (node instanceof Parser.StarNode) {
            Parser.StarNode star = (Parser.StarNode) node;
            // Try zero matches first
            int result = pos;
            
            // Then try one or more matches
            while (true) {
                int matchResult = backtrackMatch(star.child, input, result);
                if (matchResult == -1 || matchResult == result) break;
                result = matchResult;
            }
            return result;
            
        } else if (node instanceof Parser.PlusNode) {
            Parser.PlusNode plus = (Parser.PlusNode) node;
            // Must match at least once
            int result = backtrackMatch(plus.child, input, pos);
            if (result == -1) return -1;
            
            // Then try additional matches
            while (true) {
                int matchResult = backtrackMatch(plus.child, input, result);
                if (matchResult == -1 || matchResult == result) break;
                result = matchResult;
            }
            return result;
            
        } else if (node instanceof Parser.OptionalNode) {
            Parser.OptionalNode opt = (Parser.OptionalNode) node;
            int result = backtrackMatch(opt.child, input, pos);
            return result == -1 ? pos : result;
            
        } else if (node instanceof Parser.EpsilonNode) {
            return pos; // Epsilon always matches without consuming input
        }
        
        return -1;
    }
    
    @Override
    public String pattern() {
        return patternString;
    }
    
    @Override
    public String getImplementationName() {
        return "Backtracking (Recursive)";
    }
    
    @Override
    public Map<String, Object> getCompilationStats() {
        return stats;
    }
}
