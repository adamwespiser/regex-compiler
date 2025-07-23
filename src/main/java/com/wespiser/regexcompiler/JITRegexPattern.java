package com.wespiser.regexcompiler;

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * JIT bytecode compiler for regex patterns using ASM library.
 * Generates optimized JVM bytecode with LOOKUPSWITCH for maximum performance.
 */
class JITRegexPattern implements Pattern {
    private final String patternString;
    private final RegexToDFA.DFA originalDfa;
    private final JITCompatibleDFA compatibleDfa;
    private final Class<?> compiledClass;
    private final Object compiledInstance;
    private final Method matchesMethod;
    private final Method findMethod;

    /**
     * JIT-compatible DFA representation with integer state IDs and transition tables
     */
    static class JITCompatibleDFA {
        public final int startState;
        public final Set<Integer> states;
        public final Set<Integer> acceptingStates;
        public final Set<Character> alphabet;
        public final Map<Integer, Map<Character, Integer>> transitionTable;

        public JITCompatibleDFA(int startState, Set<Integer> states, Set<Integer> acceptingStates, 
                               Set<Character> alphabet, Map<Integer, Map<Character, Integer>> transitionTable) {
            this.startState = startState;
            this.states = states;
            this.acceptingStates = acceptingStates;
            this.alphabet = alphabet;
            this.transitionTable = transitionTable;
        }
    }

    public JITRegexPattern(String pattern) throws RegexCompileException {
        this.patternString = pattern;
        
        try {
            // Convert regex to optimized DFA
            RegexToDFA converter = new RegexToDFA();
            this.originalDfa = converter.convertRegexToDFAOptimized(pattern);
            
            // Convert to JIT-compatible format
            this.compatibleDfa = convertToJITCompatibleDFA(originalDfa);
            
            // Generate and compile bytecode
            String className = "GeneratedRegex_" + Math.abs(pattern.hashCode());
            byte[] bytecode = generateBytecode(className, compatibleDfa);
            
            // Load the generated class
            JITClassLoader classLoader = new JITClassLoader();
            this.compiledClass = classLoader.defineClass(className, bytecode);
            this.compiledInstance = compiledClass.getDeclaredConstructor().newInstance();
            
            // Get method references for fast invocation
            this.matchesMethod = compiledClass.getMethod("matches", String.class);
            this.findMethod = compiledClass.getMethod("find", String.class);
            
        } catch (Exception e) {
            throw new RegexCompileException("Failed to compile JIT regex: " + e.getMessage(), e);
        }
    }

    @Override
    public Pattern compile(String regex) throws RegexCompileException {
        // This implementation is already compiled in constructor
        return this;
    }

    @Override
    public boolean matches(String input) {
        try {
            return (Boolean) matchesMethod.invoke(compiledInstance, input);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("JIT execution failed", e);
        }
    }

    @Override
    public boolean find(String input) {
        try {
            return (Boolean) findMethod.invoke(compiledInstance, input);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("JIT execution failed", e);
        }
    }

    @Override
    public String pattern() {
        return patternString;
    }

    @Override
    public String getImplementationName() {
        return "JIT";
    }

    @Override
    public Map<String, Object> getCompilationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("patternString", patternString);
        stats.put("implementation", "JIT");
        stats.put("stateCount", compatibleDfa.states.size());
        stats.put("alphabetSize", compatibleDfa.alphabet.size());
        stats.put("compiledClass", compiledClass.getName());
        return stats;
    }

    /**
     * Converts the original DFA structure to JIT-compatible format with integer state IDs
     */
    private static JITCompatibleDFA convertToJITCompatibleDFA(RegexToDFA.DFA originalDfa) {
        // Create mapping from DFAState to integer ID
        Map<RegexToDFA.DFAState, Integer> stateToId = new HashMap<>();
        int nextId = 0;
        
        // Map start state to ID 0
        stateToId.put(originalDfa.start, nextId++);
        
        // Map all other states
        for (RegexToDFA.DFAState state : originalDfa.states) {
            if (!stateToId.containsKey(state)) {
                stateToId.put(state, nextId++);
            }
        }
        
        // Build integer state sets
        Set<Integer> states = new HashSet<>();
        Set<Integer> acceptingStates = new HashSet<>();
        
        for (RegexToDFA.DFAState state : originalDfa.states) {
            int stateId = stateToId.get(state);
            states.add(stateId);
            
            if (state.isAccepting) {
                acceptingStates.add(stateId);
            }
        }
        
        // Build transition table
        Map<Integer, Map<Character, Integer>> transitionTable = new HashMap<>();
        
        for (RegexToDFA.DFAState state : originalDfa.states) {
            int fromStateId = stateToId.get(state);
            Map<Character, Integer> transitions = new HashMap<>();
            
            for (Map.Entry<Character, RegexToDFA.DFAState> entry : state.transitions.entrySet()) {
                char c = entry.getKey();
                RegexToDFA.DFAState toState = entry.getValue();
                int toStateId = stateToId.get(toState);
                transitions.put(c, toStateId);
            }
            
            if (!transitions.isEmpty()) {
                transitionTable.put(fromStateId, transitions);
            }
        }
        
        int startStateId = stateToId.get(originalDfa.start);
        
        return new JITCompatibleDFA(startStateId, states, acceptingStates, 
                                   originalDfa.alphabet, transitionTable);
    }

    /**
     * Generates optimized JVM bytecode for the DFA using LOOKUPSWITCH
     */
    private byte[] generateBytecode(String className, JITCompatibleDFA dfa) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        
        // Generate class header
        cw.visit(V11, ACC_PUBLIC, className, null, "java/lang/Object", null);
        
        // Generate default constructor
        generateConstructor(cw);
        
        // Generate matches method
        generateMatchesMethod(cw, dfa);
        
        // Generate find method  
        generateFindMethod(cw, dfa);
        
        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateMatchesMethod(ClassWriter cw, JITCompatibleDFA dfa) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "matches", "(Ljava/lang/String;)Z", null, null);
        mv.visitCode();
        
        // Special case: if DFA has no transitions (empty alphabet), simplify logic
        if (dfa.alphabet.isEmpty() || dfa.transitionTable.isEmpty()) {
            generateTrivialMatchesMethod(mv, dfa);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        
        // boolean matches(String input) {
        //     if (input.length() == 0) return isAcceptingState(startState);
        //     int state = startState;
        //     for (int i = 0; i < input.length(); i++) {
        //         char c = input.charAt(i);
        //         state = transition(state, c);
        //         if (state == -1) return false; // dead state
        //     }
        //     return isAcceptingState(state);
        // }
        
        Label returnTrue = new Label();
        Label returnFalse = new Label();
        Label loopStart = new Label();
        Label loopCheck = new Label();
        
        // Check for empty string
        mv.visitVarInsn(ALOAD, 1); // load input
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        mv.visitJumpInsn(IFNE, loopCheck);
        
        // Empty string: return isAcceptingState(startState)
        if (dfa.acceptingStates.contains(dfa.startState)) {
            mv.visitLdcInsn(1);
        } else {
            mv.visitLdcInsn(0);
        }
        mv.visitInsn(IRETURN);
        
        // Initialize loop variables
        mv.visitLabel(loopCheck);
        mv.visitLdcInsn(dfa.startState); // state = startState
        mv.visitVarInsn(ISTORE, 2); // store state in local var 2
        mv.visitLdcInsn(0); // i = 0
        mv.visitVarInsn(ISTORE, 3); // store i in local var 3
        
        // Loop condition check
        mv.visitJumpInsn(GOTO, loopStart);
        
        Label loopBody = new Label();
        mv.visitLabel(loopBody);
        
        // char c = input.charAt(i)
        mv.visitVarInsn(ALOAD, 1); // load input
        mv.visitVarInsn(ILOAD, 3); // load i
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
        mv.visitVarInsn(ISTORE, 4); // store c in local var 4
        
        // state = transition(state, c) - generate LOOKUPSWITCH
        generateTransitionLookupSwitch(mv, dfa, 2, 4, returnFalse);
        
        // i++
        mv.visitIincInsn(3, 1);
        
        // Loop condition: i < input.length()
        mv.visitLabel(loopStart);
        mv.visitVarInsn(ILOAD, 3); // load i
        mv.visitVarInsn(ALOAD, 1); // load input
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        mv.visitJumpInsn(IF_ICMPLT, loopBody);
        
        // Return isAcceptingState(state)
        generateAcceptingStateCheck(mv, dfa, 2, returnTrue, returnFalse);
        
        mv.visitLabel(returnFalse);
        mv.visitLdcInsn(0);
        mv.visitInsn(IRETURN);
        
        mv.visitLabel(returnTrue);
        mv.visitLdcInsn(1);
        mv.visitInsn(IRETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateTrivialMatchesMethod(MethodVisitor mv, JITCompatibleDFA dfa) {
        // For empty alphabet: matches() only depends on input length and start state acceptance
        // boolean matches(String input) {
        //     return input.length() == 0 && isAcceptingState(startState);
        // }
        
        Label returnFalse = new Label();
        
        // Check if input is empty
        mv.visitVarInsn(ALOAD, 1); // load input
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        mv.visitJumpInsn(IFNE, returnFalse); // if length != 0, return false
        
        // Input is empty, return whether start state is accepting
        if (dfa.acceptingStates.contains(dfa.startState)) {
            mv.visitLdcInsn(1);
        } else {
            mv.visitLdcInsn(0);
        }
        mv.visitInsn(IRETURN);
        
        mv.visitLabel(returnFalse);
        mv.visitLdcInsn(0);
        mv.visitInsn(IRETURN);
    }

    private void generateFindMethod(ClassWriter cw, JITCompatibleDFA dfa) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "find", "(Ljava/lang/String;)Z", null, null);
        mv.visitCode();
        
        // Special case: if DFA has no transitions (empty alphabet), simplify logic
        if (dfa.alphabet.isEmpty() || dfa.transitionTable.isEmpty()) {
            generateTrivialFindMethod(mv, dfa);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        
        // boolean find(String input) {
        //     for (int start = 0; start <= input.length(); start++) {
        //         int state = startState;
        //         for (int i = start; i < input.length(); i++) {
        //             if (isAcceptingState(state)) return true;
        //             char c = input.charAt(i);
        //             state = transition(state, c);
        //             if (state == -1) break; // dead state
        //         }
        //         if (isAcceptingState(state)) return true;
        //     }
        //     return false;
        // }
        
        Label returnTrue = new Label();
        Label returnFalse = new Label();
        Label outerLoopStart = new Label();
        Label outerLoopCheck = new Label();
        Label innerLoopStart = new Label();
        Label innerLoopCheck = new Label();
        Label innerLoopEnd = new Label();
        
        // Outer loop: for (int start = 0; start <= input.length(); start++)
        mv.visitLdcInsn(0); // start = 0
        mv.visitVarInsn(ISTORE, 2); // store start in local var 2
        mv.visitJumpInsn(GOTO, outerLoopCheck);
        
        mv.visitLabel(outerLoopStart);
        
        // state = startState
        mv.visitLdcInsn(dfa.startState);
        mv.visitVarInsn(ISTORE, 3); // store state in local var 3
        
        // Inner loop: for (int i = start; i < input.length(); i++)
        mv.visitVarInsn(ILOAD, 2); // load start
        mv.visitVarInsn(ISTORE, 4); // i = start, store in local var 4
        mv.visitJumpInsn(GOTO, innerLoopCheck);
        
        mv.visitLabel(innerLoopStart);
        
        // if (isAcceptingState(state)) return true
        generateAcceptingStateCheck(mv, dfa, 3, returnTrue, null);
        
        // char c = input.charAt(i)
        mv.visitVarInsn(ALOAD, 1); // load input
        mv.visitVarInsn(ILOAD, 4); // load i
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
        mv.visitVarInsn(ISTORE, 5); // store c in local var 5
        
        // state = transition(state, c)
        generateTransitionLookupSwitch(mv, dfa, 3, 5, innerLoopEnd);
        
        // i++
        mv.visitIincInsn(4, 1);
        
        // Inner loop condition: i < input.length()
        mv.visitLabel(innerLoopCheck);
        mv.visitVarInsn(ILOAD, 4); // load i
        mv.visitVarInsn(ALOAD, 1); // load input
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        mv.visitJumpInsn(IF_ICMPLT, innerLoopStart);
        
        mv.visitLabel(innerLoopEnd);
        
        // Final accepting state check after inner loop
        generateAcceptingStateCheck(mv, dfa, 3, returnTrue, null);
        
        // start++
        mv.visitIincInsn(2, 1);
        
        // Outer loop condition: start <= input.length()
        mv.visitLabel(outerLoopCheck);
        mv.visitVarInsn(ILOAD, 2); // load start
        mv.visitVarInsn(ALOAD, 1); // load input
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        mv.visitJumpInsn(IF_ICMPLE, outerLoopStart);
        
        mv.visitLabel(returnFalse);
        mv.visitLdcInsn(0);
        mv.visitInsn(IRETURN);
        
        mv.visitLabel(returnTrue);
        mv.visitLdcInsn(1);
        mv.visitInsn(IRETURN);
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateTrivialFindMethod(MethodVisitor mv, JITCompatibleDFA dfa) {
        // For empty alphabet: find() only depends on start state acceptance
        // boolean find(String input) {
        //     return isAcceptingState(startState);
        // }
        
        if (dfa.acceptingStates.contains(dfa.startState)) {
            mv.visitLdcInsn(1);
        } else {
            mv.visitLdcInsn(0);
        }
        mv.visitInsn(IRETURN);
    }

    /**
     * Generates optimized LOOKUPSWITCH for state transitions
     */
    private void generateTransitionLookupSwitch(MethodVisitor mv, JITCompatibleDFA dfa, int stateVar, int charVar, Label deadStateLabel) {
        mv.visitVarInsn(ILOAD, stateVar); // load current state
        
        // Create state-specific lookup switches
        Map<Integer, Map<Character, Integer>> transitionMap = buildTransitionMap(dfa);
        
        if (transitionMap.isEmpty()) {
            mv.visitJumpInsn(GOTO, deadStateLabel);
            return;
        }
        
        // Generate nested switch: first on state, then on character
        List<Integer> stateKeys = new ArrayList<>(transitionMap.keySet());
        Collections.sort(stateKeys);
        
        int[] stateCases = stateKeys.stream().mapToInt(Integer::intValue).toArray();
        Label[] stateLabels = new Label[stateCases.length];
        Label defaultLabel = new Label();
        Label continueLabel = new Label(); // Label to continue after transition
        
        for (int i = 0; i < stateLabels.length; i++) {
            stateLabels[i] = new Label();
        }
        
        mv.visitLookupSwitchInsn(defaultLabel, stateCases, stateLabels);
        
        // Generate character switches for each state
        for (int i = 0; i < stateCases.length; i++) {
            mv.visitLabel(stateLabels[i]);
            int state = stateCases[i];
            Map<Character, Integer> charTransitions = transitionMap.get(state);
            
            if (charTransitions.isEmpty()) {
                mv.visitJumpInsn(GOTO, deadStateLabel);
                continue;
            }
            
            mv.visitVarInsn(ILOAD, charVar); // load character
            
            List<Character> charKeys = new ArrayList<>(charTransitions.keySet());
            Collections.sort(charKeys);
            
            int[] charCases = charKeys.stream().mapToInt(c -> (int) c).toArray();
            Label[] charLabels = new Label[charCases.length];
            
            for (int j = 0; j < charLabels.length; j++) {
                charLabels[j] = new Label();
            }
            
            mv.visitLookupSwitchInsn(defaultLabel, charCases, charLabels);
            
            // Generate state assignments for each character
            for (int j = 0; j < charCases.length; j++) {
                mv.visitLabel(charLabels[j]);
                char c = (char) charCases[j];
                int nextState = charTransitions.get(c);
                mv.visitLdcInsn(nextState);
                mv.visitVarInsn(ISTORE, stateVar); // store new state
                mv.visitJumpInsn(GOTO, continueLabel); // jump to continue execution
            }
        }
        
        // Default case: dead state
        mv.visitLabel(defaultLabel);
        mv.visitJumpInsn(GOTO, deadStateLabel);
        
        // Continue label - execution continues here after successful transition
        mv.visitLabel(continueLabel);
    }

    private void generateAcceptingStateCheck(MethodVisitor mv, JITCompatibleDFA dfa, int stateVar, Label trueLabel, Label falseLabel) {
        if (dfa.acceptingStates.isEmpty()) {
            if (falseLabel != null) {
                mv.visitJumpInsn(GOTO, falseLabel);
            }
            return;
        }
        
        mv.visitVarInsn(ILOAD, stateVar); // load state
        
        List<Integer> acceptingStates = new ArrayList<>(dfa.acceptingStates);
        Collections.sort(acceptingStates);
        
        int[] cases = acceptingStates.stream().mapToInt(Integer::intValue).toArray();
        Label[] labels = new Label[cases.length];
        
        for (int i = 0; i < labels.length; i++) {
            labels[i] = new Label();
        }
        
        Label defaultLabel = falseLabel != null ? falseLabel : new Label();
        mv.visitLookupSwitchInsn(defaultLabel, cases, labels);
        
        // All accepting state labels jump to true
        for (Label label : labels) {
            mv.visitLabel(label);
            mv.visitJumpInsn(GOTO, trueLabel);
        }
        
        if (falseLabel == null) {
            mv.visitLabel(defaultLabel);
        } else {
            mv.visitLabel(defaultLabel);
            mv.visitJumpInsn(GOTO, falseLabel);
        }
    }

    private Map<Integer, Map<Character, Integer>> buildTransitionMap(JITCompatibleDFA dfa) {
        // The compatible DFA already has the transition table in the right format
        return dfa.transitionTable;
    }

    /**
     * Custom class loader for dynamically generated bytecode
     */
    private static class JITClassLoader extends ClassLoader {
        public Class<?> defineClass(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }
}