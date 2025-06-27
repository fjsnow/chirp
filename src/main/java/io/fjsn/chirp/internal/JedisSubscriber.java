package io.fjsn.chirp.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.fjsn.chirp.Chirp;
import io.fjsn.chirp.ChirpPacketEvent;
import io.fjsn.chirp.ChirpRegistry;

import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class JedisSubscriber extends JedisPubSub {

    private final Chirp chirp;
    private final ChirpRegistry registry;
    private final EventDispatcher eventDispatcher;

    public JedisSubscriber(Chirp chirp, ChirpRegistry registry, EventDispatcher eventDispatcher) {
        this.chirp = chirp;
        this.registry = registry;
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public void onMessage(String channel, String message) {
        ChirpLogger.debug("Received message on channel '" + channel + "': " + message);

        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            Object packet = PacketSerializer.deserialize(json, registry);

            UUID packetId = UUID.fromString(json.get("packetId").getAsString());
            String origin = json.get("origin").getAsString();
            boolean responding = json.get("responding").getAsBoolean();
            UUID respondingTo =
                    json.has("respondingTo")
                            ? UUID.fromString(json.get("respondingTo").getAsString())
                            : null;
            boolean self = json.get("self").getAsBoolean();
            long sent = json.get("sent").getAsLong();

            if (!self && origin.equals(chirp.getOrigin())) {
                ChirpLogger.debug("Ignoring message from self");
                return;
            }

            ChirpPacketEvent<Object> event =
                    new ChirpPacketEvent<>(
                            chirp,
                            packetId,
                            packet,
                            origin,
                            responding,
                            respondingTo,
                            self,
                            sent,
                            System.currentTimeMillis());

            if (responding) {
                eventDispatcher.dispatchEventToResponders(event);
            } else {
                eventDispatcher.dispatchEventToListeners(event);
            }

        } catch (Exception e) {
            ChirpLogger.severe("Error handling message: " + e.getMessage());
        }
    }
}
