#!/bin/sh
echo "UWAgent.jar has some classic classes. Ignore javac's warning."
javac -classpath UWAgent.jar:. $1

#./runAgent.sh 51090 MyAgent cssmpi2 cssmpi1

#javac -classpath UWAgent.jar: MyAgent.java