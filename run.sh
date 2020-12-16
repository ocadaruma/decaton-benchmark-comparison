#!/bin/bash

set -e

if [ $# -ne 2 ]; then
    echo "Usage: ./run.sh <decaton repo dir> <result dir>"
    exit 1
fi

RESULT_DIR="$2"
SCRIPT_DIR="$(cd $(dirname $0); pwd)"
NUM_WARMUPS=10000000
FRAMEWORKS=(kafka-streams spring-kafka pconsumer decaton)
LATENCIES=(0)

function num_tasks() {
    framework="$1"
    latency="$2"

    if [ $latency -eq 0 ]; then
        echo 1000000
        return
    fi

    if [ $framework = "decaton10" ]; then
        echo 100000
        return
    fi

    echo "100000 / $latency" | bc
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
        pconsumer)
            runner="com.mayreh.ParallelConsumerRunner"
            params+=("--param=concurrency=3")
            ;;
        decaton10)
            runner="com.linecorp.decaton.benchmark.DecatonRunner"
            params+=("--param=decaton.max.pending.records=10000")
            params+=("--param=decaton.partition.concurrency=10")
            ;;
    esac

    tasks=$(num_tasks $framework $latency)

    name="$framework-${latency}ms"
    echo "Running benchmark: $framework with $tasks tasks, $latency ms latency"
    ./debm.sh \
      --runs 3 \
      --title "$name" \
      --format=json \
      --file-name-only \
      --runner $runner \
      --profile \
      --profiler-opts="-f $RESULT_DIR/$name-profile.svg" \
      --taskstats \
      --taskstats-output="$RESULT_DIR/$name-taskstats.txt" \
      --tasks $tasks \
      --warmup $NUM_WARMUPS \
      --simulate-latency $latency ${params[@]} | tee $RESULT_DIR/$name.json
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
