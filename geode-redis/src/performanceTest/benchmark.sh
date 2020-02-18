#!/usr/bin/env bash

# decide what commands we're testing

baseLineCommit=$1
comparisonCommit=$2
neededToStash=false

echo "baseLineCommit: ${baseLineCommit}"
echo "comparisonCommit: ${comparisonCommit}"

originalBranch=$(git branch | sed -n -e 's/^\* \(.*\)/\1/p')
echo " originalBranch: ${originalBranch}"


git co ${baseLineCommit}
  returnCode=$?
echo " returnCode: ${returnCode}"
# if [[ returnCode -eq 0 ]] ; then
#    git stash
#    $neededToStash=true
#    git co ${baseLineCommit}
#fi

gfsh -e "start locator"

gfsh -e "start server
          --name=server1
         --locators=localhost[10334]
          --server-port=0
          --redis-port=6379
          --redis-bind-address=127.0.0.1"

redis-benchmark -t set,get -q -n 10000

gfsh -e "connect" -e "shutdown --include-locators=true"

git co ${originalBranch}