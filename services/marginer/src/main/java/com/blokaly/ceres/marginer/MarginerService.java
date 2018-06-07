package com.blokaly.ceres.marginer;

import com.blokaly.ceres.binding.BootstrapService;
import com.blokaly.ceres.binding.CeresModule;
import com.blokaly.ceres.common.Source;
import com.blokaly.ceres.system.Services;
import com.blokaly.ceres.kafka.KafkaStreamModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class MarginerService extends BootstrapService {
  private static final Logger LOGGER = LoggerFactory.getLogger(MarginerService.class);
  private final KafkaStreams streams;

  @Inject
  public MarginerService(KafkaStreams streams) {
    this.streams = streams;
  }

  @Override
  protected void startUp() throws Exception {
    LOGGER.info("starting kafka streams...");
    streams.start();
  }

  @Override
  protected void shutDown() throws Exception {
    LOGGER.info("stopping kafka streams...");
    streams.close();
  }

  private static class MarginerModule extends CeresModule {

    @Override
    protected void configure() {
      install(new KafkaStreamModule());
      expose(KafkaStreams.class);
    }

    @Provides
    @Singleton
    public Gson provideGson() {
      GsonBuilder builder = new GsonBuilder();
      return builder.create();
    }

    @Provides
    @Singleton
    public KeyValueMapper<String, String, KeyValue<String, String>> provideMarginProcessor(IDSequencer sequencer, Gson gson) {
      return new MarginProcessor(sequencer, gson);
    }

    @Provides
    @Singleton
    public StreamsBuilder provideStreamsBuilder(Config config, KeyValueMapper<String, String, KeyValue<String, String>> marginProcessor) {
      StreamsBuilder builder = new StreamsBuilder();
      List<String> symbols = config.getStringList("symbols");
      KStream<String, String> stream = builder.stream(symbols.stream().map(sym -> "md." + sym).collect(Collectors.toList()));
      stream.filter((key, value) -> key.endsWith(Source.BEST.getCode())).map(marginProcessor).to("md." + Source.FINFABRIK.name().toLowerCase());
      return builder;
    }
  }

  public static void main(String[] args)  {
    Services.start(new MarginerModule());
  }
}
