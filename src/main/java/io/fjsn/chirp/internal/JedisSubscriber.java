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
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            Object packet = PacketSerializer.deserialize(json, registry);
            String origin = json.get("origin").getAsString();

            ChirpPacketEvent<Object> event = new ChirpPacketEvent<>(packet, origin);
            eventConsumer.accept(event);
        } catch (Exception e) {
            System.err.println("[ChirpJedisSubscriber] Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
