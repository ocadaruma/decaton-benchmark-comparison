package com.mayreh;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;

import com.linecorp.decaton.benchmark.Recording;
import com.linecorp.decaton.benchmark.ResourceTracker;
import com.linecorp.decaton.benchmark.Runner;
import com.linecorp.decaton.benchmark.Task;

public class SpringKafkaRunner implements Runner {
    private MessageListenerContainer container;

    @Override
    public void init(Config config, Recording recording, ResourceTracker resourceTracker)
            throws InterruptedException {
        Map<String, Object> props = new HashMap<>();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        props.put(CommonClientConfigs.CLIENT_ID_CONFIG, "decaton-benchmark-springkafka");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "decaton-benchmark-springkafka");

        // decaton-benchmark's partition count is 3
        int concurrency = 3;
        CountDownLatch startLatch = new CountDownLatch(concurrency);

        DefaultKafkaConsumerFactory<String, Task> consumerFactory =
                new DefaultKafkaConsumerFactory<>(props, Serdes.String().deserializer(), config.taskDeserializer());

        MessageListener<String, Task> listener = record -> recording.process(record.value());
        ContainerProperties containerProps = new ContainerProperties(config.topic());
        containerProps.setMessageListener(listener);
        containerProps.setConsumerRebalanceListener(new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                // noop
            }
            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                startLatch.countDown();
            }
        });

        ConcurrentMessageListenerContainer<String, Task> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, containerProps);
        container.setConcurrency(concurrency);

        this.container = container;
        container.start();
        startLatch.await();
    }

    @Override
    public void close() throws Exception {
        container.stop();
    }
}
