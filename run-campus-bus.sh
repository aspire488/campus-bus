#!/bin/sh
# CAMPUS BUS launcher for macOS / Linux: checks Java, Maven and db.properties, then starts the app from this folder.
# It never contains passwords: the database settings come from db.properties (copy db.properties.example).
cd "$(dirname "$0")" || exit 1

command -v java >/dev/null 2>&1 || { echo "Java was not found. Install JDK 17 or newer."; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "Maven was not found. Install Apache Maven 3.8+ and add it to PATH."; exit 1; }
[ -f db.properties ] || { echo "db.properties is missing. Copy db.properties.example to db.properties and fill in your MySQL user and password."; exit 1; }

echo "Starting CAMPUS BUS ..."
mvn -q compile exec:java || { echo "CAMPUS BUS stopped with an error. Check that MySQL is running and that db.properties is correct."; exit 1; }
