#!/bin/sh

# $1 your port#
java -cp UWAgent.jar:. UWAgent.UWInject -p $1 localhost WhoAgent uw1-320-21 uw1-320-22
