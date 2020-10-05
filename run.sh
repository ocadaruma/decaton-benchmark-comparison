#!/bin/bash

set -e

if [ $# -ne 1 ]; then
    echo "Usage: ./run.sh /path/to/decaton_repo"
    exit 1
fi

SCRIPT_DIR="$(cd $(dirname $0); pwd)"
NUM_WARMUPS=10000000
FRAMEWORKS=(kafka-streams spring-kafka decaton decaton32)
LATENCIES=(0 10 50 100)

function num_tasks() {
    latency="$1"

    if [ $latency -eq 0 ]; then
        echo 1000000
    elif [ $latency -le 10 ]; then
        echo 10000
    else
        echo 1000
    fi
}

function run_benchmark() {
    framework="$1"
    latency="$2"

    params=()
    runner=""
    case "$framework" in
        kafka-streams)
            runner="com.mayreh.KafkaStreamsRunner"
            ;;
        spring-kafka)
            runner="com.mayreh.SpringKafkaRunner"
            ;;
        decaton)
            runner="com.linecorp.decaton.benchmark.DecatonRunner"
            params+=("--param=decaton.max.pending.records=10000")
            ;;
        decaton32)
            runner="com.linecorp.decaton.benchmark.DecatonRunner"
            params+=("--param=decaton.max.pending.records=10000")
            params+=("--param=decaton.partition.concurrency=32")
            ;;
    esac

    tasks=$(num_tasks $latency)

    echo "Running benchmark: $framework with $tasks tasks, $latency ms latency"
    ./debm.sh \
      --runs 3 \
      --title "$framework" \
      --format=json \
      --file-name-only \
      --runner $runner \
      --profile \
      --tasks $tasks \
      --warmup $NUM_WARMUPS \
      --simulate-latency $latency ${params[@]} | tee $SCRIPT_DIR/result.$framework.${latency}ms.json
}

decaton_dir="$1"

# publish benchmark module locally
cd $decaton_dir
./gradlew :benchmark:publishToMavenLocal -x sign

# create shadowJar
cd $SCRIPT_DIR
./gradlew shadowJar
export CLASSPATH=$(ls $SCRIPT_DIR/build/libs/decaton-benchmark-comparison-*-all.jar | sort -nr | head -1)

# run benchmarks
cd $decaton_dir/benchmark

for latency in ${LATENCIES[@]}; do
    for framework in ${FRAMEWORKS[@]}; do
        run_benchmark $framework $latency
    done
done
