# regex-compiler

A comparative regex evaluation project in Java

### To build

```
jenv global 21.0.1
mvn clean compile test-compile
```

### Run battery of tests

```
mvn clean compile test-compile test
```

### Test individual files

```
mvn exec:java -Dexec.mainClass="com.example.regexcompiler.RegexParser"
mvn exec:java -Dexec.mainClass="com.example.regexcompiler.RegexToDFA"
```

### Performance Test

```
mvn clean compile test-compile
mvn exec:java -Dexec.mainClass="com.example.regexcompiler.RegexPerformanceTest" -Dexec.classpathScope="test" -X
```

### Performance Results

```
Performance ranking (lower is better):
  Table: 13.5 ns/match
  DFA: 14.1 ns/match
  Backtrack: 16.7 ns/match
```