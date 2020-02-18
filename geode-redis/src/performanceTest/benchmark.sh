#!/usr/bin/env bash
#start geode redis
#start redis becnhmark and listen to gedoe redis API

gfsh -e "start locator"

gfsh -e "start server
          --name=server1
         --locators=localhost[10334]
          --server-port=0
          --redis-port=6379
          --redis-bind-address=127.0.0.1"

redis-benchmark -t set, get -n 10000 -q --csv

gfsh -e "connect" -e "shutdown --include-locators=true"