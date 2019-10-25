#!/bin/sh

# $1: your port#
echo 'run UWPlace from UWAgent.UWPlace with port# = '$1
java -cp UWAgent.jar:. UWAgent.UWPlace -p $1
