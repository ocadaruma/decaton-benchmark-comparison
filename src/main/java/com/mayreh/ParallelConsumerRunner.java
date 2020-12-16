package com.mayreh;

import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.Serdes;

import com.linecorp.decaton.benchmark.Recording;
import com.linecorp.decaton.benchmark.ResourceTracker;
import com.linecorp.decaton.benchmark.Runner;
import com.linecorp.decaton.benchmark.Task;

import io.confluent.parallelconsumer.ParallelConsumer;
import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelConsumerOptions.CommitMode;
import io.confluent.parallelconsumer.ParallelConsumerOptions.ProcessingOrder;
import io.confluent.parallelconsumer.ParallelStreamProcessor;

public class ParallelConsumerRunner implements Runner {
    private ParallelConsumer<String, Task> parallelConsumer;

    @Override
    public void init(Config config, Recording recording, ResourceTracker resourceTracker)
            throws InterruptedException {
        Properties consumerProps = new Properties();
        consumerProps.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        consumerProps.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, "decaton-benchmark-parallel-consumer");
        consumerProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "decaton-benchmark-parallel-consumer");
        consumerProps.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        Consumer<String, Task> consumer = new KafkaConsumer<>(consumerProps,
                                                              Serdes.String().deserializer(),
                                                              config.taskDeserializer());

        ParallelConsumerOptions<String, Task> options = ParallelConsumerOptions
                .<String, Task>builder()
                .ordering(ProcessingOrder.UNORDERED)
                .commitMode(CommitMode.PERIODIC_CONSUMER_ASYNCHRONOUS)
                .maxConcurrency(Integer.parseInt(
                        config.parameters().getOrDefault("concurrency", "3")))
                .consumer(consumer)
                .build();

        ParallelStreamProcessor<String, Task> processor =
                ParallelStreamProcessor.createEosStreamProcessor(options);

        parallelConsumer = processor;
        processor.subscribe(Collections.singleton(config.topic()));
        processor.poll(record -> recording.process(record.value()));
    }

    @Override
    public void close() throws Exception {
        parallelConsumer.close();
    }
}
