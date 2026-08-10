#!/bin/bash
echo "🚀 Downloading DIXY Deadlock & Pinning Detector..."

# Clone or download the repository logic (or fetch latest release assets)
# Assuming a standard setup where the script runs inside the project root:
if [ ! -f "run-auto.sh" ]; then
    echo "📦 Setting up automation scripts..."
    # Example fetching from your repo if needed:
    # curl -sSL https://raw.githubusercontent.com/your-username/DIXY/main/run-auto.sh -o run-auto.sh
    chmod +x run-auto.sh
fi

# Automatically locate JAR and execute
./run-auto.sh