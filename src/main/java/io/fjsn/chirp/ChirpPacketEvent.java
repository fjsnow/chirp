package io.fjsn.chirp;

public class ChirpPacketEvent<T> {

    private final T packet;
    private String origin;
    private boolean self;

    private long sent;
    private long received;
    private long latency;

    public ChirpPacketEvent(T packet, String origin, boolean self, long sent, long received) {
        this.packet = packet;
        this.origin = origin;
        this.sent = sent;
        this.received = received;
        this.latency = received - sent;
    }

    public T getPacket() {
        return packet;
    }

    public String getOrigin() {
        return origin;
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

    @Override
    public String toString() {
        return "ChirpPacketEvent{"
                + "packet="
                + packet
                + ", origin='"
                + origin
                + '\''
                + ", sent="
                + sent
                + ", received="
                + received
                + ", latency="
                + latency
                + '}';
    }
}
