package io.fjsn.announcement;

import io.fjsn.chirp.ChirpPacketEvent;
import io.fjsn.chirp.annotation.ChirpHandler;
import io.fjsn.chirp.annotation.ChirpListener;

import org.bukkit.Bukkit;

@ChirpListener
public class AnnouncementListener {

    @ChirpHandler
    public void onAnnouncement(ChirpPacketEvent<AnnouncementPacket> event) {
        AnnouncementPacket packet = event.getPacket();
        String message = packet.getMessage();

        Bukkit.getServer().broadcastMessage("[Announcement] " + message);
    }
}
