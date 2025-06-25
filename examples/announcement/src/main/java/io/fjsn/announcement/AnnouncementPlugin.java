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
        chirp = new Chirp();
        chirp.scan("io.fjsn.announcement");
        chirp.connect("localhost", 6379);

        getCommand("announce").setExecutor(new AnnounceCommand(this));
    }
}
