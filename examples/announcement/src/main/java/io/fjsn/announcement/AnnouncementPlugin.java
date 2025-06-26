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
        chirp =
                Chirp.builder()
                        .debug(true)
                        .channel("announcement")
                        .origin("server-1")
                        // .scan("io.fjsn.announcement")
                        .packet(AnnouncementPacket.class)
                        .listener(new AnnouncementListener())
                        .redis("localhost", 6379)
                        .build();

        getCommand("announce").setExecutor(new AnnounceCommand(this));
    }
}
