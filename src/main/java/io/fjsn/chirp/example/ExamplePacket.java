package io.fjsn.chirp.example;

import io.fjsn.chirp.annotation.ChirpField;
import io.fjsn.chirp.annotation.ChirpPacket;

@ChirpPacket
public class ExamplePacket {

    @ChirpField private byte id;
    @ChirpField private String player;

    public String getPlayer() {
        return player;
    }

    public ExamplePacket() {}

    public ExamplePacket(String player) {
        this.player = player;
    }

    @Override
    public String toString() {
        return "ExamplePacket{" + "id=" + id + ", player='" + player + '\'' + '}';
    }
}
