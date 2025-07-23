package com.wespiser.regexcompiler;

import java.util.*;

/**
 * Converts regular expressions to DFAs using Thompson's construction (Regex → NFA)
 * followed by subset construction (NFA → DFA).
 */
public class RegexToDFA {
    
    // ============================================
    // NFA CLASSES
    // ============================================
    
    public static class NFAState {
        private static int nextId = 0;
        public final int id;
        public Map<Character, Set<NFAState>> transitions;
        public Set<NFAState> epsilonTransitions;
        public boolean isAccepting;
        
        public NFAState() {
            this.id = nextId++;
            this.transitions = new HashMap<>();
            this.epsilonTransitions = new HashSet<>();
            this.isAccepting = false;
        }
        
        public void addTransition(char c, NFAState target) {
            transitions.computeIfAbsent(c, k -> new HashSet<>()).add(target);
        }
        
        public void addEpsilonTransition(NFAState target) {
            epsilonTransitions.add(target);
        }
        
        @Override
        public String toString() {
            return "q" + id + (isAccepting ? " (accepting)" : "");
        }
    }
    
    public static class NFA {
        public final NFAState start;
        public final NFAState accept;
        
        public NFA(NFAState start, NFAState accept) {
            this.start = start;
            this.accept = accept;
        }
    }
    
    // ============================================
    // DFA CLASSES
    // ============================================
    
    public static class DFAState {
        private static int nextId = 0;
        public final int id;
        public Map<Character, DFAState> transitions;
        public boolean isAccepting;
        public Set<NFAState> nfaStates; // For construction - which NFA states this represents
        
        public DFAState(Set<NFAState> nfaStates) {
            this.id = nextId++;
            this.transitions = new HashMap<>();
            this.nfaStates = new HashSet<>(nfaStates);
            this.isAccepting = nfaStates.stream().anyMatch(s -> s.isAccepting);
        }
        
        public void addTransition(char c, DFAState target) {
            transitions.put(c, target);
        }
        
        @Override
        public String toString() {
            return "D" + id + (isAccepting ? " (accepting)" : "");
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof DFAState)) return false;
            DFAState other = (DFAState) obj;
            return nfaStates.equals(other.nfaStates);
        }
        
        @Override
        public int hashCode() {
            return nfaStates.hashCode();
        }
    }
    
    public static class DFA {
        public final DFAState start;
        public final Set<DFAState> states;
        public final Set<Character> alphabet;
        
        public DFA(DFAState start, Set<DFAState> states, Set<Character> alphabet) {
            this.start = start;
            this.states = states;
            this.alphabet = alphabet;
        }
        
        public boolean accepts(String input) {
            DFAState current = start;
            
            for (char c : input.toCharArray()) {
                current = current.transitions.get(c);
                if (current == null) {
                    return false; // No transition defined
                }
            }
            
            return current.isAccepting;
        }
        
        public void printDFA() {
            System.out.println("DFA States: " + states.size());
            System.out.println("Start state: " + start);
            System.out.println("Alphabet: " + alphabet);
            System.out.println("\nTransitions:");
            
            for (DFAState state : states) {
                for (Map.Entry<Character, DFAState> entry : state.transitions.entrySet()) {
                    System.out.println("  " + state + " --" + entry.getKey() + "--> " + entry.getValue());
                }
            }
            
            System.out.println("\nAccepting states:");
            states.stream()
                  .filter(s -> s.isAccepting)
                  .forEach(s -> System.out.println("  " + s));
        }
        
        public void printTransitionTable() {
            System.out.println("\nTransition Table:");
            System.out.print("State\t");
            for (char c : alphabet) {
                System.out.print(c + "\t");
            }
            System.out.println();
            
            for (DFAState state : states) {
                System.out.print(state + "\t");
                for (char c : alphabet) {
                    DFAState target = state.transitions.get(c);
                    System.out.print((target != null ? target.toString() : "-") + "\t");
                }
                System.out.println();
            }
        }
    }
    
    // ============================================
    // CONVERSION IMPLEMENTATION
    // ============================================
    
    /**
     * Main entry point: Convert regex string to DFA
     */
    public DFA convertRegexToDFA(String regex) throws Parser.RegexParseException {
        Parser parser = new Parser();
        Parser.RegexNode ast = parser.parse(regex);
        NFA nfa = regexToNFA(ast);
        return nfaToDFA(nfa);
    }
    
    /**
     * Convert regex AST to NFA using Thompson's Construction
     */
    public NFA regexToNFA(Parser.RegexNode node) {
        if (node instanceof Parser.CharNode) {
            return buildCharNFA((Parser.CharNode) node);
        } else if (node instanceof Parser.ConcatNode) {
            return buildConcatNFA((Parser.ConcatNode) node);
        } else if (node instanceof Parser.AlternationNode) {
            return buildAlternationNFA((Parser.AlternationNode) node);
        } else if (node instanceof Parser.StarNode) {
            return buildStarNFA((Parser.StarNode) node);
        } else if (node instanceof Parser.PlusNode) {
            return buildPlusNFA((Parser.PlusNode) node);
        } else if (node instanceof Parser.OptionalNode) {
            return buildOptionalNFA((Parser.OptionalNode) node);
        } else if (node instanceof Parser.EpsilonNode) {
            return buildEpsilonNFA();
        }
        
        throw new IllegalArgumentException("Unknown node type: " + node.getClass());
    }
    
    private NFA buildCharNFA(Parser.CharNode node) {
        NFAState start = new NFAState();
        NFAState accept = new NFAState();
        accept.isAccepting = true;
        
        start.addTransition(node.c, accept);
        return new NFA(start, accept);
    }
    
    private NFA buildConcatNFA(Parser.ConcatNode node) {
        NFA left = regexToNFA(node.left);
        NFA right = regexToNFA(node.right);
        
        // Connect left accept to right start with epsilon
        left.accept.isAccepting = false;
        left.accept.addEpsilonTransition(right.start);
        
        return new NFA(left.start, right.accept);
    }
    
    private NFA buildAlternationNFA(Parser.AlternationNode node) {
        NFA left = regexToNFA(node.left);
        NFA right = regexToNFA(node.right);
        
        NFAState start = new NFAState();
        NFAState accept = new NFAState();
        accept.isAccepting = true;
        
        // Epsilon transitions to both alternatives
        start.addEpsilonTransition(left.start);
        start.addEpsilonTransition(right.start);
        
        // Both alternatives go to common accept state
        left.accept.isAccepting = false;
        right.accept.isAccepting = false;
        left.accept.addEpsilonTransition(accept);
        right.accept.addEpsilonTransition(accept);
        
        return new NFA(start, accept);
    }
    
    private NFA buildStarNFA(Parser.StarNode node) {
        NFA child = regexToNFA(node.child);
        
        NFAState start = new NFAState();
        NFAState accept = new NFAState();
        accept.isAccepting = true;
        
        // Epsilon transition to skip (zero matches)
        start.addEpsilonTransition(accept);
        
        // Epsilon transition to child
        start.addEpsilonTransition(child.start);
        
        // Loop back from child accept to child start
        child.accept.isAccepting = false;
        child.accept.addEpsilonTransition(child.start);
        child.accept.addEpsilonTransition(accept);
        
        return new NFA(start, accept);
    }
    
    private NFA buildPlusNFA(Parser.PlusNode node) {
        // a+ = aa*
        NFA child = regexToNFA(node.child);
        NFA star = buildStarNFA(new Parser.StarNode(node.child));
        
        // Connect child to star
        child.accept.isAccepting = false;
        child.accept.addEpsilonTransition(star.start);
        
        return new NFA(child.start, star.accept);
    }
    
    private NFA buildOptionalNFA(Parser.OptionalNode node) {
        NFA child = regexToNFA(node.child);
        
        NFAState start = new NFAState();
        NFAState accept = new NFAState();
        accept.isAccepting = true;
        
        // Epsilon transition to skip
        start.addEpsilonTransition(accept);
        
        // Epsilon transition to child
        start.addEpsilonTransition(child.start);
        
        // Child accept to final accept
        child.accept.isAccepting = false;
        child.accept.addEpsilonTransition(accept);
        
        return new NFA(start, accept);
    }
    
    private NFA buildEpsilonNFA() {
        NFAState start = new NFAState();
        NFAState accept = new NFAState();
        accept.isAccepting = true;
        
        start.addEpsilonTransition(accept);
        return new NFA(start, accept);
    }
    
    /**
     * Convert NFA to DFA using Subset Construction
     */
    public DFA nfaToDFA(NFA nfa) {
        Set<Character> alphabet = computeAlphabet(nfa);
        Map<Set<NFAState>, DFAState> stateMap = new HashMap<>();
        Queue<Set<NFAState>> worklist = new ArrayDeque<>();
        Set<DFAState> dfaStates = new HashSet<>();
        
        // Start with epsilon closure of NFA start state
        Set<NFAState> startClosure = epsilonClosure(Set.of(nfa.start));
        DFAState startState = new DFAState(startClosure);
        stateMap.put(startClosure, startState);
        worklist.offer(startClosure);
        dfaStates.add(startState);
        
        while (!worklist.isEmpty()) {
            Set<NFAState> currentSet = worklist.poll();
            DFAState currentDFAState = stateMap.get(currentSet);
            
            for (char c : alphabet) {
                Set<NFAState> nextSet = epsilonClosure(move(currentSet, c));
                
                if (!nextSet.isEmpty()) {
                    DFAState nextDFAState = stateMap.get(nextSet);
                    
                    if (nextDFAState == null) {
                        nextDFAState = new DFAState(nextSet);
                        stateMap.put(nextSet, nextDFAState);
                        worklist.offer(nextSet);
                        dfaStates.add(nextDFAState);
                    }
                    
                    currentDFAState.addTransition(c, nextDFAState);
                }
            }
        }
        
        return new DFA(startState, dfaStates, alphabet);
    }
    
    // ============================================
    // HELPER METHODS
    // ============================================
    
    /**
     * Compute epsilon closure of a set of states
     */
    private Set<NFAState> epsilonClosure(Set<NFAState> states) {
        Set<NFAState> closure = new HashSet<>(states);
        Stack<NFAState> stack = new Stack<>();
        stack.addAll(states);
        
        while (!stack.isEmpty()) {
            NFAState state = stack.pop();
            for (NFAState target : state.epsilonTransitions) {
                if (!closure.contains(target)) {
                    closure.add(target);
                    stack.push(target);
                }
            }
        }
        
        return closure;
    }
    
    /**
     * Move function - states reachable on character c
     */
    private Set<NFAState> move(Set<NFAState> states, char c) {
        Set<NFAState> result = new HashSet<>();
        for (NFAState state : states) {
            Set<NFAState> targets = state.transitions.get(c);
            if (targets != null) {
                result.addAll(targets);
            }
        }
        return result;
    }
    
    /**
     * Compute alphabet from NFA
     */
    private Set<Character> computeAlphabet(NFA nfa) {
        Set<Character> alphabet = new HashSet<>();
        Set<NFAState> visited = new HashSet<>();
        Stack<NFAState> stack = new Stack<>();
        
        stack.push(nfa.start);
        
        while (!stack.isEmpty()) {
            NFAState state = stack.pop();
            if (visited.contains(state)) continue;
            visited.add(state);
            
            alphabet.addAll(state.transitions.keySet());
            
            for (Set<NFAState> targets : state.transitions.values()) {
                for (NFAState target : targets) {
                    if (!visited.contains(target)) {
                        stack.push(target);
                    }
                }
            }
            
            for (NFAState target : state.epsilonTransitions) {
                if (!visited.contains(target)) {
                    stack.push(target);
                }
            }
        }
        
        return alphabet;
    }
    
    // ============================================
    // DFA OPTIMIZATION METHODS
    // ============================================
    
    /**
     * Convert regex to optimized DFA with all optimizations applied
     */
    public DFA convertRegexToDFAOptimized(String regex) throws Parser.RegexParseException {
        // First, create the basic DFA
        DFA dfa = convertRegexToDFA(regex);
        
        // Apply optimizations in sequence
        dfa = removeUnreachableStates(dfa);
        dfa = removeDeadStates(dfa);
        dfa = minimizeDFA(dfa);
        
        return dfa;
    }
    
    /**
     * Remove states that cannot be reached from the start state
     */
    public DFA removeUnreachableStates(DFA dfa) {
        Set<DFAState> reachable = new HashSet<>();
        Queue<DFAState> queue = new ArrayDeque<>();
        
        queue.offer(dfa.start);
        reachable.add(dfa.start);
        
        while (!queue.isEmpty()) {
            DFAState current = queue.poll();
            
            for (DFAState target : current.transitions.values()) {
                if (!reachable.contains(target)) {
                    reachable.add(target);
                    queue.offer(target);
                }
            }
        }
        
        return new DFA(dfa.start, reachable, dfa.alphabet);
    }
    
    /**
     * Remove dead states (states from which no accepting state is reachable)
     */
    public DFA removeDeadStates(DFA dfa) {
        Set<DFAState> liveStates = new HashSet<>();
        Queue<DFAState> queue = new ArrayDeque<>();
        
        // Start with accepting states
        for (DFAState state : dfa.states) {
            if (state.isAccepting) {
                queue.offer(state);
                liveStates.add(state);
            }
        }
        
        // Work backwards to find all states that can reach accepting states
        boolean changed = true;
        while (changed) {
            changed = false;
            for (DFAState state : dfa.states) {
                if (!liveStates.contains(state)) {
                    for (DFAState target : state.transitions.values()) {
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
        
        return new DFA(dfa.start, liveStates, dfa.alphabet);
    }
    
    /**
     * Minimize DFA using Hopcroft's algorithm
     */
    public DFA minimizeDFA(DFA dfa) {
        // Initial partition: accepting vs non-accepting
        List<Set<DFAState>> partition = new ArrayList<>();
        Set<DFAState> accepting = new HashSet<>();
        Set<DFAState> nonAccepting = new HashSet<>();
        
        for (DFAState state : dfa.states) {
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
            List<Set<DFAState>> newPartition = new ArrayList<>();
            
            for (Set<DFAState> group : partition) {
                if (group.size() <= 1) {
                    newPartition.add(group);
                    continue;
                }
                
                // Try to split this group based on transition behavior
                Map<String, Set<DFAState>> signatures = new HashMap<>();
                
                for (DFAState state : group) {
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
    private String computeStateSignature(DFAState state, 
                                       List<Set<DFAState>> partition,
                                       Set<Character> alphabet) {
        StringBuilder sig = new StringBuilder();
        
        for (Character c : alphabet) {
            DFAState target = state.transitions.get(c);
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
    private DFA buildMinimizedDFA(DFA original, 
                                List<Set<DFAState>> partition) {
        // Create mapping from old states to their partition
        Map<DFAState, Set<DFAState>> stateToPartition = new HashMap<>();
        for (Set<DFAState> part : partition) {
            for (DFAState state : part) {
                stateToPartition.put(state, part);
            }
        }
        
        // Create one new DFA state per partition
        Map<Set<DFAState>, DFAState> partitionToNewState = new HashMap<>();
        
        for (Set<DFAState> part : partition) {
            // Collect all NFA states from all DFA states in this partition
            Set<NFAState> combinedNFAStates = new HashSet<>();
            boolean isAccepting = false;
            
            for (DFAState state : part) {
                if (state.nfaStates != null) {
                    combinedNFAStates.addAll(state.nfaStates);
                }
                if (state.isAccepting) {
                    isAccepting = true;
                }
            }
            
            // Create new DFA state representing this partition
            DFAState newState = new DFAState(combinedNFAStates);
            newState.isAccepting = isAccepting;
            partitionToNewState.put(part, newState);
        }
        
        // Build transitions for new states
        for (Map.Entry<Set<DFAState>, DFAState> entry : partitionToNewState.entrySet()) {
            Set<DFAState> sourcePartition = entry.getKey();
            DFAState newState = entry.getValue();
            
            // Use transitions from any state in the partition (they should all be equivalent)
            DFAState representative = sourcePartition.iterator().next();
            
            for (Map.Entry<Character, DFAState> trans : representative.transitions.entrySet()) {
                Character c = trans.getKey();
                DFAState oldTarget = trans.getValue();
                
                // Find which partition the target belongs to
                Set<DFAState> targetPartition = stateToPartition.get(oldTarget);
                if (targetPartition != null) {
                    DFAState newTarget = partitionToNewState.get(targetPartition);
                    if (newTarget != null) {
                        newState.addTransition(c, newTarget);
                    }
                }
            }
        }
        
        // Find new start state
        Set<DFAState> startPartition = stateToPartition.get(original.start);
        DFAState newStart = partitionToNewState.get(startPartition);
        
        if (newStart == null) {
            // Fallback if something went wrong - should not happen if partition is correct
            System.err.println("Warning: Could not find start state in minimized DFA, returning original");
            return original;
        }
        
        return new DFA(newStart, new HashSet<>(partitionToNewState.values()), original.alphabet);
    }
}