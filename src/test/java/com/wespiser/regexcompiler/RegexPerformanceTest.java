package com.wespiser.regexcompiler;

import java.util.*;

/**
 * Performance testing suite for different regex implementations.
 * 
 * Place this file in: src/test/java/com/wespiser/regexcompiler/RegexPerformanceTest.java
 * 
 * Run with:
 *   mvn test-compile
 *   mvn exec:java -Dexec.mainClass="com.wespiser.regexcompiler.RegexPerformanceTest" -Dexec.classpathScope="test"
 * 
 * Or directly with javac/java:
 *   javac -cp target/classes src/test/java/com/wespiser/regexcompiler/RegexPerformanceTest.java
 *   java -cp target/classes:src/test/java com.wespiser.regexcompiler.RegexPerformanceTest
 */
public class RegexPerformanceTest {
    
    // ============================================
    // TEST CONFIGURATION
    // ============================================
    
    private static final int WARMUP_ITERATIONS = 1000;
    private static final int BENCHMARK_ITERATIONS = 10000;
    private static final int COMPILE_ITERATIONS = 100;
    
    // Test patterns of varying complexity
    private static final TestCase[] TEST_CASES = {
        // Simple patterns
        new TestCase("a", new String[]{"a", "b", "aa", ""}, "Simple character"),
        new TestCase("ab", new String[]{"ab", "a", "b", "abc", ""}, "Simple concatenation"),
        new TestCase("a|b", new String[]{"a", "b", "c", "ab", ""}, "Simple alternation"),
        
        // Quantifier patterns
        new TestCase("a*", new String[]{"", "a", "aaa", "b"}, "Kleene star"),
        new TestCase("a+", new String[]{"a", "aa", "aaa", "", "b"}, "Plus quantifier"),
        new TestCase("a?", new String[]{"", "a", "aa", "b"}, "Optional"),
        
        // Complex patterns
        new TestCase("(a|b)*", new String[]{"", "a", "ab", "abab", "c"}, "Alternation with star"),
        new TestCase("a+b*", new String[]{"a", "ab", "abb", "aa", "b", ""}, "Multiple quantifiers"),
        new TestCase("(ab)+", new String[]{"ab", "abab", "ababab", "a", "b", ""}, "Grouped plus"),
        new TestCase("a(b|c)*d", new String[]{"ad", "abd", "acd", "abcd", "abbccd", "a", "d"}, "Complex pattern"),
        
        // Stress test patterns
        new TestCase("(a|b|c)*", generateTestStrings("abc", 5), "Large alternation"),
        new TestCase("a*b*c*", generateTestStrings("abc", 4), "Multiple stars"),
        new TestCase("((a|b)*c)+", generateComplexStrings(), "Nested quantifiers")
    };
    
    // ============================================
    // BENCHMARK FRAMEWORK
    // ============================================
    
    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("                    REGEX PERFORMANCE BENCHMARK");
        System.out.println("=".repeat(80));
        System.out.println();
        
        System.out.printf("Warmup iterations: %d%n", WARMUP_ITERATIONS);
        System.out.printf("Benchmark iterations: %d%n", BENCHMARK_ITERATIONS);
        System.out.printf("Compile iterations: %d%n", COMPILE_ITERATIONS);
        System.out.println();
        
        // Run all test cases
        for (TestCase testCase : TEST_CASES) {
            runTestCase(testCase);
        }
        
        // Summary comparison
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                         SUMMARY");
        System.out.println("=".repeat(80));
        runSummaryBenchmark();
    }
    
    private static void runTestCase(TestCase testCase) {
        System.out.println("Testing: " + testCase.description + " - Pattern: \"" + testCase.pattern + "\"");
        System.out.println("-".repeat(80));
        
        try {
            // Compile patterns once for matching tests
            Pattern dfaPattern = Pattern.compileDFA(testCase.pattern);
            Pattern backtrackPattern = Pattern.compileBacktrack(testCase.pattern);
            Pattern tablePattern = Pattern.compileTable(testCase.pattern);
            
            // Test compilation performance
            System.out.println("COMPILATION PERFORMANCE:");
            benchmarkCompilation("DFA", () -> {
                try { return Pattern.compileDFA(testCase.pattern); }
                catch (RegexCompileException e) { throw new RuntimeException(e); }
            });
            
            benchmarkCompilation("Backtrack", () -> {
                try { return Pattern.compileBacktrack(testCase.pattern); }
                catch (RegexCompileException e) { throw new RuntimeException(e); }
            });
            
            benchmarkCompilation("Table", () -> {
                try { return Pattern.compileTable(testCase.pattern); }
                catch (RegexCompileException e) { throw new RuntimeException(e); }
            });
            
            System.out.println();
            
            // Test matching performance for each input string
            System.out.println("MATCHING PERFORMANCE:");
            for (String input : testCase.inputs) {
                System.out.printf("Input: \"%s\"%n", input);
                
                // Verify all implementations give same result
                boolean dfaResult = dfaPattern.matches(input);
                boolean backtrackResult = backtrackPattern.matches(input);
                boolean tableResult = tablePattern.matches(input);
                
                if (dfaResult != backtrackResult || dfaResult != tableResult) {
                    System.err.printf("  WARNING: Inconsistent results! DFA: %s, Backtrack: %s, Table: %s%n",
                                    dfaResult, backtrackResult, tableResult);
                }
                
                benchmarkMatching("  DFA", dfaPattern, input);
                benchmarkMatching("  Backtrack", backtrackPattern, input);
                benchmarkMatching("  Table", tablePattern, input);
                System.out.println();
            }
            
        } catch (RegexCompileException e) {
            System.err.println("Failed to compile pattern: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    private static void benchmarkCompilation(String name, PatternSupplier supplier) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS / 10; i++) {
            supplier.get();
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < COMPILE_ITERATIONS; i++) {
            supplier.get();
        }
        long endTime = System.nanoTime();
        
        double avgTimeMs = (endTime - startTime) / (double) COMPILE_ITERATIONS / 1_000_000.0;
        System.out.printf("  %-12s: %8.3f ms/compile%n", name, avgTimeMs);
    }
    
    private static void benchmarkMatching(String name, Pattern pattern, String input) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            pattern.matches(input);
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            pattern.matches(input);
        }
        long endTime = System.nanoTime();
        
        double avgTimeNs = (endTime - startTime) / (double) BENCHMARK_ITERATIONS;
        System.out.printf("    %-12s: %8.1f ns/match%n", name, avgTimeNs);
    }
    
    private static void runSummaryBenchmark() {
        // Overall performance comparison with mixed workload
        String[] summaryPatterns = {"a*", "(a|b)*", "a+b*", "(ab)+c?"};
        String[] summaryInputs = {"", "a", "ab", "aabb", "abcabc"};
        
        System.out.println("Mixed workload performance (average across all patterns/inputs):");
        System.out.println();
        
        Map<String, Long> totalTimes = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        
        for (String pattern : summaryPatterns) {
            try {
                Pattern dfaPattern = Pattern.compileDFA(pattern);
                Pattern backtrackPattern = Pattern.compileBacktrack(pattern);
                Pattern tablePattern = Pattern.compileTable(pattern);
                
                for (String input : summaryInputs) {
                    // Benchmark each implementation
                    long dfaTime = benchmarkSingle(dfaPattern, input);
                    long backtrackTime = benchmarkSingle(backtrackPattern, input);
                    long tableTime = benchmarkSingle(tablePattern, input);
                    
                    totalTimes.merge("DFA", dfaTime, Long::sum);
                    totalTimes.merge("Backtrack", backtrackTime, Long::sum);
                    totalTimes.merge("Table", tableTime, Long::sum);
                    
                    counts.merge("DFA", 1, Integer::sum);
                    counts.merge("Backtrack", 1, Integer::sum);
                    counts.merge("Table", 1, Integer::sum);
                }
            } catch (RegexCompileException e) {
                System.err.println("Failed summary test for pattern: " + pattern);
            }
        }
        
        // Print average results
        for (String impl : Arrays.asList("DFA", "Backtrack", "Table")) {
            long totalTime = totalTimes.getOrDefault(impl, 0L);
            int count = counts.getOrDefault(impl, 1);
            double avgTime = totalTime / (double) count;
            
            System.out.printf("%-12s: %8.1f ns/match (average)%n", impl, avgTime);
        }
        
        System.out.println();
        System.out.println("Performance ranking (lower is better):");
        totalTimes.entrySet().stream()
                 .sorted(Map.Entry.comparingByValue())
                 .forEach(entry -> {
                     double avgTime = entry.getValue() / (double) counts.get(entry.getKey());
                     System.out.printf("  %s: %.1f ns/match%n", entry.getKey(), avgTime);
                 });
    }
    
    private static long benchmarkSingle(Pattern pattern, String input) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            pattern.matches(input);
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            pattern.matches(input);
        }
        long endTime = System.nanoTime();
        
        return (endTime - startTime) / BENCHMARK_ITERATIONS;
    }
    
    // ============================================
    // TEST DATA GENERATION
    // ============================================
    
    private static String[] generateTestStrings(String alphabet, int maxLength) {
        List<String> strings = new ArrayList<>();
        strings.add(""); // Empty string
        
        // Generate strings of various lengths
        for (int len = 1; len <= maxLength; len++) {
            generateStringsOfLength(alphabet, len, "", strings);
            if (strings.size() > 20) break; // Limit test cases
        }
        
        return strings.toArray(new String[0]);
    }
    
    private static void generateStringsOfLength(String alphabet, int length, String current, List<String> result) {
        if (current.length() == length) {
            result.add(current);
            return;
        }
        
        for (char c : alphabet.toCharArray()) {
            generateStringsOfLength(alphabet, length, current + c, result);
            if (result.size() > 50) return; // Limit combinations
        }
    }
    
    private static String[] generateComplexStrings() {
        return new String[]{
            "", "c", "ac", "bc", "cc", "acc", "bcc", "abcc", "aaccbcc", "ccccc"
        };
    }
    
    // ============================================
    // HELPER CLASSES
    // ============================================
    
    private static class TestCase {
        final String pattern;
        final String[] inputs;
        final String description;
        
        TestCase(String pattern, String[] inputs, String description) {
            this.pattern = pattern;
            this.inputs = inputs;
            this.description = description;
        }
    }
    
    @FunctionalInterface
    private interface PatternSupplier {
        Pattern get();
    }
}