package io.fjsn.announcement;

import io.fjsn.chirp.Chirp;

import org.bukkit.plugin.java.JavaPlugin;

class AnnouncementPlugin extends JavaPlugin {

    private Chirp chirp;

    public Chirp getChirp() {
        return chirp;
    }

    @Override
    public void onEnable() {
        // chirp =
        //        new Chirp.builder()
        //                .channel("announcement")
        //                .scan("io.fjsn.announcement")
        //                .connect("localhost", 6379)
        //                .subscribe();

        chirp =
                new Chirp.builder()
                        .channel("announcement")
                        .origin("server-1")
                        .packet(AnnouncementPacket.class)
                        .listener(new AnnouncementListener())
                        .redis("localhost", 6379)
                        .build();

        getCommand("announce").setExecutor(new AnnounceCommand(this));
    }
}
