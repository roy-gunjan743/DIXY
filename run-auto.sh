#!/bin/bash
# Automatically find the first JAR file in the current directory or target folder
JAR_FILE=$(find . -maxdepth 2 -name "*.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ Error: No .jar file detected automatically in the directory."
    exit 1
fi

echo "🔍 Detected JAR file: $JAR_FILE"
echo "🚀 Starting DIXY deadlock, thread, and pinning detection..."

# Execute using the existing run script or java agent command
./run.sh "$JAR_FILE"