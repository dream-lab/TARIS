#!/bin/bash

TARIS_INPUT="../graph/sampleGraphMutations"
OUTPUT_DIR="../graph/baselineInput"
RESUME="false"

# Run the Flink job
javac InputCreator.java
java InputCreator "$TARIS_INPUT" "$OUTPUT_DIR" "$RESUME"