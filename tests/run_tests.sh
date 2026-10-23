#!/bin/bash
# Builds the project, starts a server with a FRESH temporary data folder, runs the API tests,
# restarts the server and checks that data was persisted to the JSON files.
cd "$(dirname "$0")/.."
set -e
rm -rf out tests-out /tmp/evms-test-data && mkdir out tests-out
javac -d out $(find src -name "*.java")
javac -cp out -d tests-out tests/ApiTest.java
PORT=8099
java -cp out Main $PORT /tmp/evms-test-data > /tmp/evms-server.log 2>&1 &
PID=$!
sleep 2
java -cp out:tests-out ApiTest http://localhost:$PORT full || { kill $PID; exit 1; }
kill $PID; sleep 1
echo; echo ">>> Server stopped. Restarting to prove the data is saved in files..."
java -cp out Main $PORT /tmp/evms-test-data > /tmp/evms-server2.log 2>&1 &
PID=$!
sleep 2
java -cp out:tests-out ApiTest http://localhost:$PORT persist || { kill $PID; exit 1; }
kill $PID
echo; echo "ALL TESTS PASSED"
