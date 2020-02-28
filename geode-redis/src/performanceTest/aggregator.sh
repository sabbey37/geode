#!/usr/bin/env bash

set -e
set -x

TEST_RUN_COUNT=1
redis_benchmark_commands=("SET" "GET" "INCR" "LPUSH" "RPUSH" "LPOP" "RPOP" "SADD" "SPOP")

function aggregate() {
  local command=$1

  grep ${command} results.csv | cut -d"," -f 2 | cut -d"\"" -f 2 | awk '{ sum += $1 } END { if (NR > 0) print sum / NR }'
}

function join_by() {
  local IFS="$1"
  shift
  echo "$*"
}

REDIS_COMMAND_STRING=$(join_by , "${redis_benchmark_commands[@]}")

SCRIPT_DIR=$(
  cd $(dirname $0)
  pwd
)

cd ${SCRIPT_DIR}

rm -f results.csv

X=0
while [[ ${X} -lt ${TEST_RUN_COUNT} ]]; do
  redis-benchmark -t ${REDIS_COMMAND_STRING} -q -n 100000 --csv >>results.csv

  ((X = X + 1))
done

CURRENT_SHA=$(git rev-parse --short HEAD)
AGGREGATE_FILE_NAME=${CURRENT_SHA}-aggregate.csv

echo "Command", "Average Requests Per Second" >${AGGREGATE_FILE_NAME}
for command in ${redis_benchmark_commands[@]}; do
  SUM_AGGREGATE=$(aggregate ${command})
  echo ${command}, ${SUM_AGGREGATE} >>${AGGREGATE_FILE_NAME}
done