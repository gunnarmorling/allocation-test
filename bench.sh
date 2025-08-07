#!/bin/bash

source "$HOME/.sdkman/bin/sdkman-init.sh"

set -e
set -o pipefail
set -x

# Default values
DEFAULT_DURATION=30
DEFAULT_MEM=4096
DEFAULT_THREADS=4

# Function to show usage
usage() {
    echo "Usage: $0 [--duration DURATION] [--mem MEM] [--threads THREADS]"
    echo "  --duration DURATION  Duration in seconds (default: $DEFAULT_DURATION)"
    echo "  --mem MEM            Memory in MB (default: $DEFAULT_MEM)"
    echo "  --threads THREADS    Thread count (default: $DEFAULT_THREADS)"
    echo "  -h, --help           Show this help message"
    exit 1
}

# Parse command line arguments
DURATION=$DEFAULT_DURATION
BENCH=$DEFAULT_BENCH
MEM=$DEFAULT_MEM
THREADS=$DEFAULT_THREADS

while [[ $# -gt 0 ]]; do
    case $1 in
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --threads)
            THREADS="$2"
            shift 2
            ;;
        --mem)
            MEM="$2"
            shift 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Unknown parameter: $1"
            usage
            ;;
    esac
done

# Validate duration is a number
if ! [[ "$DURATION" =~ ^[0-9]+$ ]]; then
    echo "Error: Duration must be a positive integer"
    exit 1
fi

# Validate memory is a number
if ! [[ "$MEM" =~ ^[0-9]+$ ]]; then
    echo "Error: Memory must be a positive integer"
    exit 1
fi

# Validate threads is a number
if ! [[ "$THREADS" =~ ^[0-9]+$ ]]; then
    echo "Error: Threads must be a positive integer"
    exit 1
fi

echo "Running with:"
echo "  Duration: $DURATION"
echo "  Memory: $MEMm"
echo "  Threads: $THREADS"

GCS=("Parallel" "Z" "G1" "Z")
JAVA_VERSIONS=("8.0.462-amzn" "21.0.6-tem" "24.0.2-open" "24.0.2-open")

# GCS=("Z" "G1" "Z")
# JAVA_VERSIONS=("21.0.6-tem" "24.0.2-open" "24.0.2-open")

# GCS=("Z")
# JAVA_VERSIONS=("21.0.6-tem")

export THREADS=$THREADS
export DURATION=$DURATION
for i in "${!GCS[@]}"; do
    gc="${GCS[i]}"
    java_version="${JAVA_VERSIONS[i]}"
    echo "Processing GC: $gc, Java Version: $java_version"

    sdk use java $java_version
    name=$(echo $gc)_$(echo $java_version)_$(echo $THREADS)
    export JFR_RECORDING=$(echo $name).jfr
    # export RANDOM_COUNT=200
    numactl --physcpubind=0-3 java -cp /Users/gunnarmorling/.m2/repository/org/hdrhistogram/HdrHistogram/2.2.2/HdrHistogram-2.2.2.jar:target/classes -Xmx$(echo $MEM)m -Xms$(echo $MEM)m -XX:+Use$(echo $gc)GC dev.morling.demos.AllocationTest > $(echo $name).hdr
done
