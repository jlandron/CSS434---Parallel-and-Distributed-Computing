#!/bin/sh
#running each script in its own loop to make less overlap due to agent migration
echo "starting all agent tests"

for i in {1..5}
do
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost UnixAgent ${2-C} 1 cssmpi1 4 who ls ps df
done
for i in {1..5}
do
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost UnixAgent ${2-C} 2 cssmpi1 cssmpi2 4 who ls ps df
done
for i in {1..5}
do
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost UnixAgent ${2-C} 3 cssmpi2 cssmpi3 cssmpi3 4 who ls ps df
done
for i in {1..5}
do
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost UnixAgent ${2-C} 1 cssmpi1 12 who ls ps df who ls ps df who ls ps df
done
for i in {1..5}
do
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost UnixAgent ${2-C} 2 cssmpi1 cssmpi2 12 who ls ps df who ls ps df who ls ps df
done
for i in {1..5}
do
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost UnixAgent ${2-C} 3 cssmpi1 cssmpi2 cssmpi3 12 who ls ps df who ls ps df who ls ps df
done
for i in {1..5}
do
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost UnixAgent ${2-C} 1 cssmpi1 1 grep\ -o\ 123\ ../files/text1.txt
done
for i in {1..5}
do
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost UnixAgent ${2-C} 2 cssmpi1 cssmpi2 1 grep\ -o\ 123\ ../files/text1.txt
done
for i in {1..5}
do
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost UnixAgent ${2-C} 3 cssmpi1 cssmpi2 cssmpi3 1 grep\ -o\ 123\ ../files/text1.txt
done