package com.wespiser.regexcompiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Command-line driver for the regex compiler
 * 
 * Usage: java Driver <algorithm> <input-mode> <pattern> <text>
 * 
 * Arguments:
 *   algorithm   - Algorithm to use: "DFA", "Backtrack", or "Table"
 *   input-mode  - Input mode: "string" for literals, "file" to read from files
 *   pattern     - Regex pattern (string literal or file path)
 *   text        - Text to search (string literal or file path)
 * 
 * Examples:
 *   java Driver DFA string "a+" "aaabbb"
 *   java Driver Table file pattern.txt input.txt
 */
public class Driver {
    
    public static void main(String[] args) {
        if (args.length != 4) {
            printUsage();
            System.exit(1);
        }
        
        String algorithm = args[0];
        String inputMode = args[1];
        String patternArg = args[2];
        String textArg = args[3];
        
        try {
            // Validate algorithm
            if (!isValidAlgorithm(algorithm)) {
                System.err.println("Error: Invalid algorithm '" + algorithm + "'");
                System.err.println("Valid algorithms: DFA, Backtrack, Table");
                System.exit(1);
            }
            
            // Validate input mode
            if (!inputMode.equals("string") && !inputMode.equals("file")) {
                System.err.println("Error: Invalid input mode '" + inputMode + "'");
                System.err.println("Valid input modes: string, file");
                System.exit(1);
            }
            
            // Get pattern and text based on input mode
            String pattern;
            String text;
            
            if (inputMode.equals("string")) {
                pattern = patternArg;
                text = textArg;
            } else {
                // Read from files
                pattern = readFile(patternArg);
                text = readFile(textArg);
            }
            
            // Run the regex matching
            runRegexMatching(algorithm, pattern, text);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Print usage information
     */
    private static void printUsage() {
        System.out.println("Regex Compiler Driver");
        System.out.println("====================");
        System.out.println();
        System.out.println("Usage: java Driver <algorithm> <input-mode> <pattern> <text>");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  algorithm   - Algorithm to use: DFA, Backtrack, or Table");
        System.out.println("  input-mode  - Input mode: string (literals) or file (read from files)");
        System.out.println("  pattern     - Regex pattern (string literal or file path)");
        System.out.println("  text        - Text to search (string literal or file path)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java Driver DFA string \"a+\" \"aaabbb\"");
        System.out.println("  java Driver Table file pattern.txt input.txt");
        System.out.println("  java Driver Backtrack string \"(a|b)*\" \"abababab\"");
    }
    
    /**
     * Check if the algorithm name is valid
     */
    private static boolean isValidAlgorithm(String algorithm) {
        return algorithm.equals("DFA") || 
               algorithm.equals("Backtrack") || 
               algorithm.equals("Table");
    }
    
    /**
     * Read content from a file
     */
    private static String readFile(String filePath) throws IOException {
        try {
            return Files.readString(Paths.get(filePath)).trim();
        } catch (IOException e) {
            throw new IOException("Failed to read file '" + filePath + "': " + e.getMessage());
        }
    }
    
    /**
     * Run regex matching with the specified algorithm
     */
    private static void runRegexMatching(String algorithm, String pattern, String text) 
            throws RegexCompileException {
        
        System.out.println("Regex Compiler - " + algorithm + " Algorithm");
        System.out.println("=".repeat(50));
        System.out.println("Pattern: " + pattern);
        System.out.println("Text: " + (text.length() > 100 ? text.substring(0, 100) + "..." : text));
        System.out.println("Text length: " + text.length() + " characters");
        System.out.println();
        
        // Compile the pattern
        long compileStart = System.nanoTime();
        Pattern compiledPattern;
        
        switch (algorithm) {
            case "DFA":
                compiledPattern = Pattern.compileDFA(pattern);
                break;
            case "Backtrack":
                compiledPattern = Pattern.compileBacktrack(pattern);
                break;
            case "Table":
                compiledPattern = Pattern.compileTable(pattern);
                break;
            default:
                throw new IllegalArgumentException("Invalid algorithm: " + algorithm);
        }
        
        long compileEnd = System.nanoTime();
        double compileTimeMs = (compileEnd - compileStart) / 1_000_000.0;
        
        // Perform matching
        long matchStart = System.nanoTime();
        boolean matches = compiledPattern.matches(text);
        long matchEnd = System.nanoTime();
        double matchTimeNs = matchEnd - matchStart;
        
        // Perform find operation
        long findStart = System.nanoTime();
        boolean finds = compiledPattern.find(text);
        long findEnd = System.nanoTime();
        double findTimeNs = findEnd - findStart;
        
        // Display results
        System.out.println("Results:");
        System.out.println("--------");
        System.out.println("Exact match (matches): " + matches);
        System.out.println("Substring found (find): " + finds);
        System.out.println();
        
        System.out.println("Performance:");
        System.out.println("------------");
        System.out.printf("Compilation time: %.3f ms%n", compileTimeMs);
        System.out.printf("Match time: %.1f ns%n", matchTimeNs);
        System.out.printf("Find time: %.1f ns%n", findTimeNs);
        System.out.println();
        
        // Display compilation statistics
        Map<String, Object> stats = compiledPattern.getCompilationStats();
        if (!stats.isEmpty()) {
            System.out.println("Compilation Statistics:");
            System.out.println("-----------------------");
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                System.out.printf("%-20s: %s%n", entry.getKey(), entry.getValue());
            }
        }
    }
}