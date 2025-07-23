package com.example.regexcompiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generates test data for benchmarking regex implementations
 */
public class BenchmarkDataGenerator {
    
    private static final Random random = new Random(42); // Fixed seed for reproducibility
    
    // Basic regex components for generating patterns
    private static final String[] CHARS = {"a", "b", "c", "d", "e"};
    private static final String[] QUANTIFIERS = {"", "*", "+", "?"};
    private static final String[] OPERATORS = {"|", ""};
    
    public static void main(String[] args) {
        try {
            generateAllTestData();
            System.out.println("Test data generation completed!");
        } catch (IOException e) {
            System.err.println("Error generating test data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generate all test data files
     */
    public static void generateAllTestData() throws IOException {
        Path dataDir = Paths.get("src/resources/data");
        Files.createDirectories(dataDir);
        
        // Generate data for different size categories
        int[] sizes = {10, 50, 100, 500, 1000};
        
        for (int size : sizes) {
            System.out.println("Generating test data for size: " + size);
            generateTestDataForSize(dataDir, size);
        }
    }
    
    /**
     * Generate test data for a specific size category
     */
    private static void generateTestDataForSize(Path dataDir, int size) throws IOException {
        String prefix = String.format("size_%d", size);
        
        // Generate patterns
        List<String> patterns = generatePatterns(size, 100); // 100 patterns per size
        Path patternsFile = dataDir.resolve(prefix + "_patterns.txt");
        Files.write(patternsFile, patterns);
        
        // Generate matching strings
        List<String> matchingStrings = generateMatchingStrings(patterns, size);
        Path matchingFile = dataDir.resolve(prefix + "_matching.txt");
        Files.write(matchingFile, matchingStrings);
        
        // Generate non-matching strings
        List<String> nonMatchingStrings = generateNonMatchingStrings(size, patterns.size());
        Path nonMatchingFile = dataDir.resolve(prefix + "_nonmatching.txt");
        Files.write(nonMatchingFile, nonMatchingStrings);
        
        System.out.printf("  Generated %d patterns, %d matching strings, %d non-matching strings%n",
                patterns.size(), matchingStrings.size(), nonMatchingStrings.size());
    }
    
    /**
     * Generate regex patterns of varying complexity
     */
    private static List<String> generatePatterns(int maxLength, int count) {
        List<String> patterns = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String pattern;
            if (maxLength <= 10) {
                // Simple patterns for small sizes
                pattern = generateSimplePattern(Math.min(maxLength, random.nextInt(5) + 1));
            } else {
                // More complex patterns for larger sizes
                pattern = generateComplexPattern(Math.min(maxLength, random.nextInt(maxLength/2) + 1));
            }
            patterns.add(pattern);
        }
        
        return patterns;
    }
    
    /**
     * Generate simple regex patterns
     */
    private static String generateSimplePattern(int length) {
        StringBuilder pattern = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            if (random.nextDouble() < 0.1 && pattern.length() > 0) {
                // Add quantifier to previous character
                String quantifier = QUANTIFIERS[random.nextInt(QUANTIFIERS.length)];
                if (!quantifier.isEmpty()) {
                    pattern.append(quantifier);
                }
            } else if (random.nextDouble() < 0.1 && pattern.length() > 0) {
                // Add alternation
                pattern.append("|").append(CHARS[random.nextInt(CHARS.length)]);
            } else {
                // Add regular character
                pattern.append(CHARS[random.nextInt(CHARS.length)]);
            }
        }
        
        return pattern.toString();
    }
    
    /**
     * Generate complex regex patterns with grouping
     */
    private static String generateComplexPattern(int targetLength) {
        StringBuilder pattern = new StringBuilder();
        int currentLength = 0;
        
        while (currentLength < targetLength) {
            double choice = random.nextDouble();
            
            if (choice < 0.2 && currentLength < targetLength - 4) {
                // Add grouped pattern
                pattern.append("(");
                String innerPattern = generateSimplePattern(Math.min(3, targetLength - currentLength - 2));
                pattern.append(innerPattern);
                pattern.append(")");
                
                // Maybe add quantifier to group
                if (random.nextDouble() < 0.5) {
                    String quantifier = QUANTIFIERS[1 + random.nextInt(QUANTIFIERS.length - 1)]; // Skip empty
                    pattern.append(quantifier);
                    currentLength += 1;
                }
                currentLength += innerPattern.length() + 2; // +2 for parentheses
                
            } else if (choice < 0.4 && currentLength > 0 && currentLength < targetLength - 1) {
                // Add alternation
                pattern.append("|");
                pattern.append(CHARS[random.nextInt(CHARS.length)]);
                currentLength += 2;
                
            } else {
                // Add character with possible quantifier
                pattern.append(CHARS[random.nextInt(CHARS.length)]);
                currentLength += 1;
                
                if (random.nextDouble() < 0.3 && currentLength < targetLength) {
                    String quantifier = QUANTIFIERS[random.nextInt(QUANTIFIERS.length)];
                    if (!quantifier.isEmpty()) {
                        pattern.append(quantifier);
                        currentLength += 1;
                    }
                }
            }
        }
        
        return pattern.toString();
    }
    
    /**
     * Generate strings that should match the given patterns
     */
    private static List<String> generateMatchingStrings(List<String> patterns, int maxLength) {
        List<String> matchingStrings = new ArrayList<>();
        
        for (String pattern : patterns) {
            // Generate multiple matching strings per pattern
            for (int i = 0; i < 3; i++) {
                String matchingString = generateMatchingString(pattern, maxLength);
                if (matchingString != null) {
                    matchingStrings.add(matchingString);
                }
            }
        }
        
        return matchingStrings;
    }
    
    /**
     * Generate a string that matches the given pattern (simplified approach)
     */
    private static String generateMatchingString(String pattern, int maxLength) {
        // This is a simplified matching string generator
        // For production use, you'd want a more sophisticated approach
        
        StringBuilder result = new StringBuilder();
        int targetLength = Math.min(maxLength, random.nextInt(maxLength/2) + 1);
        
        // Simple heuristic: extract characters from pattern and build matching string
        for (int i = 0; i < targetLength && i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (Character.isLetter(c)) {
                result.append(c);
                
                // If next char is a quantifier, possibly repeat
                if (i + 1 < pattern.length()) {
                    char next = pattern.charAt(i + 1);
                    if (next == '*' || next == '+') {
                        int repeats = random.nextInt(3) + (next == '+' ? 1 : 0);
                        for (int r = 0; r < repeats && result.length() < targetLength; r++) {
                            result.append(c);
                        }
                    }
                }
            }
        }
        
        // If string is too short, pad with random valid characters
        while (result.length() < targetLength) {
            result.append(CHARS[random.nextInt(CHARS.length)]);
        }
        
        return result.toString();
    }
    
    /**
     * Generate strings that don't match any pattern (random strings)
     */
    private static List<String> generateNonMatchingStrings(int maxLength, int count) {
        List<String> nonMatchingStrings = new ArrayList<>();
        
        for (int i = 0; i < count * 3; i++) { // Same number as matching strings
            int length = Math.min(maxLength, random.nextInt(maxLength/2) + 1);
            StringBuilder str = new StringBuilder();
            
            for (int j = 0; j < length; j++) {
                // Use a wider character set to reduce chance of accidental matches
                char c = (char) ('f' + random.nextInt(20)); // f-z range
                str.append(c);
            }
            
            nonMatchingStrings.add(str.toString());
        }
        
        return nonMatchingStrings;
    }
}