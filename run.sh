#!/bin/bash
# Linux / macOS: compile and start the server  ->  http://localhost:8080
cd "$(dirname "$0")"
mkdir -p out
javac -d out $(find src -name "*.java") || exit 1
java -cp out Main "$@"
