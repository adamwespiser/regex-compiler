package com.example.regexcompiler;


/**
 * Complete regex parser with AST nodes in a single file.
 * Supports: characters, concatenation, alternation (|), Kleene star (*), 
 * plus (+), optional (?), and grouping with parentheses.
 */
public class Parser {
    
    // ============================================
    // AST NODE CLASSES
    // ============================================
    
    public static abstract class RegexNode {
        public abstract String toString();
    }
    
    public static class CharNode extends RegexNode {
        public final char c;
        
        public CharNode(char c) { this.c = c; }
        
        @Override
        public String toString() { return String.valueOf(c); }
    }
    
    public static class ConcatNode extends RegexNode {
        public final RegexNode left, right;
        
        public ConcatNode(RegexNode left, RegexNode right) {
            this.left = left;
            this.right = right;
        }
        
        @Override
        public String toString() { return "(" + left + " . " + right + ")"; }
    }
    
    public static class AlternationNode extends RegexNode {
        public final RegexNode left, right;
        
        public AlternationNode(RegexNode left, RegexNode right) {
            this.left = left;
            this.right = right;
        }
        
        @Override
        public String toString() { return "(" + left + " | " + right + ")"; }
    }
    
    public static class StarNode extends RegexNode {
        public final RegexNode child;
        
        public StarNode(RegexNode child) { this.child = child; }
        
        @Override
        public String toString() { return "(" + child + ")*"; }
    }
    
    public static class PlusNode extends RegexNode {
        public final RegexNode child;
        
        public PlusNode(RegexNode child) { this.child = child; }
        
        @Override
        public String toString() { return "(" + child + ")+"; }
    }
    
    public static class OptionalNode extends RegexNode {
        public final RegexNode child;
        
        public OptionalNode(RegexNode child) { this.child = child; }
        
        @Override
        public String toString() { return "(" + child + ")?"; }
    }
    
    public static class EpsilonNode extends RegexNode {
        @Override
        public String toString() { return "ε"; }
    }
    
    // ============================================
    // EXCEPTION CLASS
    // ============================================
    
    public static class RegexParseException extends Exception {
        public RegexParseException(String message) { super(message); }
    }
    
    // ============================================
    // PARSER IMPLEMENTATION
    // ============================================
    
    private String input;
    private int pos;
    private int length;
    
    public Parser() {}
    
    public RegexNode parse(String regex) throws RegexParseException {
        this.input = regex;
        this.pos = 0;
        this.length = regex.length();
        
        if (length == 0) {
            return new EpsilonNode();
        }
        
        RegexNode result = parseExpression();
        
        if (pos < length) {
            throw new RegexParseException("Unexpected character at position " + pos + ": '" + peek() + "'");
        }
        
        return result;
    }
    
    // Grammar:
    // Expression → Term ('|' Term)*
    private RegexNode parseExpression() throws RegexParseException {
        RegexNode left = parseTerm();
        
        while (pos < length && peek() == '|') {
            consume('|');
            RegexNode right = parseTerm();
            left = new AlternationNode(left, right);
        }
        
        return left;
    }
    
    // Term → Factor Factor*  (concatenation)
    private RegexNode parseTerm() throws RegexParseException {
        if (pos >= length || peek() == '|' || peek() == ')') {
            return new EpsilonNode();
        }
        
        RegexNode left = parseFactor();
        
        while (pos < length && peek() != '|' && peek() != ')') {
            RegexNode right = parseFactor();
            left = new ConcatNode(left, right);
        }
        
        return left;
    }
    
    // Factor → Atom Quantifier?
    private RegexNode parseFactor() throws RegexParseException {
        RegexNode atom = parseAtom();
        
        if (pos < length) {
            char c = peek();
            switch (c) {
                case '*':
                    consume('*');
                    return new StarNode(atom);
                case '+':
                    consume('+');
                    return new PlusNode(atom);
                case '?':
                    consume('?');
                    return new OptionalNode(atom);
            }
        }
        
        return atom;
    }
    
    // Atom → Char | '(' Expression ')'
    private RegexNode parseAtom() throws RegexParseException {
        if (pos >= length) {
            throw new RegexParseException("Unexpected end of input");
        }
        
        char c = peek();
        
        if (c == '(') {
            consume('(');
            RegexNode expr = parseExpression();
            consume(')');
            return expr;
        } else if (isMetaChar(c)) {
            throw new RegexParseException("Unexpected metacharacter '" + c + "' at position " + pos);
        } else {
            return new CharNode(consume());
        }
    }
    
    // Utility methods
    private char peek() {
        if (pos >= length) return '\0';
        return input.charAt(pos);
    }
    
    private char consume() {
        if (pos >= length) return '\0';
        return input.charAt(pos++);
    }
    
    private void consume(char expected) throws RegexParseException {
        if (pos >= length) {
            throw new RegexParseException("Expected '" + expected + "' but reached end of input");
        }
        if (input.charAt(pos) != expected) {
            throw new RegexParseException("Expected '" + expected + "' but found '" + input.charAt(pos) + "' at position " + pos);
        }
        pos++;
    }
    
    private boolean isMetaChar(char c) {
        return c == '|' || c == '*' || c == '+' || c == '?' || c == ')';
    }
    
    // ============================================
    // DEMO AND TESTING
    // ============================================
    
    public static void main(String[] args) {
        Parser parser = new Parser();
        String[] testCases = {
            "a",
            "ab", 
            "a|b",
            "a*",
            "a+",
            "a?",
            "(a|b)*",
            "a(b|c)*d",
            "(a+b)*c",
            "((a|b)*c)+",
            "a|b*c",
            "(a|b)*(c|d)+",
            ""
        };
        
        System.out.println("=== Regex Parser Test ===\n");
        
        for (String regex : testCases) {
            System.out.println("Parsing: \"" + regex + "\"");
            try {
                RegexNode ast = parser.parse(regex);
                System.out.println("Result:  " + ast);
            } catch (RegexParseException e) {
                System.out.println("Error:   " + e.getMessage());
            }
            System.out.println();
        }
        
        // Test error cases
        System.out.println("=== Error Cases ===\n");
        String[] errorCases = {
            "a)",
            "(a", 
            "*a",
            "a||b",
            "a++",
            "()"
        };
        
        for (String regex : errorCases) {
            System.out.println("Parsing: \"" + regex + "\"");
            try {
                RegexNode ast = parser.parse(regex);
                System.out.println("Result:  " + ast);
            } catch (RegexParseException e) {
                System.out.println("Error:   " + e.getMessage());
            }
            System.out.println();
        }
    }
}