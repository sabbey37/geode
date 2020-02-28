#!/usr/bin/env bash

set -e
set -x

SCRIPT_DIR=$(
  cd $(dirname $0)
  pwd
)
GEODE_BASE=$(
  cd $SCRIPT_DIR/../../..
  pwd
)

cd $GEODE_BASE

./gradlew devBuild installD

GFSH=$PWD/geode-assembly/build/install/apache-geode/bin/gfsh

pkill -9 -f ServerLauncher || true
rm -rf server1
rm -rf locator1

$GFSH -e "start locator --name=locator1"

$GFSH -e "start server
          --name=server1
          --locators=localhost[10334]
          --server-port=0
          --redis-port=6379
          --redis-bind-address=127.0.0.1"

cd ${SCRIPT_DIR}

./aggregator.sh

$GFSH -e "connect" -e "shutdown --include-locators=true"
