package com.example.regexcompiler;

import java.util.*;
import java.util.stream.Collectors;
import com.example.regexcompiler.RegexToDFA;
import com.example.regexcompiler.Parser;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fully optimized table-driven implementation with:
 * - DFA minimization (Hopcroft's algorithm)
 * - Dead state elimination
 * - Character class optimization
 * - Table compression
 * - Cache-friendly layout
 * - Special case optimizations
 */
class TableDrivenOptimized implements Pattern {
    private String patternString;
    private int[][] transitionTable;
    private boolean[] acceptingStates;
    private Map<Character, Integer> charToIndex;
    private int startState;
    private Map<String, Object> stats;
    
    // Optimization flags
    private boolean isAnchored;
    private boolean hasSmallAlphabet;
    
    // Compressed table structures
    private CompressedTable compressedTable;
    private int[] characterClasses;
    
    @Override
    public Pattern compile(String regex) throws RegexCompileException {
        long startTime = System.nanoTime();
        
        try {
            RegexToDFA converter = new RegexToDFA();
            RegexToDFA.DFA dfa = converter.convertRegexToDFA(regex);
            
            // Apply all optimizations in sequence
            int originalStates = dfa.states.size();
            
            // 1. Remove unreachable states
            dfa = removeUnreachableStates(dfa);
            int afterUnreachable = dfa.states.size();
            
            // 2. Remove dead states (states that can't reach accepting states)
            dfa = removeDeadStates(dfa);
            int afterDead = dfa.states.size();
            
            // 3. Minimize DFA using Hopcroft's algorithm
            dfa = minimizeDFA(dfa);
            int afterMinimization = dfa.states.size();
            
            // 4. Optimize character classes
            Map<Character, Integer> charClasses = computeCharacterClasses(dfa);
            int originalAlphabetSize = dfa.alphabet.size();
            int optimizedAlphabetSize = new HashSet<>(charClasses.values()).size();
            
            TableDrivenOptimized compiled = new TableDrivenOptimized();
            compiled.patternString = regex;
            
            // Check for special cases
            compiled.isAnchored = isAnchoredPattern(regex);
            compiled.hasSmallAlphabet = optimizedAlphabetSize < 8;
            
            // Convert optimized DFA to table
            compiled.convertDFAToTable(dfa, charClasses);
            
            // Apply table compression if beneficial
            if (shouldCompressTable(compiled.transitionTable)) {
                compiled.compressedTable = compressTable(compiled.transitionTable);
            }
            
            long compileTime = System.nanoTime() - startTime;
            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put("implementation", "Table-Driven (Fully Optimized)");
            statsMap.put("compileTimeNs", compileTime);
            statsMap.put("originalStates", originalStates);
            statsMap.put("afterUnreachable", afterUnreachable);
            statsMap.put("afterDead", afterDead);
            statsMap.put("afterMinimization", afterMinimization);
            statsMap.put("originalAlphabetSize", originalAlphabetSize);
            statsMap.put("optimizedAlphabetSize", optimizedAlphabetSize);
            statsMap.put("tableSize", compiled.transitionTable.length * compiled.transitionTable[0].length);
            statsMap.put("isAnchored", compiled.isAnchored);
            statsMap.put("hasSmallAlphabet", compiled.hasSmallAlphabet);
            statsMap.put("isCompressed", compiled.compressedTable != null);
            
            compiled.stats = statsMap;
 
            return compiled;
            
        } catch (Parser.RegexParseException e) {
            throw new RegexCompileException("Failed to compile regex: " + regex, e);
        }
    }
    
    /**
     * Remove states that cannot be reached from the start state
     */
    private RegexToDFA.DFA removeUnreachableStates(RegexToDFA.DFA dfa) {
        Set<RegexToDFA.DFAState> reachable = new HashSet<>();
        Queue<RegexToDFA.DFAState> queue = new ArrayDeque<>();
        
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
        
        return new RegexToDFA.DFA(dfa.start, reachable, dfa.alphabet);
    }
    
    /**
     * Remove dead states (states from which no accepting state is reachable)
     */
    private RegexToDFA.DFA removeDeadStates(RegexToDFA.DFA dfa) {
        Set<RegexToDFA.DFAState> liveStates = new HashSet<>();
        Queue<RegexToDFA.DFAState> queue = new ArrayDeque<>();
        
        // Start with accepting states
        for (RegexToDFA.DFAState state : dfa.states) {
            if (state.isAccepting) {
                queue.offer(state);
                liveStates.add(state);
            }
        }
        
        // Work backwards to find all states that can reach accepting states
        boolean changed = true;
        while (changed) {
            changed = false;
            for (RegexToDFA.DFAState state : dfa.states) {
                if (!liveStates.contains(state)) {
                    for (RegexToDFA.DFAState target : state.transitions.values()) {
                        if (liveStates.contains(target)) {
                            liveStates.add(state);
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }
        
        // Must keep start state even if it's dead
        liveStates.add(dfa.start);
        
        return new RegexToDFA.DFA(dfa.start, liveStates, dfa.alphabet);
    }
    
    /**
     * Minimize DFA using Hopcroft's algorithm
     */
    private RegexToDFA.DFA minimizeDFA(RegexToDFA.DFA dfa) {
        // Initial partition: accepting vs non-accepting
        List<Set<RegexToDFA.DFAState>> partition = new ArrayList<>();
        Set<RegexToDFA.DFAState> accepting = new HashSet<>();
        Set<RegexToDFA.DFAState> nonAccepting = new HashSet<>();
        
        for (RegexToDFA.DFAState state : dfa.states) {
            if (state.isAccepting) {
                accepting.add(state);
            } else {
                nonAccepting.add(state);
            }
        }
        
        if (!accepting.isEmpty()) partition.add(accepting);
        if (!nonAccepting.isEmpty()) partition.add(nonAccepting);
        
        // Refine partition until stable
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Set<RegexToDFA.DFAState>> newPartition = new ArrayList<>();
            
            for (Set<RegexToDFA.DFAState> group : partition) {
                if (group.size() <= 1) {
                    newPartition.add(group);
                    continue;
                }
                
                // Try to split this group based on transition behavior
                Map<String, Set<RegexToDFA.DFAState>> signatures = new HashMap<>();
                
                for (RegexToDFA.DFAState state : group) {
                    String signature = computeStateSignature(state, partition, dfa.alphabet);
                    signatures.computeIfAbsent(signature, k -> new HashSet<>()).add(state);
                }
                
                if (signatures.size() > 1) {
                    changed = true;
                    newPartition.addAll(signatures.values());
                } else {
                    newPartition.add(group);
                }
            }
            
            partition = newPartition;
        }
        
        // Build minimized DFA
        return buildMinimizedDFA(dfa, partition);
    }
    
    /**
     * Compute signature for a state based on which partition its transitions go to
     */
    private String computeStateSignature(RegexToDFA.DFAState state, 
                                       List<Set<RegexToDFA.DFAState>> partition,
                                       Set<Character> alphabet) {
        StringBuilder sig = new StringBuilder();
        
        for (Character c : alphabet) {
            RegexToDFA.DFAState target = state.transitions.get(c);
            if (target == null) {
                sig.append("-1,");
            } else {
                // Find which partition the target belongs to
                for (int i = 0; i < partition.size(); i++) {
                    if (partition.get(i).contains(target)) {
                        sig.append(i).append(",");
                        break;
                    }
                }
            }
        }
        
        return sig.toString();
    }
    
    /**
     * Build minimized DFA from partition
     */
    private RegexToDFA.DFA buildMinimizedDFA(RegexToDFA.DFA original, 
                                           List<Set<RegexToDFA.DFAState>> partition) {
        // Create mapping from old states to their partition
        Map<RegexToDFA.DFAState, Set<RegexToDFA.DFAState>> stateToPartition = new HashMap<>();
        for (Set<RegexToDFA.DFAState> part : partition) {
            for (RegexToDFA.DFAState state : part) {
                stateToPartition.put(state, part);
            }
        }
        
        // Create one new DFA state per partition
        Map<Set<RegexToDFA.DFAState>, RegexToDFA.DFAState> partitionToNewState = new HashMap<>();
        
        for (Set<RegexToDFA.DFAState> part : partition) {
            // Collect all NFA states from all DFA states in this partition
            Set<RegexToDFA.NFAState> combinedNFAStates = new HashSet<>();
            boolean isAccepting = false;
            
            for (RegexToDFA.DFAState state : part) {
                if (state.nfaStates != null) {
                    combinedNFAStates.addAll(state.nfaStates);
                }
                if (state.isAccepting) {
                    isAccepting = true;
                }
            }
            
            // Create new DFA state representing this partition
            RegexToDFA.DFAState newState = new RegexToDFA.DFAState(combinedNFAStates);
            newState.isAccepting = isAccepting;
            partitionToNewState.put(part, newState);
        }
        
        // Build transitions for new states
        for (Map.Entry<Set<RegexToDFA.DFAState>, RegexToDFA.DFAState> entry : partitionToNewState.entrySet()) {
            Set<RegexToDFA.DFAState> sourcePartition = entry.getKey();
            RegexToDFA.DFAState newState = entry.getValue();
            
            // Use transitions from any state in the partition (they should all be equivalent)
            RegexToDFA.DFAState representative = sourcePartition.iterator().next();
            
            for (Map.Entry<Character, RegexToDFA.DFAState> trans : representative.transitions.entrySet()) {
                Character c = trans.getKey();
                RegexToDFA.DFAState oldTarget = trans.getValue();
                
                // Find which partition the target belongs to
                Set<RegexToDFA.DFAState> targetPartition = stateToPartition.get(oldTarget);
                if (targetPartition != null) {
                    RegexToDFA.DFAState newTarget = partitionToNewState.get(targetPartition);
                    if (newTarget != null) {
                        newState.addTransition(c, newTarget);
                    }
                }
            }
        }
        
        // Find new start state
        Set<RegexToDFA.DFAState> startPartition = stateToPartition.get(original.start);
        RegexToDFA.DFAState newStart = partitionToNewState.get(startPartition);
        
        if (newStart == null) {
            // Fallback if something went wrong - should not happen if partition is correct
            System.err.println("Warning: Could not find start state in minimized DFA, returning original");
            return original;
        }
        
        return new RegexToDFA.DFA(newStart, new HashSet<>(partitionToNewState.values()), original.alphabet);
    }
       
    
    /**
     * Compute character classes - group characters that always have same transitions
     */
    private Map<Character, Integer> computeCharacterClasses(RegexToDFA.DFA dfa) {
        Map<Character, String> charSignatures = new HashMap<>();
        List<RegexToDFA.DFAState> stateList = new ArrayList<>(dfa.states);
        
        // Compute signature for each character
        for (Character c : dfa.alphabet) {
            StringBuilder sig = new StringBuilder();
            for (RegexToDFA.DFAState state : stateList) {
                RegexToDFA.DFAState target = state.transitions.get(c);
                sig.append(target != null ? target.id : -1).append(",");
            }
            charSignatures.put(c, sig.toString());
        }
        
        // Group characters with same signature
        Map<String, Integer> signatureToClass = new HashMap<>();
        Map<Character, Integer> charToClass = new HashMap<>();
        int classId = 0;
        
        for (Map.Entry<Character, String> entry : charSignatures.entrySet()) {
            String sig = entry.getValue();
            if (!signatureToClass.containsKey(sig)) {
                signatureToClass.put(sig, classId++);
            }
            charToClass.put(entry.getKey(), signatureToClass.get(sig));
        }
        
        return charToClass;
    }
    
    /**
     * Convert DFA to transition table with character class optimization
     */
    private void convertDFAToTable(RegexToDFA.DFA dfa, Map<Character, Integer> charClasses) {
        // Create state mapping
        List<RegexToDFA.DFAState> stateList = new ArrayList<>(dfa.states);
        Map<RegexToDFA.DFAState, Integer> stateToId = new HashMap<>();
        
        for (int i = 0; i < stateList.size(); i++) {
            stateToId.put(stateList.get(i), i);
        }
        
        // Create optimized character mapping
        int numClasses = new HashSet<>(charClasses.values()).size();
        this.characterClasses = new int[256]; // ASCII
        Arrays.fill(characterClasses, -1);
        
        // Build character to class index mapping
        this.charToIndex = new HashMap<>();
        for (Map.Entry<Character, Integer> entry : charClasses.entrySet()) {
            charToIndex.put(entry.getKey(), entry.getValue());
            if (entry.getKey() < 256) {
                characterClasses[entry.getKey()] = entry.getValue();
            }
        }
        
        // Initialize transition table
        int numStates = stateList.size();
        this.transitionTable = new int[numStates][numClasses];
        
        for (int i = 0; i < numStates; i++) {
            Arrays.fill(transitionTable[i], -1);
        }
        
        // Fill transition table using character classes
        for (int stateIdx = 0; stateIdx < numStates; stateIdx++) {
            RegexToDFA.DFAState state = stateList.get(stateIdx);
            
            for (Map.Entry<Character, RegexToDFA.DFAState> entry : state.transitions.entrySet()) {
                char c = entry.getKey();
                RegexToDFA.DFAState target = entry.getValue();
                
                Integer classIdx = charClasses.get(c);
                Integer targetIdx = stateToId.get(target);
                
                if (classIdx != null && targetIdx != null) {
                    transitionTable[stateIdx][classIdx] = targetIdx;
                }
            }
        }
        
        // Create accepting states array
        this.acceptingStates = new boolean[numStates];
        for (int i = 0; i < numStates; i++) {
            acceptingStates[i] = stateList.get(i).isAccepting;
        }
        
        // Set start state
        this.startState = stateToId.get(dfa.start);
    }
    
    /**
     * Check if pattern is anchored (starts with ^ or doesn't need substring search)
     */
    private boolean isAnchoredPattern(String regex) {
        return regex.startsWith("^") || 
               (!regex.contains("*") && !regex.contains("+") && !regex.contains("?"));
    }
    
    /**
     * Determine if table compression would be beneficial
     */
    private boolean shouldCompressTable(int[][] table) {
        if (table.length < 10 || table[0].length < 10) {
            return false; // Too small to benefit
        }
        
        // Count unique rows
        Set<List<Integer>> uniqueRows = new HashSet<>();
        for (int[] row : table) {
            uniqueRows.add(Arrays.stream(row).boxed().collect(Collectors.toList()));
        }
        
        // Compress if we have significant duplication
        return uniqueRows.size() < table.length * 0.7;
    }
    
    /**
     * Compress transition table using row sharing
     */
    private CompressedTable compressTable(int[][] table) {
        Map<List<Integer>, Integer> rowMap = new HashMap<>();
        int[] rowIndices = new int[table.length];
        List<int[]> uniqueRows = new ArrayList<>();
        
        for (int i = 0; i < table.length; i++) {
            List<Integer> rowKey = Arrays.stream(table[i]).boxed().collect(Collectors.toList());
            
            Integer existingIndex = rowMap.get(rowKey);
            if (existingIndex != null) {
                rowIndices[i] = existingIndex;
            } else {
                rowIndices[i] = uniqueRows.size();
                rowMap.put(rowKey, uniqueRows.size());
                uniqueRows.add(Arrays.copyOf(table[i], table[i].length));
            }
        }
        
        return new CompressedTable(uniqueRows.toArray(new int[0][]), rowIndices);
    }
    
    @Override
    public boolean matches(String input) {
        if (input.isEmpty()) {
            return acceptingStates[startState];
        }
        
        // Use optimized matching for anchored patterns
        if (isAnchored) {
            return matchesAnchored(input);
        }
        
        // Use compressed table if available
        if (compressedTable != null) {
            return matchesCompressed(input);
        }
        
        // Standard table-driven matching
        int currentState = startState;
        
        for (char c : input.toCharArray()) {
            int classIdx = getCharacterClass(c);
            
            if (classIdx == -1) {
                return false;
            }
            
            int nextState = transitionTable[currentState][classIdx];
            
            if (nextState == -1) {
                return false;
            }
            
            currentState = nextState;
        }
        
        return acceptingStates[currentState];
    }
    
    /**
     * Optimized matching for anchored patterns
     */
    private boolean matchesAnchored(String input) {
        int state = startState;
        
        for (int i = 0; i < input.length(); i++) {
            int classIdx = getCharacterClass(input.charAt(i));
            
            if (classIdx == -1 || transitionTable[state][classIdx] == -1) {
                return false;
            }
            
            state = transitionTable[state][classIdx];
        }
        
        return acceptingStates[state];
    }
    
    /**
     * Matching using compressed table
     */
    private boolean matchesCompressed(String input) {
        int state = startState;
        
        for (char c : input.toCharArray()) {
            int classIdx = getCharacterClass(c);
            
            if (classIdx == -1) {
                return false;
            }
            
            int rowIdx = compressedTable.rowIndices[state];
            int nextState = compressedTable.rows[rowIdx][classIdx];
            
            if (nextState == -1) {
                return false;
            }
            
            state = nextState;
        }
        
        return acceptingStates[state];
    }
    
    /**
     * Get character class for a character
     */
    private int getCharacterClass(char c) {
        if (c < 256 && characterClasses != null) {
            return characterClasses[c];
        }
        
        Integer classIdx = charToIndex.get(c);
        return classIdx != null ? classIdx : -1;
    }
    
    @Override
    public boolean find(String input) {
        // Optimize for anchored patterns
        if (isAnchored) {
            return matches(input);
        }
        
        // Try matching at each position
        for (int start = 0; start <= input.length(); start++) {
            // Try matching from this start position
            if (matchesFrom(input, start)) {
                return true;
            }
        }
        
        return false;
    }
        private boolean matchesFrom(String input, int start) {
        int state = startState;
        
        // First check if we accept empty string at this position
        if (acceptingStates[state]) {
            return true;
        }
        
        // Try to match from position 'start'
        for (int pos = start; pos < input.length(); pos++) {
            int classIdx = getCharacterClass(input.charAt(pos));
            
            if (classIdx == -1) {
                return false;
            }
            
            int nextState;
            if (compressedTable != null) {
                int rowIdx = compressedTable.rowIndices[state];
                nextState = compressedTable.rows[rowIdx][classIdx];
            } else {
                nextState = transitionTable[state][classIdx];
            }
            
            if (nextState == -1) {
                return false;
            }
            
            state = nextState;
            
            // Check if we've reached an accepting state
            if (acceptingStates[state]) {
                return true;
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
        return "Table-Driven (Fully Optimized)";
    }
    
    @Override
    public Map<String, Object> getCompilationStats() {
        return stats;
    }
    
    /**
     * Compressed table representation
     */
    private static class CompressedTable {
        final int[][] rows;
        final int[] rowIndices;
        
        CompressedTable(int[][] rows, int[] rowIndices) {
            this.rows = rows;
            this.rowIndices = rowIndices;
        }
    }
    
    /**
     * Debug method to print optimization statistics
     */
    public void printOptimizationStats() {
        System.out.println("Optimization Statistics for: " + patternString);
        System.out.println("========================");
        
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            System.out.printf("%-20s: %s%n", entry.getKey(), entry.getValue());
        }
        
        if (compressedTable != null) {
            System.out.printf("Compression ratio: %.2f%%%n", 
                (1.0 - (double)compressedTable.rows.length / transitionTable.length) * 100);
        }
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
        System.out.println("Character classes: " + charToIndex.size());
        
        // Print header
        System.out.print("State\t");
        for (int i = 0; i < transitionTable[0].length; i++) {
            System.out.print("C" + i + "\t");
        }
        System.out.println("Accept");
        
        // Print table rows
        for (int state = 0; state < transitionTable.length; state++) {
            System.out.print(state + (state == startState ? "*" : "") + "\t");
            
            for (int classIdx = 0; classIdx < transitionTable[state].length; classIdx++) {
                int target = transitionTable[state][classIdx];
                System.out.print((target == -1 ? "-" : String.valueOf(target)) + "\t");
            }
            
            System.out.println(acceptingStates[state] ? "YES" : "NO");
        }
        
        // Print character to class mapping
        System.out.println("\nCharacter Classes:");
        Map<Integer, List<Character>> classToChars = new HashMap<>();
        for (Map.Entry<Character, Integer> entry : charToIndex.entrySet()) {
            classToChars.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        
        for (Map.Entry<Integer, List<Character>> entry : classToChars.entrySet()) {
            System.out.printf("Class %d: %s%n", entry.getKey(), entry.getValue());
        }
    }
}