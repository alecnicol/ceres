package com.blokaly.ceres.kafka;

import com.blokaly.ceres.system.CommonConfigs;
import com.blokaly.ceres.common.Configs;
import com.blokaly.ceres.common.Source;
import com.blokaly.ceres.binding.SingleThread;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HBProducer {
  private static final Logger LOGGER = LoggerFactory.getLogger(HBProducer.class);
  private final Producer<String, String> producer;
  private final String topic;
  private final String key;
  private final ScheduledExecutorService ses;
  private volatile boolean closing = false;


  @Inject
  public HBProducer(Producer<String, String> producer, Config config, @SingleThread ScheduledExecutorService ses) {
    this.producer = producer;
    topic = config.getString(CommonConfigs.KAFKA_TOPIC);
    String hbKey = Configs.getOrDefault(config, CommonConfigs.KAFKA_HB_KEY, Configs.STRING_EXTRACTOR, "");
    String suffix = exchangeOrDefault(hbKey);
    key = suffix.isEmpty() ? null :  "hb." + suffix;
    this.ses = ses;
  }

  private String exchangeOrDefault(String name) {
    Source source = Source.parse(name.toUpperCase());
    if (source == null) {
      return name;
    } else {
      return source.getCode();
    }
  }

  @PostConstruct
  public void init() {
    if (producer==null || key==null) {
      LOGGER.info("producer or hb key unavailable, heartbeat disabled");
      return;
    }
    LOGGER.info("scheduling kafka topic heartbeating...");
    ses.scheduleWithFixedDelay(this::hb, 3, 1, TimeUnit.SECONDS);
  }

  @PreDestroy
  public void stop() {
    closing = true;
    if (producer != null) {
      producer.flush();
      producer.close();
    }
  }

  private void hb() {
    if (closing) {
      return;
    }
    ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, String.valueOf(System.currentTimeMillis()));
    LOGGER.debug("{} hb", key);
    producer.send(record, (metadata, exception) -> {
      if (exception != null) {
        LOGGER.error("Error sending Kafka hb", exception);
      }
    });
  }
}
