package com.wespiser.regexcompiler.algorithms;

import com.wespiser.regexcompiler.Pattern;
import com.wespiser.regexcompiler.RegexCompileException;
import com.wespiser.regexcompiler.Parser;
import java.util.*;

/**
 * Table-driven implementation with DFA optimization and transition table conversion
 *
 *  The table-driven algorithm first converts the regex to an optimized DFA, then transforms it into a
 *  compact 2D transition table representation. States are mapped to integer indices, and characters in
 *  the alphabet are mapped to column indices, creating a dense integer matrix where table[state][char]
 *  gives the next state (-1 for no transition). Runtime complexity is O(n) for matching, with very fast
 *  constant-time state transitions via array lookups. Space complexity is O(s×a) where s is the number of
 *   states and a is the alphabet size. The strategy optimizes for runtime performance by trading space
 *  for speed, using simple array indexing instead of hash map lookups, making it ideal for
 *  high-performance applications where the alphabet size is reasonable.
 */
public class TableDriven implements Pattern {
    private String patternString;
    private int[][] transitionTable;
    private boolean[] acceptingStates;
    private Map<Character, Integer> charToIndex;
    private int startState;
    private Map<String, Object> stats;
    
    @Override
    public Pattern compile(String regex) throws RegexCompileException {
        long startTime = System.nanoTime();
        
        try {
            RegexToDFA converter = new RegexToDFA();
            RegexToDFA.DFA optimizedDfa = converter.convertRegexToDFAOptimized(regex);
            
            TableDriven compiled = new TableDriven();
            compiled.patternString = regex;
            // Convert DFA to transition table
            compiled.convertDFAToTable(optimizedDfa);
            
            long compileTime = System.nanoTime() - startTime;
            compiled.stats = Map.of(
                "implementation", "Table-Driven (Optimized DFA)",
                "compileTimeNs", compileTime,
                "optimizedStates", optimizedDfa.states.size(),
                "alphabetSize", optimizedDfa.alphabet.size(),
                "tableSize", compiled.transitionTable.length * compiled.transitionTable[0].length
            );
            
            return compiled;
            
        } catch (Parser.RegexParseException e) {
            throw new RegexCompileException("Failed to compile regex: " + regex, e);
        }
    }
    /**
     * Convert optimized DFA to compact transition table representation
     */
    private void convertDFAToTable(RegexToDFA.DFA dfa) {
        // Create state ID mapping (only include reachable states)
        List<RegexToDFA.DFAState> stateList = new ArrayList<>(dfa.states);
        Map<RegexToDFA.DFAState, Integer> stateToId = new HashMap<>();
        
        for (int i = 0; i < stateList.size(); i++) {
            stateToId.put(stateList.get(i), i);
        }
        
        // Collect all characters actually used in transitions (rebuild alphabet)
        Set<Character> actualAlphabet = new HashSet<>();
        for (RegexToDFA.DFAState state : dfa.states) {
            actualAlphabet.addAll(state.transitions.keySet());
        }
        
        // Create character index mapping from actual alphabet
        List<Character> alphabetList = new ArrayList<>(actualAlphabet);
        Collections.sort(alphabetList);
        this.charToIndex = new HashMap<>();
        
        for (int i = 0; i < alphabetList.size(); i++) {
            charToIndex.put(alphabetList.get(i), i);
        }
        
        // Initialize transition table (-1 means no transition)
        int numStates = stateList.size();
        int numChars = alphabetList.size();
        this.transitionTable = new int[numStates][numChars];
        
        for (int i = 0; i < numStates; i++) {
            Arrays.fill(transitionTable[i], -1);
        }
        
        // Fill transition table
        for (int stateIdx = 0; stateIdx < numStates; stateIdx++) {
            RegexToDFA.DFAState state = stateList.get(stateIdx);
            
            for (Map.Entry<Character, RegexToDFA.DFAState> entry : state.transitions.entrySet()) {
                char c = entry.getKey();
                RegexToDFA.DFAState target = entry.getValue();
                
                Integer charIdx = charToIndex.get(c);
                Integer targetIdx = stateToId.get(target);
                
                // Defensive programming - skip if mapping not found
                if (charIdx != null && targetIdx != null) {
                    transitionTable[stateIdx][charIdx] = targetIdx;
                }
            }
        }
        
        // Create accepting states array
        this.acceptingStates = new boolean[numStates];
        for (int i = 0; i < numStates; i++) {
            acceptingStates[i] = stateList.get(i).isAccepting;
        }
        
        // Set start state - ENSURE we find the right start state
        Integer startIdx = stateToId.get(dfa.start);
        if (startIdx == null) {
            throw new RuntimeException("Start state not found in state mapping!");
        }
        this.startState = startIdx;
    }
    
    @Override
    public boolean matches(String input) {
        if (transitionTable == null) {
            // Fallback if table construction failed
            try {
                return Pattern.compileDFA(patternString).matches(input);
            } catch (RegexCompileException e) {
                return false;
            }
        }
        
        // DEBUG: Add logging for empty string case
        if (input.isEmpty()) {
            boolean result = acceptingStates[startState];
            return result;
        }
        
        int currentState = startState;
        
        for (char c : input.toCharArray()) {
            Integer charIdx = charToIndex.get(c);
            
            // Character not in alphabet - reject
            if (charIdx == null) {
                return false;
            }
            
            // Get next state from transition table
            int nextState = transitionTable[currentState][charIdx];
            
            // No transition defined - reject
            if (nextState == -1) {
                return false;
            }
            
            currentState = nextState;
        }
        
        return acceptingStates[currentState];
    }
    
    @Override
    public boolean find(String input) {
        // Try matching at each position
        for (int i = 0; i <= input.length(); i++) {
            for (int j = i; j <= input.length(); j++) {
                if (matches(input.substring(i, j))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public String pattern() {
        return patternString;
    }
    
    @Override
    public String getImplementationName() {
        return "Table-Driven (Optimized Transition Table)";
    }
    
    @Override
    public Map<String, Object> getCompilationStats() {
        return stats;
    }
    
    /**
     * Debug method to print the transition table
     */
    public void printTransitionTable() {
        if (transitionTable == null) {
            System.out.println("No transition table available");
            return;
        }
        
        System.out.println("Transition Table for: " + patternString);
        System.out.println("Start state: " + startState);
        
        // Print header
        System.out.print("State\t");
        List<Character> sortedChars = new ArrayList<>(charToIndex.keySet());
        Collections.sort(sortedChars);
        for (char c : sortedChars) {
            System.out.print(c + "\t");
        }
        System.out.println("Accept");
        
        // Print table rows
        for (int state = 0; state < transitionTable.length; state++) {
            System.out.print(state + (state == startState ? "*" : "") + "\t");
            
            for (char c : sortedChars) {
                int charIdx = charToIndex.get(c);
                int target = transitionTable[state][charIdx];
                System.out.print((target == -1 ? "-" : String.valueOf(target)) + "\t");
            }
            
            System.out.println(acceptingStates[state] ? "YES" : "NO");
        }
        System.out.println();
    }
} 