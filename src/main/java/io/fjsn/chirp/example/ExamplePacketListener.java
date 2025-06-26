package io.fjsn.chirp.example;

import io.fjsn.chirp.ChirpPacketEvent;
import io.fjsn.chirp.annotation.ChirpHandler;
import io.fjsn.chirp.annotation.ChirpListener;

@ChirpListener(scan = false)
public class ExamplePacketListener {

    @ChirpHandler
    public void onExamplePacket(ChirpPacketEvent<ExamplePacket> event) {
        ExamplePacket packet = event.getPacket();
        System.out.println("Received ExamplePacket with player: " + packet.getPlayer());
    }
}
