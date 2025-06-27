package io.fjsn.chirp;

import java.util.UUID;

public class ChirpPacketEvent<T> {

    private final Chirp chirp;

    private final UUID packetId;

    private final T packet;
    private final String origin;
    private final boolean responding;
    private final UUID respondingTo;
    private final boolean self;

    private final long sent;
    private final long received;
    private final long latency;

    public ChirpPacketEvent(
            Chirp chirp,
            UUID packetId,
            T packet,
            String origin,
            boolean responding,
            UUID respondingTo,
            boolean self,
            long sent,
            long received) {
        this.chirp = chirp;
        this.packetId = packetId;
        this.packet = packet;
        this.origin = origin;
        this.responding = responding;
        this.respondingTo = respondingTo;
        this.self = self;
        this.sent = sent;
        this.received = received;
        this.latency = received - sent;
    }

    public UUID getPacketId() {
        return packetId;
    }

    public T getPacket() {
        return packet;
    }

    public String getOrigin() {
        return origin;
    }

    public boolean isResponding() {
        return responding;
    }

    public UUID getRespondingTo() {
        return respondingTo;
    }

    public long getSent() {
        return sent;
    }

    public long getReceived() {
        return received;
    }

    public long getLatency() {
        return latency;
    }

    public void respond(Object response) {
        chirp.respond(this, response, self);
    }

    @Override
    public String toString() {
        return "ChirpPacketEvent{"
                + "packetId="
                + packetId
                + ", packet="
                + packet
                + ", origin='"
                + origin
                + '\''
                + ", self="
                + self
                + ", sent="
                + sent
                + ", received="
                + received
                + ", latency="
                + latency
                + '}';
    }
}
