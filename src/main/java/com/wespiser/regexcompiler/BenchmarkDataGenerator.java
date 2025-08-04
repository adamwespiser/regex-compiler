package com.wespiser.regexcompiler;

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
    private static final String[] BASIC_QUANTIFIERS = {"*", "+", "?"}; // Exclude empty to avoid consecutive quantifiers
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
        int[] sizes = {10, 50, 100, 500, 1000, 10000};
        
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
     * Generate regex patterns with consistent target length
     */
    private static List<String> generatePatterns(int targetLength, int count) {
        List<String> patterns = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String pattern;
            
            // Allow small variation (±20%) around target length for diversity
            int minLength = Math.max(1, (int)(targetLength * 0.8));
            int maxLength = (int)(targetLength * 1.2);
            int actualLength = minLength + random.nextInt(maxLength - minLength + 1);
            
            // Generate pattern with retry logic for validation
            int attempts = 0;
            do {
                if (targetLength <= 10) {
                    // Simple patterns for small sizes
                    pattern = generateSimplePattern(actualLength);
                } else if (targetLength <= 1000) {
                    // More complex patterns for medium sizes
                    pattern = generateComplexPattern(actualLength);
                } else {
                    // For very large sizes, use repetition patterns to avoid compilation issues
                    pattern = generateRepetitionPattern(actualLength);
                }
                attempts++;
            } while (!isValidPattern(pattern) && attempts < 5);
            
            // If we couldn't generate a valid pattern, create a simple fallback
            if (!isValidPattern(pattern)) {
                pattern = generateSimpleFallback(Math.min(actualLength, 10));
            }
            
            patterns.add(pattern);
        }
        
        return patterns;
    }
    
    /**
     * Generate simple regex patterns with valid syntax
     */
    private static String generateSimplePattern(int length) {
        StringBuilder pattern = new StringBuilder();
        boolean lastWasQuantifier = false;
        
        while (pattern.length() < length) {
            double choice = random.nextDouble();
            
            if (choice < 0.6) {
                // Add regular character (most common)
                pattern.append(CHARS[random.nextInt(CHARS.length)]);
                lastWasQuantifier = false;
                
            } else if (choice < 0.8 && !lastWasQuantifier && pattern.length() > 0) {
                // Add quantifier to previous character (only if last wasn't a quantifier)
                String quantifier = BASIC_QUANTIFIERS[random.nextInt(BASIC_QUANTIFIERS.length)];
                pattern.append(quantifier);
                lastWasQuantifier = true;
                
            } else if (choice < 0.9 && pattern.length() > 0 && pattern.length() < length - 1) {
                // Add alternation (ensure we have room for at least one more character)
                pattern.append("|").append(CHARS[random.nextInt(CHARS.length)]);
                lastWasQuantifier = false;
                
            } else {
                // Add regular character as fallback
                pattern.append(CHARS[random.nextInt(CHARS.length)]);
                lastWasQuantifier = false;
            }
        }
        
        return pattern.toString();
    }
    
    /**
     * Generate complex regex patterns with grouping and valid syntax
     */
    private static String generateComplexPattern(int targetLength) {
        StringBuilder pattern = new StringBuilder();
        boolean lastWasQuantifier = false;
        
        while (pattern.length() < targetLength) {
            int remainingLength = targetLength - pattern.length();
            double choice = random.nextDouble();
            
            if (choice < 0.25 && remainingLength >= 6 && !lastWasQuantifier) {
                // Add grouped pattern with alternation: (a|b)
                pattern.append("(");
                pattern.append(CHARS[random.nextInt(CHARS.length)]);
                pattern.append("|");
                pattern.append(CHARS[random.nextInt(CHARS.length)]);
                pattern.append(")");
                lastWasQuantifier = false;
                
                // Maybe add quantifier to group
                if (random.nextDouble() < 0.5 && remainingLength > 6) {
                    String quantifier = BASIC_QUANTIFIERS[random.nextInt(BASIC_QUANTIFIERS.length)];
                    pattern.append(quantifier);
                    lastWasQuantifier = true;
                }
                
            } else if (choice < 0.4 && !lastWasQuantifier && pattern.length() > 0) {
                // Add quantifier to previous element
                String quantifier = BASIC_QUANTIFIERS[random.nextInt(BASIC_QUANTIFIERS.length)];
                pattern.append(quantifier);
                lastWasQuantifier = true;
                
            } else if (choice < 0.6 && pattern.length() > 0 && remainingLength >= 3) {
                // Add alternation
                pattern.append("|");
                pattern.append(CHARS[random.nextInt(CHARS.length)]);
                lastWasQuantifier = false;
                
            } else {
                // Add regular character
                pattern.append(CHARS[random.nextInt(CHARS.length)]);
                lastWasQuantifier = false;
            }
        }
        
        return pattern.toString();
    }
    
    /**
     * Generate simple patterns for very large sizes with valid syntax
     * Creates basic patterns that are fast to compile and execute
     */
    private static String generateRepetitionPattern(int targetLength) {
        StringBuilder pattern = new StringBuilder();
        boolean lastWasQuantifier = false;
        
        // For very large sizes, create short simple patterns
        int maxPatternLength = Math.min(30, Math.max(5, targetLength / 1000));
        
        while (pattern.length() < maxPatternLength) {
            double choice = random.nextDouble();
            int remainingLength = maxPatternLength - pattern.length();
            
            if (choice < 0.3 && remainingLength >= 5 && !lastWasQuantifier) {
                // Simple alternation (a|b)
                pattern.append("(")
                        .append(CHARS[random.nextInt(CHARS.length)])
                        .append("|")
                        .append(CHARS[random.nextInt(CHARS.length)])
                        .append(")");
                lastWasQuantifier = false;
                
            } else if (choice < 0.5 && !lastWasQuantifier && pattern.length() > 0) {
                // Add quantifier to previous element
                String quantifier = BASIC_QUANTIFIERS[random.nextInt(BASIC_QUANTIFIERS.length)];
                pattern.append(quantifier);
                lastWasQuantifier = true;
                
            } else {
                // Add a simple character
                pattern.append(CHARS[random.nextInt(CHARS.length)]);
                lastWasQuantifier = false;
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
    
    /**
     * Validate that a pattern follows supported regex syntax
     */
    private static boolean isValidPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        
        boolean lastWasQuantifier = false;
        int parenDepth = 0;
        
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            
            switch (c) {
                case '*':
                case '+':
                case '?':
                    // Quantifiers can't be first, can't follow other quantifiers
                    if (i == 0 || lastWasQuantifier) {
                        return false;
                    }
                    lastWasQuantifier = true;
                    break;
                    
                case '(':
                    parenDepth++;
                    lastWasQuantifier = false;
                    break;
                    
                case ')':
                    parenDepth--;
                    if (parenDepth < 0) {
                        return false; // Unmatched closing paren
                    }
                    lastWasQuantifier = false;
                    break;
                    
                case '|':
                    // Alternation can't be first or last, and can't follow quantifiers
                    if (i == 0 || i == pattern.length() - 1 || lastWasQuantifier) {
                        return false;
                    }
                    lastWasQuantifier = false;
                    break;
                    
                case '[':
                case ']':
                    // Character classes not supported by parser
                    return false;
                    
                default:
                    // Regular character
                    lastWasQuantifier = false;
                    break;
            }
        }
        
        // Check for unmatched parentheses
        return parenDepth == 0;
    }
    
    /**
     * Generate a simple fallback pattern that's guaranteed to be valid
     */
    private static String generateSimpleFallback(int length) {
        StringBuilder pattern = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            if (i > 0 && random.nextDouble() < 0.3) {
                // Add alternation occasionally
                pattern.append("|");
            }
            pattern.append(CHARS[random.nextInt(CHARS.length)]);
            
            // Add quantifier occasionally (but not after alternation)
            if (random.nextDouble() < 0.2 && !pattern.toString().endsWith("|")) {
                pattern.append(BASIC_QUANTIFIERS[random.nextInt(BASIC_QUANTIFIERS.length)]);
            }
        }
        
        return pattern.toString();
    }
}