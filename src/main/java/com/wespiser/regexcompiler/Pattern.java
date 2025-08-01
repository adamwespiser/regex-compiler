package com.wespiser.regexcompiler;

import com.wespiser.regexcompiler.algorithms.Naive;
import com.wespiser.regexcompiler.algorithms.Backtrack;
import com.wespiser.regexcompiler.algorithms.TableDriven;
import com.wespiser.regexcompiler.algorithms.JITRegexPattern;
import java.util.*;

/**
 * Interface mimicking java.util.regex.Pattern for different regex implementations.
 * Allows testing and benchmarking of various regex compilation strategies.
 */
public interface Pattern {
    
    /**
     * Compile a regex pattern string into this implementation
     * @param regex The regular expression string
     * @return A compiled pattern ready for matching
     * @throws RegexCompileException if the regex is invalid
     */
    Pattern compile(String regex) throws RegexCompileException;
    
    /**
     * Test if the entire input string matches this pattern
     * @param input The string to test
     * @return true if the entire string matches, false otherwise
     */
    boolean matches(String input);
    
    /**
     * Test if any substring of the input matches this pattern
     * @param input The string to search in
     * @return true if any substring matches, false otherwise
     */
    boolean find(String input);
    
    /**
     * Get the original pattern string
     * @return The regex pattern string used to compile this pattern
     */
    String pattern();
    
    /**
     * Get implementation name for benchmarking/identification
     * @return A string identifying this implementation
     */
    String getImplementationName();
    
    /**
     * Get compilation statistics (optional)
     * @return Map of stats like compile time, state count, etc.
     */
    default Map<String, Object> getCompilationStats() {
        return Collections.emptyMap();
    }
    
    // ============================================
    // FACTORY METHODS (Static Interface Methods)
    // ============================================
    
    /**
     * Factory method to create a DFA-based implementation
     */
    static Pattern compileDFA(String regex) throws RegexCompileException {
        return new Naive().compile(regex);
    }
    
    /**
     * Factory method to create a backtracking implementation
     */
    static Pattern compileBacktrack(String regex) throws RegexCompileException {
        return new Backtrack().compile(regex);
    }
    
    /**
     * Factory method to create a table-driven implementation
     */
    static Pattern compileTable(String regex) throws RegexCompileException {
        return new TableDriven().compile(regex);
    }
    
    /**
     * Factory method to create a JIT bytecode implementation for maximum performance
     */
    static Pattern compileJIT(String regex) throws RegexCompileException {
        return new JITRegexPattern(regex);
    }
}
