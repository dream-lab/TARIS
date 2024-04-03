#!/bin/bash

# Check if the correct number of arguments are provided
if [ "$#" -ne 5 ]; then
    echo "Usage: $0 <Full_executable_jar_path> <Graph> <Src_Vertex_ID> <Start_time_step> <End_time_step>"
    echo "Example: run.sh /home/hadoop/jan-baseline/jars/tink.jar Reddit 4912335 1 122"
    exit 1
fi

FULL_JAR_PATH=$1
GRAPH=$2
ALGORITHM="EAT"
SRC_VERTEX_ID=$3
START_TIME_STEP=$4
END_TIME_STEP=$5

OUTPUT_DIR="baseline-output-tink"
mkdir "$OUTPUT_DIR"

# Run the Flink job
flink run -c tink.BaselineExecutor "$FULL_JAR_PATH" "$GRAPH" "$ALGORITHM" "$SRC_VERTEX_ID" "$START_TIME_STEP" "$END_TIME_STEP" "$OUTPUT_DIR"