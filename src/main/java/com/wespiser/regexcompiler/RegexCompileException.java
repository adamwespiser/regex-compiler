package com.wespiser.regexcompiler;

/**
 * Exception thrown when regex compilation fails
 */
public class RegexCompileException extends Exception {
    public RegexCompileException(String message) {
        super(message);
    }
    
    public RegexCompileException(String message, Throwable cause) {
        super(message, cause);
    }
}