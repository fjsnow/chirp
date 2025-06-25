package io.fjsn.chirp.example;

import io.fjsn.chirp.Chirp;

public class ExamplePlugin {

    private Chirp chirp;

    public void onEnable() {
        chirp = new Chirp("hub-1");
        chirp.scanAndRegister("io.fjsn.chirp.example");

        ExamplePacket packet = new ExamplePacket("Player1");
        chirp.publish(packet);

        System.out.println("ExamplePlugin enabled and packet sent.");
    }
}
