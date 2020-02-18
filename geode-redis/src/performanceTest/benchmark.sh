#!/usr/bin/env bash

# decide what commands we're testing

baseLineCommit=$1
comparisonCommit=$2
neededToStash=false

echo "baseLineCommit: ${baseLineCommit}"
echo "comparisonCommit: ${comparisonCommit}"

originalBranch=$(git branch | sed -n -e 's/^\* \(.*\)/\1/p')
echo " originalBranch: ${originalBranch}"

#git checkout ${baseLineCommit}
#returnCode=$?
#echo " returnCode: ${returnCode}"
# if [[ ${returnCode} -ne 0 ]] ; then
#    git stash
#    neededToStash=true
#    git co ${baseLineCommit}
#fi

echo "STASHHHHHH: ${neededToStash}"

gfsh -e "start locator"

gfsh -e "start server
          --name=server1
          --locators=localhost[10334]
          --server-port=0
          --redis-port=6379
          --redis-bind-address=127.0.0.1"

redis-benchmark -t set,get -q -n 100000  --csv > results.csv

gfsh -e "connect" -e "shutdown --include-locators=true"

#git checkout ${originalBranch}
#
#if [[ ${neededToStash} ]] ; then
#  echo "POPPING THE STASH"
#  git stash pop
#fi

gfsh -e "start locator"

gfsh -e "start server
          --name=server1
         --locators=localhost[10334]
          --server-port=0
          --redis-port=6379
          --redis-bind-address=127.0.0.1"

redis-benchmark -t set,get -q -n 100000  --csv > results2.csv

gfsh -e "connect" -e "shutdown --include-locators=true"
