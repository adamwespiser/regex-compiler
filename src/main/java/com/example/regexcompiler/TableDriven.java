package com.example.regexcompiler;

import java.util.*;

/**
 * Table-driven implementation with DFA optimization and transition table conversion
 */
class TableDriven implements Pattern {
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
            RegexToDFA.DFA dfa = converter.convertRegexToDFA(regex);
            
            RegexToDFA.DFA optimizedDfa = optimizeDFA(dfa);
            
            TableDriven compiled = new TableDriven();
            compiled.patternString = regex;
            // Convert DFA to transition table
            compiled.convertDFAToTable(optimizedDfa);
            
            long compileTime = System.nanoTime() - startTime;
            compiled.stats = Map.of(
                "implementation", "Table-Driven (Optimized DFA)",
                "compileTimeNs", compileTime,
                "originalStates", dfa.states.size(),
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
     * Optimize DFA by removing unreachable states (disable equivalence merging for now)
     */
    private RegexToDFA.DFA optimizeDFA(RegexToDFA.DFA dfa) {
        // Step 1: Remove unreachable states (this is safe)
        Set<RegexToDFA.DFAState> reachableStates = findReachableStates(dfa);
        
        // Create a NEW set to avoid any reference issues
        Set<RegexToDFA.DFAState> optimizedStates = new HashSet<>(reachableStates);
        
        // Ensure start state is in the optimized set
        if (!optimizedStates.contains(dfa.start)) {
            throw new RuntimeException("Optimization removed start state - this should never happen! " +
                "Start state: " + dfa.start.id + ", Optimized states: " + 
                optimizedStates.stream().map(s -> String.valueOf(s.id)).reduce((a,b) -> a + "," + b).orElse("none"));
        }
        
        // Create optimized DFA
        return new RegexToDFA.DFA(dfa.start, optimizedStates, dfa.alphabet);
    }
    
    /**
     * Find all states reachable from the start state (INCLUDING the start state itself)
     */
    private Set<RegexToDFA.DFAState> findReachableStates(RegexToDFA.DFA dfa) {
        Set<RegexToDFA.DFAState> reachable = new HashSet<>();
        Queue<RegexToDFA.DFAState> queue = new ArrayDeque<>();
        
        // Add the start state first
        queue.offer(dfa.start);
        reachable.add(dfa.start);
        
        while (!queue.isEmpty()) {
            RegexToDFA.DFAState current = queue.poll();
            
            for (RegexToDFA.DFAState target : current.transitions.values()) {
                if (!reachable.contains(target)) {
                    reachable.add(target);
                    queue.offer(target);
                }
            }
        }
        
        return reachable;
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