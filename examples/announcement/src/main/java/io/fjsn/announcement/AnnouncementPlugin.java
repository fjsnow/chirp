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
                new Chirp("announcement")
                        .debug()
                        .scan("io.fjsn.announcement")
                        .connect("localhost", 6379)
                        .subscribe();

        getCommand("announce").setExecutor(new AnnounceCommand(this));
    }
}
