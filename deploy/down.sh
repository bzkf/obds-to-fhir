#!/bin/sh
docker stop "$(docker ps -a | grep -v "oracle" | awk 'NR>1 {print $1}')"
docker rm "$(docker ps -a -q -f status=exited)"
