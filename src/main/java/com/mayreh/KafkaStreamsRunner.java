package com.mayreh;

import java.util.Properties;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;

import com.linecorp.decaton.benchmark.Recording;
import com.linecorp.decaton.benchmark.ResourceTracker;
import com.linecorp.decaton.benchmark.Runner;
import com.linecorp.decaton.benchmark.Task;

public class KafkaStreamsRunner implements Runner {
    private KafkaStreams streams;

    @Override
    public void init(Config config, Recording recording, ResourceTracker resourceTracker)
            throws InterruptedException {
        Serializer<Task> taskSerializer = (topic, data) -> {
            throw new UnsupportedOperationException("Never used");
        };

        StreamsBuilder builder = new StreamsBuilder();
        builder.stream(config.topic(), Consumed.with(
                Serdes.String(),
                Serdes.serdeFrom(taskSerializer, config.taskDeserializer())))
               .foreach((key, task) -> recording.process(task));

        Properties props = new Properties();
        props.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "decaton-benchmark-kafkastreams");
        // decaton-benchmark's partition count is 3
        props.setProperty(StreamsConfig.NUM_STREAM_THREADS_CONFIG, "3");
        config.parameters().forEach(props::setProperty);

        streams = new KafkaStreams(builder.build(), props);
        streams.start();
    }

    @Override
    public void close() throws Exception {
        streams.close();
    }
}
