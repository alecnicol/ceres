package com.blokaly.ceres.anx;

import com.blokaly.ceres.anx.callback.CommandCallbackHandler;
import com.blokaly.ceres.anx.callback.SnapshotCallbackHandler;
import com.blokaly.ceres.anx.event.AbstractEvent;
import com.blokaly.ceres.anx.event.EventType;
import com.blokaly.ceres.binding.BootstrapService;
import com.blokaly.ceres.binding.CeresModule;
import com.blokaly.ceres.system.CommonConfigs;
import com.blokaly.ceres.common.Source;
import com.blokaly.ceres.system.Services;
import com.blokaly.ceres.data.SymbolFormatter;
import com.blokaly.ceres.kafka.HBProducer;
import com.blokaly.ceres.kafka.KafkaCommonModule;
import com.blokaly.ceres.kafka.KafkaStreamModule;
import com.blokaly.ceres.kafka.ToBProducer;
import com.blokaly.ceres.orderbook.PriceBasedOrderBook;
import com.blokaly.ceres.web.HandlerModule;
import com.blokaly.ceres.web.UndertowModule;
import com.blokaly.ceres.web.handlers.HealthCheckHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Exposed;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.undertow.Undertow;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.blokaly.ceres.anx.event.EventType.SNAPSHOT;

public class AnxService extends BootstrapService {
  private final AnxSocketIOClient client;
  private final KafkaStreams streams;
  private final Undertow undertow;

  @Inject
  public AnxService(AnxSocketIOClient client,
                    @Named("Throttled") KafkaStreams streams,
                    Undertow server) {
    this.client = client;
    this.streams = streams;
    undertow = server;
  }

  @Override
  protected void startUp() throws Exception {
    LOGGER.info("starting ANX socketio client...");
    client.connect();

    waitFor(3);
    LOGGER.info("starting kafka streams...");
    streams.start();

    this.LOGGER.info("Web server starting...");
    this.undertow.start();
  }

  @Override
  protected void shutDown() throws Exception {
    LOGGER.info("Web server stopping...");
    undertow.stop();

    LOGGER.info("stopping ANX socketio client...");
    client.close();

    LOGGER.info("stopping kafka streams...");
    streams.close();
  }

  public static class AnxModule extends CeresModule {

    @Override
    protected void configure() {
      this.install(new UndertowModule(new HandlerModule() {

        @Override
        protected void configureHandlers() {
          this.bindHandler().to(HealthCheckHandler.class);
        }
      }));
      expose(Undertow.class);

      install(new KafkaCommonModule());
      install(new KafkaStreamModule());
      bindExpose(ToBProducer.class);
      bind(HBProducer.class).asEagerSingleton();
      expose(StreamsBuilder.class).annotatedWith(Names.named("Throttled"));
      expose(KafkaStreams.class).annotatedWith(Names.named("Throttled"));;

      bindAllCallbacks();
      bindExpose(MessageHandler.class).to(MessageHandlerImpl.class);
    }

    @Provides
    @Singleton
    @Exposed
    public Gson provideGson(Map<EventType, CommandCallbackHandler> handlers) {
      GsonBuilder builder = new GsonBuilder();
      builder.registerTypeAdapter(AbstractEvent.class, new EventAdapter(handlers));
      return builder.create();
    }

    @Provides
    @Singleton
    @Exposed
    public Socket provideSocket(Config config) throws Exception {
      Config apiConfig = config.getConfig("api");
      String host = apiConfig.getString("host");
      String streamPath = apiConfig.getString("path.stream");
      IO.Options options = new IO.Options();
      options.path = streamPath;
      return IO.socket(host, options);
    }

    @Provides
    @Singleton
    public Map<String, PriceBasedOrderBook> provideOrderBooks(Config config) {
      List<String> symbols = config.getStringList("symbols");
      String source = Source.valueOf(config.getString(CommonConfigs.APP_SOURCE).toUpperCase()).getCode();
      return symbols.stream().collect(Collectors.toMap(sym -> sym, sym -> {
        String symbol = SymbolFormatter.normalise(sym);
        return new PriceBasedOrderBook(symbol, symbol + "." + source);
      }));
    }

    private void bindAllCallbacks() {
      MapBinder<EventType, CommandCallbackHandler> binder = MapBinder.newMapBinder(binder(), EventType.class, CommandCallbackHandler.class);
      binder.addBinding(SNAPSHOT).to(SnapshotCallbackHandler.class);
    }
  }

  public static void main(String[] args) {
    Services.start(new AnxModule());
  }
}
