package io.fjsn.chirp.example;

import io.fjsn.chirp.Chirp;

public class ExamplePlugin {

    private Chirp chirp;

    public void onEnable() {
        chirp = new Chirp("hub-1");
        chirp.scan("io.fjsn.chirp.example");
        chirp.connect("127.0.0.1", 12345);

        ExamplePacket packet = new ExamplePacket("Player1");
        chirp.publish(packet);

        System.out.println("ExamplePlugin enabled and packet sent.");
    }
}
