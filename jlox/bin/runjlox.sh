#!/bin/zsh

cd /Users/jahigd02/Documents/Dropbox/jacob-root/compsci/months/june23/craftinginterpreters/jlox/src/lox
javac *.java
cd ..
java lox.Lox "$@"
cd ..