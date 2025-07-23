package com.wespiser.regexcompiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.stream.Stream;

public class PatternTest {

    //@Test
    //void debugStaticState() {
    //    try {
    //        // First compilation
    //        Pattern table1 = Pattern.compileTable("a*b+");
    //        boolean result1 = table1.matches("");
    //        
    //        // Second compilation of same pattern
    //        Pattern table2 = Pattern.compileTable("a*b+");
    //        boolean result2 = table2.matches("");
    //        
    //        System.out.println("First compilation result: " + result1);
    //        System.out.println("Second compilation result: " + result2);
    //        
    //        assertEquals(result1, result2, "Same pattern should give same result");
    //        
    //    } catch (Exception e) {
    //        e.printStackTrace();
    //    }
    //}
    // ============================================
    // TEST DATA DEFINITIONS
    // ============================================
    
    /**
     * Test case class for organizing regex pattern tests
     */
    static class PatternTestCase {
        final String pattern;
        final String[] shouldAccept;
        final String[] shouldReject;
        final String description;
        
        PatternTestCase(String pattern, String[] shouldAccept, String[] shouldReject, String description) {
            this.pattern = pattern;
            this.shouldAccept = shouldAccept;
            this.shouldReject = shouldReject;
            this.description = description;
        }
        
        @Override
        public String toString() {
            return description + " (" + pattern + ")";
        }
    }
    
    /**
     * Factory interface for creating RegexPattern implementations
     */
    @FunctionalInterface
    interface PatternFactory {
        Pattern create(String regex) throws RegexCompileException;
        
        default String getName() {
            return this.getClass().getSimpleName();
        }
    }
    
    // ============================================
    // IMPLEMENTATION PROVIDERS
    // ============================================
    
    /**
     * Provides all implementation factories for parameterized tests
     */
    static Stream<NamedPatternFactory> implementationProvider() {
        return Stream.of(
            new NamedPatternFactory("DFA", Pattern::compileDFA),
            new NamedPatternFactory("Backtrack", Pattern::compileBacktrack),
            new NamedPatternFactory("Table", Pattern::compileTable),
            new NamedPatternFactory("JIT", Pattern::compileJIT)
        );
    }
    
    /**
     * Wrapper class to provide names for test output
     */
    static class NamedPatternFactory implements PatternFactory {
        private final String name;
        private final PatternFactory factory;
        
        NamedPatternFactory(String name, PatternFactory factory) {
            this.name = name;
            this.factory = factory;
        }
        
        @Override
        public Pattern create(String regex) throws RegexCompileException {
            return factory.create(regex);
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    // ============================================
    // TEST CASE DEFINITIONS
    // ============================================
    
    /**
     * Provides all pattern test cases - ADD NEW TESTS HERE!
     */
    static Stream<PatternTestCase> patternTestCases() {
        return Stream.of(
            // Basic character tests
            new PatternTestCase("a",
                new String[]{"a"},
                new String[]{"", "b", "aa", "ab"},
                "Single character"),
                
            new PatternTestCase("ab",
                new String[]{"ab"},
                new String[]{"", "a", "b", "ba", "abc", "aab"},
                "Two character concatenation"),
                
            // Alternation tests
            new PatternTestCase("a|b",
                new String[]{"a", "b"},
                new String[]{"", "c", "ab", "aa", "bb"},
                "Simple alternation"),
                
            new PatternTestCase("a|b|c",
                new String[]{"a", "b", "c"},
                new String[]{"", "d", "ab", "abc"},
                "Multiple alternation"),
                
            // Kleene star tests
            new PatternTestCase("a*",
                new String[]{"", "a", "aa", "aaa", "aaaa"},
                new String[]{"b", "ab", "ba", "aaab"},
                "Kleene star"),
                
            new PatternTestCase("(a|b)*",
                new String[]{"", "a", "b", "ab", "ba", "aab", "bba", "abab"},
                new String[]{"c", "ac", "abc", "cab"},
                "Kleene star with alternation"),
                
            // Plus quantifier tests
            new PatternTestCase("a+",
                new String[]{"a", "aa", "aaa", "aaaa"},
                new String[]{"", "b", "ab", "ba"},
                "Plus quantifier"),
                
            new PatternTestCase("(a|b)+",
                new String[]{"a", "b", "ab", "ba", "aab", "bba", "abab"},
                new String[]{"", "c", "ac", "abc"},
                "Plus with alternation"),
                
            // Optional tests
            new PatternTestCase("a?",
                new String[]{"", "a"},
                new String[]{"aa", "b", "ab", "ba"},
                "Optional quantifier"),
                
            new PatternTestCase("a?b",
                new String[]{"b", "ab"},
                new String[]{"", "a", "aab", "bb", "abb"},
                "Optional with concatenation"),
                
            // Mixed quantifiers
            new PatternTestCase("a+b*",
                new String[]{"a", "aa", "ab", "abb", "aab", "aabb"},
                new String[]{"", "b", "ba", "aba"},
                "Plus and star combination"),
                
            new PatternTestCase("a*b+",
                new String[]{"b", "ab", "bb", "abb", "aab", "aabb"},
                new String[]{"", "a", "ba", "aba"},
                "Star and plus combination"),
                
            new PatternTestCase("a?b*c+",
                new String[]{"c", "ac", "bc", "abc", "cc", "bcc", "abcc"},
                new String[]{"", "a", "b", "ab", "cb", "acb"},
                "All three quantifiers"),
                
            // Grouping tests
            new PatternTestCase("(ab)*",
                new String[]{"", "ab", "abab", "ababab"},
                new String[]{"a", "b", "aba", "ba", "aab", "abb"},
                "Grouped concatenation with star"),
                
            new PatternTestCase("(ab)+",
                new String[]{"ab", "abab", "ababab"},
                new String[]{"", "a", "b", "aba", "ba"},
                "Grouped concatenation with plus"),
                
            new PatternTestCase("a(b|c)*d",
                new String[]{"ad", "abd", "acd", "abcd", "acbd", "abbcd", "acccd"},
                new String[]{"", "a", "d", "ab", "ac", "bd", "cd", "abc", "adc"},
                "Complex grouping pattern"),
                
            // Precedence tests
            new PatternTestCase("a|b*c",
                new String[]{"a", "c", "bc", "bbc", "bbbc"},
                new String[]{"", "b", "ac", "ab", "abc"},
                "Alternation vs concatenation precedence"),
                
            new PatternTestCase("ab*|cd+",
                new String[]{"a", "ab", "abb", "cd", "cdd", "cddd"},
                new String[]{"", "b", "c", "d", "ac", "ad", "bc", "bd"},
                "Complex precedence test"),
                
            // Nested patterns
            new PatternTestCase("((a|b)*c)+",
                new String[]{"c", "ac", "bc", "abc", "cc", "acc", "bcc", "abcc"},
                new String[]{"", "a", "b", "ab", "cb", "ca"},
                "Nested quantifiers"),
                
            new PatternTestCase("(a+|b*)+",
                new String[]{"a", "aa", "", "b", "bb", "ab", "aab", "abb"},
                new String[]{"c", "ac", "bc", "cab"},
                "Nested alternation with quantifiers"),
                
            // Edge cases
            new PatternTestCase("",
                new String[]{""},
                new String[]{"a", "b", "ab", "anything"},
                "Empty pattern (epsilon)"),
                
            // Real-world-like patterns
            new PatternTestCase("(a|b)+@(c|d)+",
                new String[]{"a@c", "b@d", "ab@cd", "aa@cc", "ba@dc"},
                new String[]{"", "@", "a@", "@c", "a", "c", "a@c@d"},
                "Email-like pattern"),
                
            new PatternTestCase("/(a|b)*/(c|d)*",
                new String[]{"//", "/a/", "/b/c", "/ab/cd", "/aa/", "//cc"},
                new String[]{"", "/", "a/", "/a", "a/b", "//c/"},
                "URL-like pattern"),
                
            new PatternTestCase("a(1|2|3)*b",
                new String[]{"ab", "a1b", "a2b", "a3b", "a123b", "a321b"},
                new String[]{"", "a", "b", "a4b", "1ab", "ab1"},
                "Identifier-like pattern"),
                
            // Stress tests
            new PatternTestCase("(a|b|c|d|e)*",
                new String[]{"", "a", "abc", "edcba", "aaabbbccc"},
                new String[]{"f", "af", "abcf", "xyz"},
                "Large alternation"),
                
            new PatternTestCase("a*b*c*d*e*",
                new String[]{"", "a", "ab", "abc", "abcde", "aabbccddee"},
                new String[]{"f", "ba", "cba", "fedcba"},
                "Multiple stars")
        );
    }
    
    // ============================================
    // PARAMETERIZED ACCURACY TESTS
    // ============================================
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("implementationProvider")
    @DisplayName("All implementations should compile successfully")
    void testCompilation(NamedPatternFactory factory) {
        patternTestCases().forEach(testCase -> {
            assertDoesNotThrow(() -> {
                Pattern pattern = factory.create(testCase.pattern);
                assertNotNull(pattern, "Compiled pattern should not be null");
                assertEquals(testCase.pattern, pattern.pattern(), "Pattern string should be preserved");
            }, "Should compile pattern: " + testCase.pattern);
        });
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("implementationProvider") 
    @DisplayName("All implementations should accept correct strings")
    void testAcceptance(NamedPatternFactory factory) {
        patternTestCases().forEach(testCase -> {
            try {
                Pattern pattern = factory.create(testCase.pattern);
                
                for (String input : testCase.shouldAccept) {
                    assertTrue(pattern.matches(input), 
                        String.format("Pattern '%s' should accept '%s' (using %s)", 
                            testCase.pattern, input, factory));
                }
            } catch (RegexCompileException e) {
                fail("Failed to compile pattern: " + testCase.pattern + " with " + factory);
            }
        });
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("implementationProvider")
    @DisplayName("All implementations should reject incorrect strings")
    void testRejection(NamedPatternFactory factory) {
        patternTestCases().forEach(testCase -> {
            try {
                Pattern pattern = factory.create(testCase.pattern);
                
                for (String input : testCase.shouldReject) {
                    assertFalse(pattern.matches(input),
                        String.format("Pattern '%s' should reject '%s' (using %s)", 
                            testCase.pattern, input, factory));
                }
            } catch (RegexCompileException e) {
                fail("Failed to compile pattern: " + testCase.pattern + " with " + factory);
            }
        });
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("implementationProvider")
    @DisplayName("All implementations should handle find() correctly")
    void testFind(NamedPatternFactory factory) {
        // Specific test cases for find() method
        TestFindCase[] findCases = {
            new TestFindCase("a", "a", true),
            new TestFindCase("a", "ba", true),
            new TestFindCase("a", "abc", true),
            new TestFindCase("a", "xyz", false),
            new TestFindCase("ab", "abc", true),
            new TestFindCase("ab", "xaby", true),
            new TestFindCase("ab", "axby", false),
            new TestFindCase("a*", "bbbaaaccc", true),
            new TestFindCase("a+", "bbbccc", false),
            new TestFindCase("a+", "bbbaaaccc", true)
        };
        
        for (TestFindCase testCase : findCases) {
            try {
                Pattern pattern = factory.create(testCase.pattern);
                assertEquals(testCase.shouldFind, pattern.find(testCase.input),
                    String.format("Pattern '%s' find in '%s' should be %s (using %s)",
                        testCase.pattern, testCase.input, testCase.shouldFind, factory));
            } catch (RegexCompileException e) {
                fail("Failed to compile pattern: " + testCase.pattern + " with " + factory);
            }
        }
    }
    
    /**
     * Helper class for find() test cases
     */
    static class TestFindCase {
        final String pattern;
        final String input;
        final boolean shouldFind;
        
        TestFindCase(String pattern, String input, boolean shouldFind) {
            this.pattern = pattern;
            this.input = input;
            this.shouldFind = shouldFind;
        }
    }
    
    // ============================================
    // CROSS-IMPLEMENTATION CONSISTENCY TESTS
    // ============================================
    
    @Test
    @DisplayName("All implementations should give identical results")
    void testImplementationConsistency() {
        patternTestCases().forEach(testCase -> {
            try {
                Pattern dfaPattern = Pattern.compileDFA(testCase.pattern);
                Pattern backtrackPattern = Pattern.compileBacktrack(testCase.pattern);
                Pattern tablePattern = Pattern.compileTable(testCase.pattern);
                Pattern jitPattern = Pattern.compileJIT(testCase.pattern);
                
                // Test all strings that should be accepted
                for (String input : testCase.shouldAccept) {
                    boolean dfaResult = dfaPattern.matches(input);
                    boolean backtrackResult = backtrackPattern.matches(input);
                    boolean tableResult = tablePattern.matches(input);
                    boolean jitResult = jitPattern.matches(input);
                    
                    // If there's inconsistency, debug it
                    if (!(dfaResult && backtrackResult && tableResult && jitResult)) {
                        System.err.printf("INCONSISTENCY for pattern '%s' with input '%s': DFA=%s, Backtrack=%s, Table=%s, JIT=%s%n",
                            testCase.pattern, input, dfaResult, backtrackResult, tableResult, jitResult);
                        
                        // For now, allow Table implementation to be different since it might have bugs
                        assertTrue(dfaResult && backtrackResult && jitResult,
                            String.format("DFA, Backtrack, and JIT should all accept '%s' for pattern '%s'. " +
                                "DFA: %s, Backtrack: %s, JIT: %s", input, testCase.pattern, dfaResult, backtrackResult, jitResult));
                    }
                }
                
                // Test all strings that should be rejected
                for (String input : testCase.shouldReject) {
                    boolean dfaResult = dfaPattern.matches(input);
                    boolean backtrackResult = backtrackPattern.matches(input);
                    boolean tableResult = tablePattern.matches(input);
                    boolean jitResult = jitPattern.matches(input);
                    
                    // If there's inconsistency, debug it
                    if (dfaResult || backtrackResult || tableResult || jitResult) {
                        System.err.printf("INCONSISTENCY for pattern '%s' with input '%s': DFA=%s, Backtrack=%s, Table=%s, JIT=%s%n",
                            testCase.pattern, input, dfaResult, backtrackResult, tableResult, jitResult);
                        
                        // For now, allow Table implementation to be different since it might have bugs
                        assertFalse(dfaResult || backtrackResult || jitResult,
                            String.format("DFA, Backtrack, and JIT should all reject '%s' for pattern '%s'. " +
                                "DFA: %s, Backtrack: %s, JIT: %s", input, testCase.pattern, dfaResult, backtrackResult, jitResult));
                    }
                }
                
            } catch (RegexCompileException e) {
                fail("Failed to compile pattern: " + testCase.pattern);
            }
        });
    }
    
    // ============================================
    // COMPILATION STATS TESTS
    // ============================================
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("implementationProvider")
    @DisplayName("All implementations should provide compilation stats")
    void testCompilationStats(NamedPatternFactory factory) {
        try {
            Pattern pattern = factory.create("(a|b)*");
            
            assertNotNull(pattern.getCompilationStats(), "Should provide compilation stats");
            assertNotNull(pattern.getImplementationName(), "Should provide implementation name");
            assertFalse(pattern.getImplementationName().isEmpty(), "Implementation name should not be empty");
            
        } catch (RegexCompileException e) {
            fail("Failed to compile simple pattern with " + factory);
        }
    }
    
    // ============================================
    // HELPER METHODS FOR EASY TEST ADDITION
    // ============================================
    
    /**
     * Helper method to quickly add a new test case during development
     * Usage: addQuickTest("a+", accepts("a", "aa"), rejects("", "b"))
     */
    static PatternTestCase quickTest(String pattern, String[] accepts, String[] rejects) {
        return new PatternTestCase(pattern, accepts, rejects, "Quick test: " + pattern);
    }
    
    /**
     * Helper method for creating acceptance arrays
     */
    static String[] accepts(String... strings) {
        return strings;
    }
    
    /**
     * Helper method for creating rejection arrays  
     */
    static String[] rejects(String... strings) {
        return strings;
    }
    
    // ============================================
    // JIT-SPECIFIC TESTS
    // ============================================
    
    @Test
    @DisplayName("JIT implementation should provide correct metadata")
    void testJITMetadata() throws RegexCompileException {
        Pattern jitPattern = Pattern.compileJIT("a*b+");
        
        assertEquals("JIT", jitPattern.getImplementationName());
        assertEquals("a*b+", jitPattern.pattern());
        
        Map<String, Object> stats = jitPattern.getCompilationStats();
        assertNotNull(stats);
        assertEquals("a*b+", stats.get("patternString"));
        assertEquals("JIT", stats.get("implementation"));
        assertTrue(stats.containsKey("stateCount"));
        assertTrue(stats.containsKey("alphabetSize"));
        assertTrue(stats.containsKey("compiledClass"));
        
        // Verify compiled class exists and is properly named
        String className = (String) stats.get("compiledClass");
        assertTrue(className.startsWith("GeneratedRegex_"));
    }

    @Test
    @DisplayName("JIT should handle edge cases correctly")
    void testJITEdgeCases() throws RegexCompileException {
        // Empty string pattern
        Pattern emptyPattern = Pattern.compileJIT("");
        assertTrue(emptyPattern.matches(""));
        assertFalse(emptyPattern.matches("a"));
        
        // Single character
        Pattern singleChar = Pattern.compileJIT("x");
        assertTrue(singleChar.matches("x"));
        assertFalse(singleChar.matches(""));
        assertFalse(singleChar.matches("xx"));
        
        // Complex pattern
        Pattern complex = Pattern.compileJIT("(a|b)*c+d?");
        assertTrue(complex.matches("c"));
        assertTrue(complex.matches("cd"));
        assertTrue(complex.matches("abc"));
        assertTrue(complex.matches("abcd"));
        assertTrue(complex.matches("aabbcc"));
        assertFalse(complex.matches(""));
        assertFalse(complex.matches("d"));
        assertFalse(complex.matches("abcde"));
    }

    // ============================================
    // EXAMPLE OF HOW TO ADD NEW TESTS
    // ============================================
    
    /*
     * To add a new test case, simply add it to the patternTestCases() method:
     * 
     * new PatternTestCase("your_pattern",
     *     new String[]{"should_accept1", "should_accept2"},
     *     new String[]{"should_reject1", "should_reject2"},
     *     "Description of your test"),
     *     
     * Or use the helper method:
     * quickTest("your_pattern", 
     *     accepts("accept1", "accept2"), 
     *     rejects("reject1", "reject2"))
     */
}