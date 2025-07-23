package com.example.regexcompiler;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive benchmarking suite for regex implementations
 */
public class RegexBenchmark {
    
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final int WARMUP_ITERATIONS = 10;
    private static final int BENCHMARK_ITERATIONS = 100;
    
    /**
     * Benchmark result data structure
     */
    public static class BenchmarkResult {
        public String algorithm;
        public String dataSize;
        public String pattern;
        public String inputType; // "matching" or "nonmatching"
        public String input;
        public long compileTimeNs;
        public long matchTimeNs;
        public long findTimeNs;
        public long memoryUsedBytes;
        public int patternLength;
        public int inputLength;
        public boolean matchResult;
        public boolean findResult;
        public String errorMessage;
        
        public String toCsvRow() {
            return String.format("%s,%s,%d,%s,%d,%s,%s,%d,%d,%d,%d,%s,%s,%s",
                algorithm, dataSize, patternLength, inputType, inputLength,
                errorMessage != null ? "ERROR" : "SUCCESS",
                errorMessage != null ? errorMessage.replace(",", ";") : "",
                compileTimeNs, matchTimeNs, findTimeNs, memoryUsedBytes,
                matchResult, findResult,
                pattern.replace(",", ";").replace("\"", "\"\""));
        }
        
        public static String getCsvHeader() {
            return "algorithm,dataSize,patternLength,inputType,inputLength,status,errorMessage," +
                   "compileTimeNs,matchTimeNs,findTimeNs,memoryUsedBytes,matchResult,findResult,pattern";
        }
    }
    
    public static void main(String[] args) {
        try {
            System.out.println("Starting comprehensive regex benchmark...");
            runComprehensiveBenchmark();
            System.out.println("Benchmark complete! Results saved to benchmark_results.csv");
        } catch (IOException e) {
            System.err.println("Error running benchmark: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Run comprehensive benchmark across all algorithms and data sizes
     */
    public static void runComprehensiveBenchmark() throws IOException {
        List<BenchmarkResult> results = new ArrayList<>();
        
        String[] algorithms = {"DFA", "Backtrack", "Table"};
        String[] dataSizes = {"10", "50", "100", "500", "1000"};
        
        int totalTests = 0;
        int completedTests = 0;
        
        // Count total tests
        for (String size : dataSizes) {
            Path patternsFile = Paths.get("src/resources/data/size_" + size + "_patterns.txt");
            if (Files.exists(patternsFile)) {
                List<String> patterns = Files.readAllLines(patternsFile);
                totalTests += algorithms.length * patterns.size() * 2; // 2 for matching/nonmatching
            }
        }
        
        System.out.println("Total tests to run: " + totalTests);
        
        for (String algorithm : algorithms) {
            for (String size : dataSizes) {
                System.out.printf("Running %s algorithm with size %s data...%n", algorithm, size);
                
                try {
                    List<BenchmarkResult> sizeResults = benchmarkAlgorithmForSize(algorithm, size);
                    results.addAll(sizeResults);
                    completedTests += sizeResults.size();
                    
                    System.out.printf("  Completed %d/%d tests (%.1f%%)%n", 
                        completedTests, totalTests, (completedTests * 100.0) / totalTests);
                    
                } catch (Exception e) {
                    System.err.printf("  Error with %s algorithm, size %s: %s%n", 
                        algorithm, size, e.getMessage());
                }
            }
        }
        
        // Write results to CSV
        saveToCsv(results, "benchmark_results.csv");
        
        // Print summary statistics
        printSummaryStats(results);
    }
    
    /**
     * Benchmark a specific algorithm for a specific data size
     */
    private static List<BenchmarkResult> benchmarkAlgorithmForSize(String algorithm, String size) 
            throws IOException {
        
        List<BenchmarkResult> results = new ArrayList<>();
        
        // Load test data
        Path patternsFile = Paths.get("src/resources/data/size_" + size + "_patterns.txt");
        Path matchingFile = Paths.get("src/resources/data/size_" + size + "_matching.txt");
        Path nonMatchingFile = Paths.get("src/resources/data/size_" + size + "_nonmatching.txt");
        
        if (!Files.exists(patternsFile)) {
            throw new IOException("Patterns file not found: " + patternsFile);
        }
        
        List<String> patterns = Files.readAllLines(patternsFile);
        List<String> matchingStrings = Files.readAllLines(matchingFile);
        List<String> nonMatchingStrings = Files.readAllLines(nonMatchingFile);
        
        // Benchmark each pattern
        for (int i = 0; i < patterns.size(); i++) {
            String pattern = patterns.get(i);
            
            // Test with matching string
            if (i < matchingStrings.size()) {
                String matchingString = matchingStrings.get(i);
                BenchmarkResult result = benchmarkSingleCase(algorithm, size, pattern, 
                    matchingString, "matching");
                results.add(result);
            }
            
            // Test with non-matching string
            if (i < nonMatchingStrings.size()) {
                String nonMatchingString = nonMatchingStrings.get(i);
                BenchmarkResult result = benchmarkSingleCase(algorithm, size, pattern, 
                    nonMatchingString, "nonmatching");
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * Benchmark a single test case
     */
    private static BenchmarkResult benchmarkSingleCase(String algorithm, String size, 
            String pattern, String input, String inputType) {
        
        BenchmarkResult result = new BenchmarkResult();
        result.algorithm = algorithm;
        result.dataSize = size;
        result.pattern = pattern;
        result.input = input;
        result.inputType = inputType;
        result.patternLength = pattern.length();
        result.inputLength = input.length();
        
        try {
            // Force garbage collection before measurement
            System.gc();
            System.gc();
            Thread.sleep(10); // Give GC time to complete
            
            // Compile pattern
            long compileStart = System.nanoTime();
            Pattern compiledPattern = compilePattern(algorithm, pattern);
            long compileEnd = System.nanoTime();
            result.compileTimeNs = compileEnd - compileStart;
            
            // Measure memory footprint of this pattern compilation
            result.memoryUsedBytes = measurePatternMemory(algorithm, pattern);
            
            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                compiledPattern.matches(input);
                compiledPattern.find(input);
            }
            
            // Benchmark matches()
            long matchStart = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                result.matchResult = compiledPattern.matches(input);
            }
            long matchEnd = System.nanoTime();
            result.matchTimeNs = (matchEnd - matchStart) / BENCHMARK_ITERATIONS;
            
            // Benchmark find()
            long findStart = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                result.findResult = compiledPattern.find(input);
            }
            long findEnd = System.nanoTime();
            result.findTimeNs = (findEnd - findStart) / BENCHMARK_ITERATIONS;
            
        } catch (Exception e) {
            result.errorMessage = e.getMessage();
            result.compileTimeNs = -1;
            result.matchTimeNs = -1;
            result.findTimeNs = -1;
            result.memoryUsedBytes = -1;
        }
        
        return result;
    }
    
    /**
     * Compile pattern using specified algorithm
     */
    private static Pattern compilePattern(String algorithm, String pattern) 
            throws RegexCompileException {
        switch (algorithm) {
            case "DFA":
                return Pattern.compileDFA(pattern);
            case "Backtrack":
                return Pattern.compileBacktrack(pattern);
            case "Table":
                return Pattern.compileTable(pattern);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
    
    /**
     * Get current used memory in bytes with more accurate measurement
     */
    private static long getUsedMemory() {
        // Force multiple GC cycles to get stable measurement
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(50); // Allow GC to complete
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getUsed();
    }
    
    /**
     * Measure memory footprint of pattern compilation more accurately
     */
    private static long measurePatternMemory(String algorithm, String patternString) {
        final int INSTANCES = 20; // Further reduced for better performance while maintaining accuracy
        
        // Get stable baseline
        long baseline = getUsedMemory();
        
        // Create array to hold references and prevent GC
        Pattern[] patterns = new Pattern[INSTANCES];
        
        try {
            // Compile multiple instances of the same pattern
            for (int i = 0; i < INSTANCES; i++) {
                patterns[i] = compilePattern(algorithm, patternString);
            }
            
            // Force GC to ensure all temporary objects are cleaned up
            System.gc();
            Thread.sleep(50);
            
            long afterAllocation = getUsedMemory();
            long totalMemory = Math.max(0, afterAllocation - baseline);
            long memoryPerInstance = totalMemory / INSTANCES;
            
            return memoryPerInstance;
            
        } catch (Exception e) {
            return -1; // Error in measurement
        } finally {
            // Clear references to allow GC
            for (int i = 0; i < patterns.length; i++) {
                patterns[i] = null;
            }
            System.gc();
        }
    }
    
    /**
     * Save results to CSV file
     */
    private static void saveToCsv(List<BenchmarkResult> results, String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(filename)) {
            writer.println(BenchmarkResult.getCsvHeader());
            for (BenchmarkResult result : results) {
                writer.println(result.toCsvRow());
            }
        }
    }
    
    /**
     * Print summary statistics
     */
    private static void printSummaryStats(List<BenchmarkResult> results) {
        System.out.println("\nBenchmark Summary:");
        System.out.println("==================");
        
        int totalTests = results.size();
        int errorCount = (int) results.stream().mapToInt(r -> r.errorMessage != null ? 1 : 0).sum();
        int successCount = totalTests - errorCount;
        
        System.out.printf("Total tests: %d%n", totalTests);
        System.out.printf("Successful: %d (%.1f%%)%n", successCount, (successCount * 100.0) / totalTests);
        System.out.printf("Errors: %d (%.1f%%)%n", errorCount, (errorCount * 100.0) / totalTests);
        
        if (successCount > 0) {
            // Calculate average times by algorithm
            String[] algorithms = {"DFA", "Backtrack", "Table"};
            
            System.out.println("\nAverage Performance by Algorithm:");
            System.out.println("---------------------------------");
            System.out.printf("%-12s %-15s %-15s %-15s %-15s%n", 
                "Algorithm", "Compile (ns)", "Match (ns)", "Find (ns)", "Memory (bytes)");
            
            for (String algorithm : algorithms) {
                List<BenchmarkResult> algoResults = results.stream()
                    .filter(r -> r.algorithm.equals(algorithm) && r.errorMessage == null)
                    .collect(java.util.stream.Collectors.toList());
                
                if (!algoResults.isEmpty()) {
                    double avgCompile = algoResults.stream().mapToLong(r -> r.compileTimeNs).average().orElse(0);
                    double avgMatch = algoResults.stream().mapToLong(r -> r.matchTimeNs).average().orElse(0);
                    double avgFind = algoResults.stream().mapToLong(r -> r.findTimeNs).average().orElse(0);
                    double avgMemory = algoResults.stream().mapToLong(r -> r.memoryUsedBytes).average().orElse(0);
                    
                    System.out.printf("%-12s %-15.0f %-15.0f %-15.0f %-15.0f%n", 
                        algorithm, avgCompile, avgMatch, avgFind, avgMemory);
                }
            }
        }
        
        System.out.println("\nResults saved to: benchmark_results.csv");
    }
}