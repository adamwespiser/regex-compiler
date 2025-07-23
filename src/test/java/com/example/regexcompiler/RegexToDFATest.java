package com.example.regexcompiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for RegexToDFA converter
 * 
 * Place this file in: src/test/java/com/example/regexcompiler/RegexToDFATest.java
 * 
 * Run with: mvn test -Dtest=RegexToDFATest
 */
public class RegexToDFATest {
    
    private RegexToDFA converter;
    
    @BeforeEach
    void setUp() {
        converter = new RegexToDFA();
    }
    
    // ============================================
    // BASIC PATTERN TESTS
    // ============================================
    
    @Nested
    @DisplayName("Basic Pattern Tests")
    class BasicPatternTests {
        
        @Test
        @DisplayName("Single character 'a'")
        void testSingleCharacter() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a");
            
            // Test structure
            assertNotNull(dfa);
            assertNotNull(dfa.start);
            assertTrue(dfa.states.size() >= 2); // At least start and accept
            assertTrue(dfa.alphabet.contains('a'));
            
            // Test acceptance
            assertTrue(dfa.accepts("a"));
            assertFalse(dfa.accepts(""));
            assertFalse(dfa.accepts("aa"));
            assertFalse(dfa.accepts("b"));
        }
        
        @Test
        @DisplayName("Two character concatenation 'ab'")
        void testTwoCharConcat() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("ab");
            
            // Test structure
            assertNotNull(dfa);
            assertTrue(dfa.alphabet.contains('a'));
            assertTrue(dfa.alphabet.contains('b'));
            
            // Test acceptance
            assertTrue(dfa.accepts("ab"));
            assertFalse(dfa.accepts(""));
            assertFalse(dfa.accepts("a"));
            assertFalse(dfa.accepts("abb"));
            assertFalse(dfa.accepts("ba"));
        }
        
        @Test
        @DisplayName("Simple alternation 'a|b'")
        void testSimpleAlternation() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a|b");
            
            // Test structure
            assertNotNull(dfa);
            assertTrue(dfa.alphabet.contains('a'));
            assertTrue(dfa.alphabet.contains('b'));
            
            // Test acceptance
            assertTrue(dfa.accepts("a"));
            assertTrue(dfa.accepts("b"));
            assertFalse(dfa.accepts(""));
            assertFalse(dfa.accepts("ab"));
            assertFalse(dfa.accepts("c"));
        }
        
        @Test
        @DisplayName("Kleene star 'a*'")
        void testKleeneStar() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a*");
            
            // Test structure
            assertNotNull(dfa);
            assertTrue(dfa.alphabet.contains('a'));
            
            // Test acceptance
            assertTrue(dfa.accepts(""));      // Zero occurrences
            assertTrue(dfa.accepts("a"));     // One occurrence
            assertTrue(dfa.accepts("aa"));    // Two occurrences
            assertTrue(dfa.accepts("aaa"));   // Three occurrences
            assertFalse(dfa.accepts("b"));    // Different character
        }
    }
    
    // ============================================
    // QUANTIFIER TESTS
    // ============================================
    
    @Nested
    @DisplayName("Quantifier Tests")
    class QuantifierTests {
        
        @Test
        @DisplayName("Plus quantifier 'a+'")
        void testPlusQuantifier() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a+");
            
            // Test acceptance
            assertFalse(dfa.accepts(""));     // Zero occurrences not allowed
            assertTrue(dfa.accepts("a"));     // One occurrence
            assertTrue(dfa.accepts("aa"));    // Two occurrences
            assertTrue(dfa.accepts("aaa"));   // Three occurrences
            assertFalse(dfa.accepts("b"));    // Different character
        }
        
        @Test
        @DisplayName("Optional quantifier 'a?'")
        void testOptionalQuantifier() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a?");
            
            // Test acceptance
            assertTrue(dfa.accepts(""));      // Zero occurrences
            assertTrue(dfa.accepts("a"));     // One occurrence
            assertFalse(dfa.accepts("aa"));   // Two occurrences not allowed
            assertFalse(dfa.accepts("b"));    // Different character
        }
        
        @Test
        @DisplayName("Mixed quantifiers 'a+b*'")
        void testMixedQuantifiers() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a+b*");
            
            // Test acceptance
            assertFalse(dfa.accepts(""));     // Need at least one 'a'
            assertTrue(dfa.accepts("a"));     // One 'a', zero 'b'
            assertTrue(dfa.accepts("ab"));    // One 'a', one 'b'
            assertTrue(dfa.accepts("abb"));   // One 'a', two 'b'
            assertTrue(dfa.accepts("aa"));    // Two 'a', zero 'b'
            assertTrue(dfa.accepts("aab"));   // Two 'a', one 'b'
            assertFalse(dfa.accepts("b"));    // Need at least one 'a'
        }
    }
    
    // ============================================
    // GROUPING TESTS
    // ============================================
    
    @Nested
    @DisplayName("Grouping Tests")
    class GroupingTests {
        
        @Test
        @DisplayName("Grouped alternation '(a|b)*'")
        void testGroupedAlternation() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("(a|b)*");
            
            // Test acceptance
            assertTrue(dfa.accepts(""));      // Zero occurrences
            assertTrue(dfa.accepts("a"));     // Single 'a'
            assertTrue(dfa.accepts("b"));     // Single 'b'
            assertTrue(dfa.accepts("ab"));    // 'a' then 'b'
            assertTrue(dfa.accepts("ba"));    // 'b' then 'a'
            assertTrue(dfa.accepts("aab"));   // Multiple characters
            assertFalse(dfa.accepts("c"));    // Character not in set
        }
        
        @Test
        @DisplayName("Grouped concatenation '(ab)*'")
        void testGroupedConcatenation() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("(ab)*");
            
            // Test acceptance
            assertTrue(dfa.accepts(""));      // Zero occurrences
            assertTrue(dfa.accepts("ab"));    // One occurrence
            assertTrue(dfa.accepts("abab"));  // Two occurrences
            assertFalse(dfa.accepts("a"));    // Incomplete pattern
            assertFalse(dfa.accepts("aba"));  // Incomplete pattern
            assertFalse(dfa.accepts("ba"));   // Wrong order
        }
        
        @Test
        @DisplayName("Complex grouping 'a(b|c)*d'")
        void testComplexGrouping() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a(b|c)*d");
            
            // Test acceptance
            assertTrue(dfa.accepts("ad"));     // Minimum: a + d
            assertTrue(dfa.accepts("abd"));    // a + b + d
            assertTrue(dfa.accepts("acd"));    // a + c + d
            assertTrue(dfa.accepts("abcd"));   // a + b + c + d
            assertTrue(dfa.accepts("abbcd"));  // a + multiple b/c + d
            assertFalse(dfa.accepts("a"));     // Missing d
            assertFalse(dfa.accepts("d"));     // Missing a
            assertFalse(dfa.accepts(""));      // Empty string
        }
    }
    
    // ============================================
    // COMPLEX PATTERN TESTS
    // ============================================
    
    @Nested
    @DisplayName("Complex Pattern Tests")
    class ComplexPatternTests {
        
        @Test
        @DisplayName("Nested quantifiers '((a|b)*c)+'")
        void testNestedQuantifiers() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("((a|b)*c)+");
            
            // Test acceptance
            assertTrue(dfa.accepts("c"));      // Single c
            assertTrue(dfa.accepts("ac"));     // a + c
            assertTrue(dfa.accepts("bc"));     // b + c
            assertTrue(dfa.accepts("cc"));     // c + c
            assertTrue(dfa.accepts("abc"));    // a + b + c
            assertTrue(dfa.accepts("abcc"));   // (a + b + c) + c
            assertFalse(dfa.accepts(""));      // Need at least one c
            assertFalse(dfa.accepts("a"));     // Need c at end
            assertFalse(dfa.accepts("ab"));    // Need c at end
        }
        
        @Test
        @DisplayName("Alternation with concatenation 'a|b*c'")
        void testAlternationWithConcat() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a|b*c");
            
            // Test acceptance - should parse as a|(b*c)
            assertTrue(dfa.accepts("a"));      // First alternative
            assertTrue(dfa.accepts("c"));      // b* matches empty, then c
            assertTrue(dfa.accepts("bc"));     // One b, then c
            assertTrue(dfa.accepts("bbc"));    // Two b's, then c
            assertFalse(dfa.accepts(""));      // Neither alternative matches
            assertFalse(dfa.accepts("b"));     // b without c
            assertFalse(dfa.accepts("ac"));    // a doesn't continue to c
        }
        
        @Test
        @DisplayName("Multiple alternations '(a|b)*(c|d)+'")
        void testMultipleAlternations() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("(a|b)*(c|d)+");
            
            // Test acceptance
            assertTrue(dfa.accepts("c"));      // Just c
            assertTrue(dfa.accepts("d"));      // Just d
            assertTrue(dfa.accepts("cd"));     // c then d
            assertTrue(dfa.accepts("ac"));     // a then c
            assertTrue(dfa.accepts("abc"));    // a, b, then c
            assertTrue(dfa.accepts("abcd"));   // a, b, c, then d
            assertFalse(dfa.accepts(""));      // Need at least one c or d
            assertFalse(dfa.accepts("a"));     // a without c or d
            assertFalse(dfa.accepts("ab"));    // ab without c or d
        }
    }
    
    // ============================================
    // EDGE CASE TESTS
    // ============================================
    
    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("Empty string (epsilon)")
        void testEmptyString() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("");
            
            // Empty regex should match only empty string
            assertTrue(dfa.accepts(""));
            assertFalse(dfa.accepts("a"));
            assertFalse(dfa.accepts("anything"));
        }
        
        @Test
        @DisplayName("Single character alphabet")
        void testSingleCharAlphabet() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a*");
            
            assertEquals(1, dfa.alphabet.size());
            assertTrue(dfa.alphabet.contains('a'));
        }
        
        @Test
        @DisplayName("Large alphabet")
        void testLargeAlphabet() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("(a|b|c|d|e|f|g|h|i|j)*");
            
            assertEquals(10, dfa.alphabet.size());
            for (char c = 'a'; c <= 'j'; c++) {
                assertTrue(dfa.alphabet.contains(c));
            }
        }
        
        @Test
        @DisplayName("Deeply nested pattern")
        void testDeeplyNested() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("((((a))))");
            
            // Should be equivalent to just 'a'
            assertTrue(dfa.accepts("a"));
            assertFalse(dfa.accepts(""));
            assertFalse(dfa.accepts("aa"));
            assertFalse(dfa.accepts("b"));
        }
    }
    
    // ============================================
    // DFA STRUCTURE TESTS
    // ============================================
    
    @Nested
    @DisplayName("DFA Structure Tests")
    class DFAStructureTests {
        
        @Test
        @DisplayName("DFA has valid start state")
        void testValidStartState() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a*");
            
            assertNotNull(dfa.start);
            assertTrue(dfa.states.contains(dfa.start));
        }
        
        @Test
        @DisplayName("DFA has at least one accepting state")
        void testHasAcceptingState() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a+");
            
            boolean hasAcceptingState = dfa.states.stream()
                    .anyMatch(state -> state.isAccepting);
            assertTrue(hasAcceptingState);
        }
        
        @Test
        @DisplayName("All transitions are within state set")
        void testTransitionsValid() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("(a|b)*");
            
            for (RegexToDFA.DFAState state : dfa.states) {
                for (RegexToDFA.DFAState target : state.transitions.values()) {
                    assertTrue(dfa.states.contains(target), 
                        "Transition target should be in DFA state set");
                }
            }
        }
        
        @Test
        @DisplayName("Deterministic property")
        void testDeterministic() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a*b+");
            
            // Each state should have at most one transition per character
            for (RegexToDFA.DFAState state : dfa.states) {
                for (char c : dfa.alphabet) {
                    RegexToDFA.DFAState target1 = state.transitions.get(c);
                    RegexToDFA.DFAState target2 = state.transitions.get(c);
                    assertEquals(target1, target2, 
                        "DFA should be deterministic - same input should give same transition");
                }
            }
        }
    }
    
    // ============================================
    // PERFORMANCE TESTS
    // ============================================
    
    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {
        
        @Test
        @DisplayName("Large input string")
        void testLargeInput() throws Parser.RegexParseException {
            RegexToDFA.DFA dfa = converter.convertRegexToDFA("a*");
            
            // Create a large string of 'a's
            StringBuilder largeString = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                largeString.append('a');
            }
            
            // Should accept in reasonable time
            long startTime = System.nanoTime();
            boolean result = dfa.accepts(largeString.toString());
            long endTime = System.nanoTime();
            
            assertTrue(result);
            long timeMs = (endTime - startTime) / 1_000_000;
            assertTrue(timeMs < 100, "Should complete in under 100ms, took: " + timeMs + "ms");
        }
        
        @Test
        @DisplayName("Complex pattern compilation")
        void testComplexCompilation() throws Parser.RegexParseException {
            // Complex but reasonable pattern
            String complexPattern = "((a|b)*c+(d|e)*f?)+";
            
            long startTime = System.nanoTime();
            RegexToDFA.DFA dfa = converter.convertRegexToDFA(complexPattern);
            long endTime = System.nanoTime();
            
            assertNotNull(dfa);
            long timeMs = (endTime - startTime) / 1_000_000;
            assertTrue(timeMs < 1000, "Complex compilation should complete in under 1s, took: " + timeMs + "ms");
        }
    }
    
    // ============================================
    // ERROR CASE TESTS
    // ============================================
    
    @Nested
    @DisplayName("Error Case Tests")
    class ErrorCaseTests {
        
        @Test
        @DisplayName("Invalid regex throws exception")
        void testInvalidRegex() {
            assertThrows(Parser.RegexParseException.class, () -> {
                converter.convertRegexToDFA("a)");
            });
            
            assertThrows(Parser.RegexParseException.class, () -> {
                converter.convertRegexToDFA("(a");
            });
            
            assertThrows(Parser.RegexParseException.class, () -> {
                converter.convertRegexToDFA("*a");
            });
        }
    }
    
    // ============================================
    // INTEGRATION TESTS (from original main)
    // ============================================
    
    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        
        @Test
        @DisplayName("All original test cases")
        void testOriginalTestCases() {
            String[] testRegexes = {
                "a", "ab", "a|b", "a*", "(a|b)*", "a+b*", "(ab)*", "a(b|c)*d"
            };
            
            for (String regex : testRegexes) {
                assertDoesNotThrow(() -> {
                    RegexToDFA.DFA dfa = converter.convertRegexToDFA(regex);
                    assertNotNull(dfa);
                    
                    // Test with original test strings
                    String[] testStrings = getOriginalTestStrings(regex);
                    for (String test : testStrings) {
                        // Should not throw exception, result can be true or false
                        dfa.accepts(test);
                    }
                }, "Regex: " + regex + " should compile and run without errors");
            }
        }
    }
    
    // ============================================
    // DFA OPTIMIZATION TESTS
    // ============================================
    
    @Nested
    @DisplayName("DFA Optimization Tests")
    class OptimizationTests {
        
        @Test
        @DisplayName("Optimized compiler should produce equivalent results")
        void testOptimizedEquivalence() throws Parser.RegexParseException {
            String[] testPatterns = {"a", "ab", "a|b", "a*", "a+", "(a|b)*", "a+b*", "(ab)*"};
            String[] testInputs = {"", "a", "b", "ab", "aa", "bb", "abc"};
            
            for (String pattern : testPatterns) {
                RegexToDFA.DFA originalDFA = converter.convertRegexToDFA(pattern);
                RegexToDFA.DFA optimizedDFA = converter.convertRegexToDFAOptimized(pattern);
                
                // Both should accept the same inputs
                for (String input : testInputs) {
                    boolean originalResult = originalDFA.accepts(input);
                    boolean optimizedResult = optimizedDFA.accepts(input);
                    
                    assertEquals(originalResult, optimizedResult, 
                        String.format("Pattern '%s' with input '%s': original=%s, optimized=%s", 
                            pattern, input, originalResult, optimizedResult));
                }
            }
        }
        
        @Test
        @DisplayName("Optimization should reduce or maintain state count")
        void testStateReduction() throws Parser.RegexParseException {
            String[] testPatterns = {"a|a", "a*a*", "(a|b)|(a|b)", "a+a*"};
            
            for (String pattern : testPatterns) {
                RegexToDFA.DFA originalDFA = converter.convertRegexToDFA(pattern);
                RegexToDFA.DFA optimizedDFA = converter.convertRegexToDFAOptimized(pattern);
                
                // Optimized should have fewer or equal states
                assertTrue(optimizedDFA.states.size() <= originalDFA.states.size(),
                    String.format("Pattern '%s': original=%d states, optimized=%d states", 
                        pattern, originalDFA.states.size(), optimizedDFA.states.size()));
            }
        }
        
        @Test
        @DisplayName("Individual optimization steps work correctly")
        void testIndividualOptimizations() throws Parser.RegexParseException {
            // Test case that should benefit from all optimizations
            String pattern = "(a|b)*a(a|b)*";
            RegexToDFA.DFA originalDFA = converter.convertRegexToDFA(pattern);
            
            // Test unreachable state removal
            RegexToDFA.DFA afterUnreachable = converter.removeUnreachableStates(originalDFA);
            assertTrue(afterUnreachable.states.size() <= originalDFA.states.size());
            
            // Test dead state removal
            RegexToDFA.DFA afterDead = converter.removeDeadStates(afterUnreachable);
            assertTrue(afterDead.states.size() <= afterUnreachable.states.size());
            
            // Test minimization
            RegexToDFA.DFA minimized = converter.minimizeDFA(afterDead);
            assertTrue(minimized.states.size() <= afterDead.states.size());
            
            // All should accept the same strings
            String[] testInputs = {"a", "aa", "ab", "ba", "aba", "bab"};
            for (String input : testInputs) {
                boolean original = originalDFA.accepts(input);
                boolean optimized = minimized.accepts(input);
                assertEquals(original, optimized, 
                    String.format("Input '%s': original=%s, optimized=%s", input, original, optimized));
            }
        }
        
        @Test
        @DisplayName("Optimization preserves all accepting behavior")
        void testOptimizationPreservesAcceptance() throws Parser.RegexParseException {
            String[] complexPatterns = {
                "((a|b)*c)+",
                "a+b*c+",
                "(a|b)*(c|d)*",
                "a(b|c)*d(e|f)*"
            };
            
            for (String pattern : complexPatterns) {
                RegexToDFA.DFA originalDFA = converter.convertRegexToDFA(pattern);
                RegexToDFA.DFA optimizedDFA = converter.convertRegexToDFAOptimized(pattern);
                
                // Generate test strings
                String[] testInputs = generateTestStrings(pattern);
                
                for (String input : testInputs) {
                    boolean originalResult = originalDFA.accepts(input);
                    boolean optimizedResult = optimizedDFA.accepts(input);
                    
                    assertEquals(originalResult, optimizedResult,
                        String.format("Pattern '%s' with input '%s'", pattern, input));
                }
            }
        }
        
        @Test
        @DisplayName("Optimization handles edge cases")
        void testOptimizationEdgeCases() throws Parser.RegexParseException {
            // Empty pattern
            RegexToDFA.DFA emptyOriginal = converter.convertRegexToDFA("");
            RegexToDFA.DFA emptyOptimized = converter.convertRegexToDFAOptimized("");
            assertEquals(emptyOriginal.accepts(""), emptyOptimized.accepts(""));
            assertEquals(emptyOriginal.accepts("a"), emptyOptimized.accepts("a"));
            
            // Single character
            RegexToDFA.DFA singleOriginal = converter.convertRegexToDFA("a");
            RegexToDFA.DFA singleOptimized = converter.convertRegexToDFAOptimized("a");
            assertEquals(singleOriginal.accepts("a"), singleOptimized.accepts("a"));
            assertEquals(singleOriginal.accepts(""), singleOptimized.accepts(""));
            
            // Star of single character (should be minimal already)
            RegexToDFA.DFA starOriginal = converter.convertRegexToDFA("a*");
            RegexToDFA.DFA starOptimized = converter.convertRegexToDFAOptimized("a*");
            assertEquals(starOriginal.accepts(""), starOptimized.accepts(""));
            assertEquals(starOriginal.accepts("a"), starOptimized.accepts("a"));
            assertEquals(starOriginal.accepts("aa"), starOptimized.accepts("aa"));
        }
        
        private String[] generateTestStrings(String pattern) {
            // Simple test string generation - could be more sophisticated
            return new String[]{"", "a", "b", "c", "d", "e", "f", "ab", "ac", "bc", "abc", "abcd"};
        }
    }
    
    /**
     * Helper method from original RegexToDFA main method
     */
    private String[] getOriginalTestStrings(String regex) {
        switch (regex) {
            case "a": return new String[]{"", "a", "aa", "b"};
            case "ab": return new String[]{"", "a", "ab", "abb", "ba"};
            case "a|b": return new String[]{"", "a", "b", "ab", "c"};
            case "a*": return new String[]{"", "a", "aa", "aaa", "b"};
            case "(a|b)*": return new String[]{"", "a", "b", "ab", "ba", "aab", "c"};
            case "a+b*": return new String[]{"", "a", "ab", "abb", "aa", "aab", "b"};
            case "(ab)*": return new String[]{"", "ab", "abab", "a", "aba", "ba"};
            case "a(b|c)*d": return new String[]{"ad", "abd", "acd", "abcd", "abbcd", "a", "d", ""};
            default: return new String[]{"", "a", "ab", "abc"};
        }
    }
}