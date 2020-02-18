#!/usr/bin/env bash

# decide what commands we're testing

baseLineCommit=$1
comparisonCommit=$2

echo "baseLineCommit: ${baseLineCommit}"
echo "comparisonCommit: ${comparisonCommit}"

git stash
git co ${baseLineCommit}

gfsh -e "start locator"

gfsh -e "start server
          --name=server1
         --locators=localhost[10334]
          --server-port=0
          --redis-port=6379
          --redis-bind-address=127.0.0.1"

redis-benchmark -t set,get -q -n 10000

gfsh -e "connect" -e "shutdown --include-locators=true"