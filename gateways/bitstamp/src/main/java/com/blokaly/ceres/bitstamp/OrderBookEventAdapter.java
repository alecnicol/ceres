package com.blokaly.ceres.bitstamp;

import com.blokaly.ceres.bitstamp.event.OrderBookEvent;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;

public class OrderBookEventAdapter implements JsonDeserializer<OrderBookEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderBookEventAdapter.class);

    @Override
    public OrderBookEvent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

        JsonObject jsonObject = json.getAsJsonObject();
        long sequence = jsonObject.get("timestamp").getAsLong();
        JsonArray bids = jsonObject.get("bids").getAsJsonArray();
        JsonArray asks = jsonObject.get("asks").getAsJsonArray();
        return OrderBookEvent.parse(sequence, bids, asks);
    }
}
