package io.fjsn.chirp.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.fjsn.chirp.ChirpPacketEvent;
import io.fjsn.chirp.ChirpRegistry;

import redis.clients.jedis.JedisPubSub;

import java.util.function.Consumer;

public class JedisSubscriber extends JedisPubSub {

    private final Consumer<ChirpPacketEvent<?>> eventConsumer;
    private final ChirpRegistry registry;

    public JedisSubscriber(ChirpRegistry registry, Consumer<ChirpPacketEvent<?>> eventConsumer) {
        this.registry = registry;
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void onMessage(String channel, String message) {
        ChirpLogger.debug("Recieved message on channel '" + channel + "': " + message);

        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            Object packet = PacketSerializer.deserialize(json, registry);
            String origin = json.get("origin").getAsString();
            long sent = json.get("sent").getAsLong();

            ChirpPacketEvent<Object> event =
                    new ChirpPacketEvent<>(packet, origin, sent, System.currentTimeMillis());
            eventConsumer.accept(event);
        } catch (Exception e) {
            ChirpLogger.severe("Error handling message: " + e.getMessage());
        }
    }
}
