#!/bin/sh

# $1: your port#
# $2: an agent class to inject
# $3 ~ $5: argumenets passed to the agent
 
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost $2 $3 $4 $5
