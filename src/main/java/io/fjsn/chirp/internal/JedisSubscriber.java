package io.fjsn.chirp.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.fjsn.chirp.Chirp;
import io.fjsn.chirp.ChirpPacketEvent;
import io.fjsn.chirp.ChirpRegistry;

import redis.clients.jedis.JedisPubSub;

import java.util.function.Consumer;

public class JedisSubscriber extends JedisPubSub {

    private final Chirp chirp;
    private final ChirpRegistry registry;
    private final Consumer<ChirpPacketEvent<?>> eventConsumer;

    public JedisSubscriber(
            Chirp chirp, ChirpRegistry registry, Consumer<ChirpPacketEvent<?>> eventConsumer) {
        this.chirp = chirp;
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
            boolean self = json.get("self").getAsBoolean();
            long sent = json.get("sent").getAsLong();

            if (!self && origin.equals(chirp.getOrigin())) {
                ChirpLogger.debug("Ignoring message from self");
                return;
            }

            ChirpPacketEvent<Object> event =
                    new ChirpPacketEvent<>(packet, origin, self, sent, System.currentTimeMillis());
            eventConsumer.accept(event);
        } catch (Exception e) {
            ChirpLogger.severe("Error handling message: " + e.getMessage());
        }
    }
}
