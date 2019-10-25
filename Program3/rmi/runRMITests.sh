#!/bin/sh
echo "starting all rmi tests"

for i in {1..5}
do
#standard commands
java UnixClient P $1 1 cssmpi1 4 who ls ps df
done
for i in {1..5}
do
java UnixClient P $1 2 cssmpi2 cssmpi1 4 who ls ps df
done
for i in {1..5}
do
java UnixClient P $1 3 cssmpi3 cssmpi2 cssmpi1 4 who ls ps df
done
for i in {1..5}
do
java UnixClient P $1 1 cssmpi1 12 who ls ps df who ls ps df who ls ps df
done
for i in {1..5}
do
java UnixClient P $1 2 cssmpi1 cssmpi2 12 who ls ps df who ls ps df who ls ps df
done
for i in {1..5}
do
java UnixClient P $1 3 cssmpi1 cssmpi2 cssmpi3 12 who ls ps df who ls ps df who ls ps df
done
for i in {1..5}
do
#grep
java UnixClient P $1 1 cssmpi1 1 grep\ -o\ 123\ ../files/text1.txt
done
for i in {1..5}
do
java UnixClient P $1 2 cssmpi2 cssmpi1 1 grep\ -o\ 123\ ../files/text1.txt
done
for i in {1..5}
do
java UnixClient P $1 3 cssmpi3 cssmpi2 cssmpi1 1 grep\ -o\ 123\ ../files/text1.txt
done
for i in {1..5}
do
#download file and grep
java UnixClient S $1 1 cssmpi1 1 cat\ ../files/text1.txt | grep -o 123 | wc -l 
done
for i in {1..5}
do
java UnixClient S $1 2 cssmpi2 cssmpi1 1 cat\ ../files/text1.txt | grep -o 123 | wc -l 
done
for i in {1..5}
do
java UnixClient S $1 3 cssmpi3 cssmpi2 cssmpi1 1 cat\ ../files/text1.txt | grep -o 123 | wc -l 
done