package com.blokaly.ceres.anx;

import com.blokaly.ceres.common.Pair;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import io.socket.client.Socket;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class AnxSocketIOClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnxSocketIOClient.class);
  private static final String PRIVATE = "private/";
  private static final String TOPIC_PREFIX = "public/orderBook/ANX/";
  private static final String SUBSCRIBE = "subscribe";
  private final AnxRestClient restClient;
  private final JsonCracker cracker;
  private final Socket socket;

  @Inject
  public AnxSocketIOClient(Config config, Socket socket, AnxRestClient restClient, JsonCracker cracker) {
    this.restClient = restClient;
    this.cracker = cracker;
    List<String> symbols = config.getStringList("symbols");
    socket.on(Socket.EVENT_CONNECT, callback -> this.onConnected(symbols));
    socket.on(Socket.EVENT_DISCONNECT, callback -> this.onDisconnected());
    this.socket = socket;
    symbols.forEach(this::onTopic);
  }

  private String[] getTopics(String uuid, List<String> symbols) {
    String[] topics = new String[symbols.size()+1];
    int idx = 0;
    topics[idx++] = PRIVATE + uuid;
    for (String symbol : symbols) {
      topics[idx++] = TOPIC_PREFIX + symbol;
    }
    return topics;
  }

  private void onConnected(List<String> symbols) {
    LOGGER.info("ANX socket connected");
    Pair<String, String> pair = restClient.getUuidAndToken();
    try {
      JSONObject obj = new JSONObject();
      obj.put("token", pair.getRight());
      obj.put("topics",  getTopics(pair.getLeft(), symbols));
      LOGGER.info("subscribing topics: {}", obj);
      socket.emit(SUBSCRIBE, obj);
    } catch (Exception e) {
      LOGGER.error("Error in subscription", e);
    }
  }

  private void onDisconnected() {
    LOGGER.info("ANX socket disconnected");
  }

  private void onTopic(String symbol) {
    socket.on(TOPIC_PREFIX + symbol, callback -> {
      JSONObject jsonObject = (JSONObject) callback[0];
      cracker.crack(jsonObject.toString());
    })  ;
  }

  public void connect() {
    socket.connect();
  }

  public void close() {
    socket.close();
  }
}
