package com.wespiser.regexcompiler.algorithms;

import com.wespiser.regexcompiler.Pattern;
import com.wespiser.regexcompiler.RegexCompileException;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;


/**
 * JIT bytecode compiler for regex patterns using Java's Class-File API.
 * Generates optimized JVM bytecode with LOOKUPSWITCH for maximum performance.
 * 
 * The JIT algorithm converts the regex to an optimized DFA, then generates specialized JVM bytecode
 * using Java's built-in Class-File API for maximum runtime performance. It creates a custom Java class with hardcoded
 * state transitions using LOOKUPSWITCH instructions, which the JVM can optimize heavily. The generated
 * bytecode eliminates method call overhead and uses the most efficient JVM instructions for state
 * transitions. Runtime complexity is O(n) with extremely low constant factors due to JVM optimizations
 * like branch prediction and instruction-level parallelism. Space complexity is O(s×a) for the
 * transition logic plus bytecode overhead. The strategy achieves the fastest possible execution by
 * leveraging the JVM's sophisticated optimization capabilities, generating machine code that's
 * specifically tailored to the exact regex pattern being matched.
 */
public class JITRegexPattern implements Pattern {
    private final String patternString;
    private final RegexToDFA.DFA originalDfa;
    private final JITCompatibleDFA compatibleDfa;
    private final Class<?> compiledClass;
    private final Object compiledInstance;
    private final MethodHandle matchesHandle;
    private final MethodHandle findHandle;

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
            
            // Get MethodHandles for extremely fast invocation (much faster than reflection)
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType matchesType = MethodType.methodType(boolean.class, String.class);
            MethodType findType = MethodType.methodType(boolean.class, String.class);
            
            this.matchesHandle = lookup.findVirtual(compiledClass, "matches", matchesType)
                    .bindTo(compiledInstance);
            this.findHandle = lookup.findVirtual(compiledClass, "find", findType)
                    .bindTo(compiledInstance);
            
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
            return (boolean) matchesHandle.invokeExact(input);
        } catch (Throwable e) {
            throw new RuntimeException("JIT execution failed", e);
        }
    }

    @Override
    public boolean find(String input) {
        try {
            return (boolean) findHandle.invokeExact(input);
        } catch (Throwable e) {
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
        ClassDesc classDesc = ClassDesc.of(className);
        
        return ClassFile.of().build(classDesc, classBuilder -> {
            // Generate default constructor
            classBuilder.withMethod(INIT_NAME, MTD_void, ACC_PUBLIC, methodBuilder -> 
                methodBuilder.withCode(codeBuilder -> 
                    codeBuilder.aload(0)
                               .invokespecial(CD_Object, INIT_NAME, MTD_void)
                               .return_()
                )
            );
            
            // Generate matches method
            classBuilder.withMethod("matches", MethodTypeDesc.of(CD_boolean, CD_String), ACC_PUBLIC, 
                methodBuilder -> generateMatchesMethod(methodBuilder, dfa));
            
            // Generate find method
            classBuilder.withMethod("find", MethodTypeDesc.of(CD_boolean, CD_String), ACC_PUBLIC,
                methodBuilder -> generateFindMethod(methodBuilder, dfa));
        });
    }


    private void generateMatchesMethod(MethodBuilder methodBuilder, JITCompatibleDFA dfa) {
        methodBuilder.withCode(codeBuilder -> {
            // Special case: if DFA has no transitions (empty alphabet), simplify logic
            if (dfa.alphabet.isEmpty() || dfa.transitionTable.isEmpty()) {
                generateTrivialMatchesMethod(codeBuilder, dfa);
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
            
            Label returnTrue = codeBuilder.newLabel();
            Label returnFalse = codeBuilder.newLabel();
            Label loopStart = codeBuilder.newLabel();
            Label loopCheck = codeBuilder.newLabel();
            
            // Check for empty string
            codeBuilder.aload(1) // load input
                       .invokevirtual(CD_String, "length", MethodTypeDesc.of(CD_int))
                       .ifne(loopCheck);
            
            // Empty string: return isAcceptingState(startState)
            if (dfa.acceptingStates.contains(dfa.startState)) {
                codeBuilder.iconst_1();
            } else {
                codeBuilder.iconst_0();
            }
            codeBuilder.ireturn();
            
            // Initialize loop variables
            codeBuilder.labelBinding(loopCheck)
                       .ldc(dfa.startState) // state = startState
                       .istore(2) // store state in local var 2
                       .iconst_0() // i = 0
                       .istore(3) // store i in local var 3
                       .goto_(loopStart);
            
            Label loopBody = codeBuilder.newLabel();
            codeBuilder.labelBinding(loopBody);
            
            // char c = input.charAt(i)
            codeBuilder.aload(1) // load input
                       .iload(3) // load i
                       .invokevirtual(CD_String, "charAt", MethodTypeDesc.of(CD_char, CD_int))
                       .istore(4); // store c in local var 4
            
            // state = transition(state, c) - generate LOOKUPSWITCH
            generateTransitionLookupSwitch(codeBuilder, dfa, 2, 4, returnFalse);
            
            // i++
            codeBuilder.iinc(3, 1);
            
            // Loop condition: i < input.length()
            codeBuilder.labelBinding(loopStart)
                       .iload(3) // load i
                       .aload(1) // load input
                       .invokevirtual(CD_String, "length", MethodTypeDesc.of(CD_int))
                       .if_icmplt(loopBody);
            
            // Return isAcceptingState(state)
            generateAcceptingStateCheck(codeBuilder, dfa, 2, returnTrue, returnFalse);
            
            codeBuilder.labelBinding(returnFalse)
                       .iconst_0()
                       .ireturn();
            
            codeBuilder.labelBinding(returnTrue)
                       .iconst_1()
                       .ireturn();
        });
    }

    private void generateTrivialMatchesMethod(CodeBuilder codeBuilder, JITCompatibleDFA dfa) {
        // For empty alphabet: matches() only depends on input length and start state acceptance
        // boolean matches(String input) {
        //     return input.length() == 0 && isAcceptingState(startState);
        // }
        
        Label returnFalse = codeBuilder.newLabel();
        
        // Check if input is empty
        codeBuilder.aload(1) // load input
                   .invokevirtual(CD_String, "length", MethodTypeDesc.of(CD_int))
                   .ifne(returnFalse); // if length != 0, return false
        
        // Input is empty, return whether start state is accepting
        if (dfa.acceptingStates.contains(dfa.startState)) {
            codeBuilder.iconst_1();
        } else {
            codeBuilder.iconst_0();
        }
        codeBuilder.ireturn();
        
        codeBuilder.labelBinding(returnFalse)
                   .iconst_0()
                   .ireturn();
    }

    private void generateFindMethod(MethodBuilder methodBuilder, JITCompatibleDFA dfa) {
        methodBuilder.withCode(codeBuilder -> {
        
            // Special case: if DFA has no transitions (empty alphabet), simplify logic
            if (dfa.alphabet.isEmpty() || dfa.transitionTable.isEmpty()) {
                generateTrivialFindMethod(codeBuilder, dfa);
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
            
            Label returnTrue = codeBuilder.newLabel();
            Label returnFalse = codeBuilder.newLabel();
            Label outerLoopStart = codeBuilder.newLabel();
            Label outerLoopCheck = codeBuilder.newLabel();
            Label innerLoopStart = codeBuilder.newLabel();
            Label innerLoopCheck = codeBuilder.newLabel();
            Label innerLoopEnd = codeBuilder.newLabel();
            
            // Outer loop: for (int start = 0; start <= input.length(); start++)
            codeBuilder.iconst_0() // start = 0
                       .istore(2) // store start in local var 2
                       .goto_(outerLoopCheck);
            
            codeBuilder.labelBinding(outerLoopStart);
            
            // state = startState
            codeBuilder.ldc(dfa.startState)
                       .istore(3); // store state in local var 3
            
            // Inner loop: for (int i = start; i < input.length(); i++)
            codeBuilder.iload(2) // load start
                       .istore(4) // i = start, store in local var 4
                       .goto_(innerLoopCheck);
            
            codeBuilder.labelBinding(innerLoopStart);
            
            // if (isAcceptingState(state)) return true
            generateAcceptingStateCheck(codeBuilder, dfa, 3, returnTrue, null);
            
            // char c = input.charAt(i)
            codeBuilder.aload(1) // load input
                       .iload(4) // load i
                       .invokevirtual(CD_String, "charAt", MethodTypeDesc.of(CD_char, CD_int))
                       .istore(5); // store c in local var 5
            
            // state = transition(state, c)
            generateTransitionLookupSwitch(codeBuilder, dfa, 3, 5, innerLoopEnd);
            
            // i++
            codeBuilder.iinc(4, 1);
            
            // Inner loop condition: i < input.length()
            codeBuilder.labelBinding(innerLoopCheck)
                       .iload(4) // load i
                       .aload(1) // load input
                       .invokevirtual(CD_String, "length", MethodTypeDesc.of(CD_int))
                       .if_icmplt(innerLoopStart);
            
            codeBuilder.labelBinding(innerLoopEnd);
            
            // Final accepting state check after inner loop
            generateAcceptingStateCheck(codeBuilder, dfa, 3, returnTrue, null);
            
            // start++
            codeBuilder.iinc(2, 1);
            
            // Outer loop condition: start <= input.length()
            codeBuilder.labelBinding(outerLoopCheck)
                       .iload(2) // load start
                       .aload(1) // load input
                       .invokevirtual(CD_String, "length", MethodTypeDesc.of(CD_int))
                       .if_icmple(outerLoopStart);
            
            codeBuilder.labelBinding(returnFalse)
                       .iconst_0()
                       .ireturn();
            
            codeBuilder.labelBinding(returnTrue)
                       .iconst_1()
                       .ireturn();
        });
    }

    private void generateTrivialFindMethod(CodeBuilder codeBuilder, JITCompatibleDFA dfa) {
        // For empty alphabet: find() only depends on start state acceptance
        // boolean find(String input) {
        //     return isAcceptingState(startState);
        // }
        
        if (dfa.acceptingStates.contains(dfa.startState)) {
            codeBuilder.iconst_1();
        } else {
            codeBuilder.iconst_0();
        }
        codeBuilder.ireturn();
    }

    /**
     * Generates optimized LOOKUPSWITCH for state transitions
     */
    private void generateTransitionLookupSwitch(CodeBuilder codeBuilder, JITCompatibleDFA dfa, int stateVar, int charVar, Label deadStateLabel) {
        codeBuilder.iload(stateVar); // load current state
        
        // Create state-specific lookup switches
        Map<Integer, Map<Character, Integer>> transitionMap = buildTransitionMap(dfa);
        
        if (transitionMap.isEmpty()) {
            codeBuilder.goto_(deadStateLabel);
            return;
        }
        
        // Generate nested switch: first on state, then on character
        List<Integer> stateKeys = new ArrayList<>(transitionMap.keySet());
        Collections.sort(stateKeys);
        
        int[] stateCases = stateKeys.stream().mapToInt(Integer::intValue).toArray();
        Label stateDefaultLabel = codeBuilder.newLabel(); // Default case for state switch
        Label continueLabel = codeBuilder.newLabel(); // Label to continue after transition
        
        SwitchCase[] stateSwitch = new SwitchCase[stateCases.length];
        for (int i = 0; i < stateCases.length; i++) {
            stateSwitch[i] = SwitchCase.of(stateCases[i], codeBuilder.newLabel());
        }

        codeBuilder.lookupswitch(stateDefaultLabel, Arrays.asList(stateSwitch));
        
        // Generate character switches for each state
        for (int i = 0; i < stateSwitch.length; i++) {
            codeBuilder.labelBinding(stateSwitch[i].target());
            int state = stateSwitch[i].caseValue();
            Map<Character, Integer> charTransitions = transitionMap.get(state);
            
            if (charTransitions.isEmpty()) {
                codeBuilder.goto_(deadStateLabel);
                continue;
            }
            
            codeBuilder.iload(charVar); // load character
            
            List<Character> charKeys = new ArrayList<>(charTransitions.keySet());
            Collections.sort(charKeys);
            
            int[] charCases = charKeys.stream().mapToInt(c -> (int) c).toArray();
            SwitchCase[] charLabels = new SwitchCase[charCases.length];
            
            for (int j = 0; j < charLabels.length; j++) {
                charLabels[j] = SwitchCase.of(charCases[j], codeBuilder.newLabel());
            }
            
            // Each character switch needs its own default label
            Label charDefaultLabel = codeBuilder.newLabel();
            codeBuilder.lookupswitch(charDefaultLabel, Arrays.asList(charLabels));

            // Generate state assignments for each character
            for (int j = 0; j < charCases.length; j++) {
                codeBuilder.labelBinding(charLabels[j].target());
                char c = (char) charCases[j];
                int nextState = charTransitions.get(c);
                codeBuilder.ldc(nextState);
                codeBuilder.istore(stateVar);
                codeBuilder.goto_(continueLabel);
            }
            
            // Character default case: dead state
            codeBuilder.labelBinding(charDefaultLabel);
            codeBuilder.goto_(deadStateLabel);
        }
        
        // State default case: dead state
        codeBuilder.labelBinding(stateDefaultLabel);
        codeBuilder.goto_(deadStateLabel);

        // Continue label - execution continues here after successful transition
        codeBuilder.labelBinding(continueLabel);
    }

    private void generateAcceptingStateCheck(CodeBuilder codeBuilder, JITCompatibleDFA dfa, int stateVar, Label trueLabel, Label falseLabel) {
        if (dfa.acceptingStates.isEmpty()) {
            if (falseLabel != null) {
                codeBuilder.goto_(falseLabel);
            }
            return;
        }

        codeBuilder.iload(stateVar); // load state
        
        List<Integer> acceptingStates = new ArrayList<>(dfa.acceptingStates);
        Collections.sort(acceptingStates);
        
        int[] cases = acceptingStates.stream().mapToInt(Integer::intValue).toArray();
        SwitchCase[] labels = new SwitchCase[cases.length];

        for (int i = 0; i < labels.length; i++) {
            labels[i] = SwitchCase.of(cases[i], codeBuilder.newLabel());
        }
        
        Label defaultLabel;
        if (falseLabel != null) {
            defaultLabel = falseLabel;
        } else {
            defaultLabel = codeBuilder.newLabel();
        }
        
        codeBuilder.lookupswitch(defaultLabel, Arrays.asList(labels));
        
        // All accepting state labels jump to true
        for (SwitchCase switchCase : labels) {
            codeBuilder.labelBinding(switchCase.target());
            codeBuilder.goto_(trueLabel);
        }
        
        // Only bind the defaultLabel if we created it (falseLabel == null)
        if (falseLabel == null) {
            codeBuilder.labelBinding(defaultLabel);
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