#!/bin/bash

# Regex Compiler Benchmark Script
# ================================
# This script runs comprehensive benchmarks comparing DFA, Backtrack, Table, and JIT algorithms
# across different data sizes and outputs results to CSV for analysis.

set -e  # Exit on any error

echo "=================================="
echo "Regex Compiler Benchmark Suite"
echo "=================================="
echo

if [ ! -f "./mvn24" ]; then
    echo "Error: ./mvn24.sh script not found"
    exit 1
fi

if [ ! -x "./mvn24" ]; then
    echo "Error: ./mvn24.sh script is not executable"
    exit 1
fi

# Set JVM options for consistent benchmarking
export MAVEN_OPTS="-Xmx4g -Xms1g -XX:+UseG1GC"

echo "Step 1: Compiling project..."
./mvn24.sh compile -q
if [ $? -ne 0 ]; then
    echo "Error: Failed to compile project"
    exit 1
fi
echo "✓ Project compiled successfully"

echo
echo "Step 2: Generating test data..."
./mvn24 exec:java -Dexec.mainClass="com.wespiser.regexcompiler.BenchmarkDataGenerator" -q
if [ $? -ne 0 ]; then
    echo "Error: Failed to generate test data"
    exit 1
fi
echo "✓ Test data generated"

echo
echo "Step 3: Running benchmarks..."
echo "This may take several minutes depending on your system..."
echo

# Set specific JVM options for benchmarking
export JAVA_OPTS="-server -Xmx4g -Xms2g -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI"

start_time=$(date +%s)

./mvn24.sh exec:java -Dexec.mainClass="com.wespiser.regexcompiler.RegexBenchmark" -Dexec.args="" -q
benchmark_exit_code=$?

end_time=$(date +%s)
duration=$((end_time - start_time))

echo
if [ $benchmark_exit_code -eq 0 ]; then
    echo "✓ Benchmarks completed successfully!"
    echo "Total runtime: ${duration} seconds"
    
    # Check if results file was created
    if [ -f "benchmark_results.csv" ]; then
        echo
        echo "Results Summary:"
        echo "=================="
        
        # Count total lines (minus header)
        total_tests=$(($(wc -l < benchmark_results.csv) - 1))
        echo "Total test cases: $total_tests"
        
        # Count successful vs error cases
        success_count=$(tail -n +2 benchmark_results.csv | grep -c ",SUCCESS,")
        error_count=$(tail -n +2 benchmark_results.csv | grep -c ",ERROR,")
        
        echo "Successful tests: $success_count"
        echo "Failed tests: $error_count"
        echo
        echo "CSV file saved as: benchmark_results.csv"
        echo "File size: $(du -h benchmark_results.csv | cut -f1)"
        
        # Show first few lines as preview
        echo
        echo "Preview (first 5 data rows):"
        echo "----------------------------"
        head -n 6 benchmark_results.csv | column -t -s ','
        
    else
        echo "Warning: benchmark_results.csv was not created"
    fi
else
    echo "Error: Benchmarks failed with exit code $benchmark_exit_code"
    exit 1
fi

echo
echo "==========="
echo "Created benchmark_results.csv"

echo
echo "Benchmark complete! 🎉"