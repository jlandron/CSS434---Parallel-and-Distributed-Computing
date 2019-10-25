#!/bin/sh
base64 /dev/urandom | head -c 100000000 > text1.txt
base64 /dev/urandom | head -c 100000000 > text2.txt
base64 /dev/urandom | head -c 100000000 > text3.txt
