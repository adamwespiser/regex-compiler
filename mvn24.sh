#!/bin/bash
# mvn24 - Maven wrapper script for Java 24
# 
# This script forces Maven to use Java 24 from Homebrew
# regardless of system JAVA_HOME or jenv settings

# Set Java 24 home
export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home

# Add Java 24 to PATH (prioritize it)
export PATH=$JAVA_HOME/bin:$PATH

# Maven installation directory
MAVEN_HOME=/usr/local/Cellar/maven/3.9.4/libexec

# Run Maven using Java 24 explicitly
exec $JAVA_HOME/bin/java \
  --enable-native-access=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  -classpath $MAVEN_HOME/boot/plexus-classworlds-2.7.0.jar \
  -Dclassworlds.conf=$MAVEN_HOME/bin/m2.conf \
  -Dmaven.home=$MAVEN_HOME \
  -Dmaven.multiModuleProjectDirectory="$PWD" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
