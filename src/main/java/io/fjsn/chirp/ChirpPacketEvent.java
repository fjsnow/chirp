package io.fjsn.chirp;

public class ChirpPacketEvent<T> {

    private final T packet;
    private String origin;

    public ChirpPacketEvent(T packet, String origin) {
        this.packet = packet;
        this.origin = origin;
    }

    public T getPacket() {
        return packet;
    }

    public String getOrigin() {
        return origin;
    }
}
