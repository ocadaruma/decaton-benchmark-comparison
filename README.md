# Kafka consumer framework benchmarks

This repository contains custom `com.linecorp.decaton.benchmark.Runner` implementations
which executable by [Decaton benchmark](https://github.com/line/decaton/tree/master/benchmark)
for several Kafka consumer frameworks along with benchmark results.

- Kafka Streams
- Spring Kafka

Please see [results](./results) for benchmark condition and results.

## How to run benchmarks

```sh
# Clone Decaton repo to somewhere
git clone git@github.com:line/decaton.git

cd /path/to/decaton-benchmark-comparison

./run.sh /path/to/decaton
```
