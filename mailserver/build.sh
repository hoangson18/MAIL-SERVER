#!/bin/sh
# Bien dich server (can JDK 17+)
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java") && echo "Build OK -> thu muc out/"
