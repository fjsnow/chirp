package io.fjsn.chirp.example;

import io.fjsn.chirp.Chirp;

public class ExamplePlugin {

    private Chirp chirp;

    public void onEnable() {
        chirp =
                Chirp.builder()
                        .channel("announcement")
                        .origin("server-1")
                        // .packet(ExamplePacket.class)
                        .listener(new ExamplePacketListener())
                        .redis("localhost", 6379)
                        .build();

        ExamplePacket packet = new ExamplePacket("Player1");
        chirp.publish(packet);

        System.out.println("ExamplePlugin enabled and packet sent.");
    }
}
