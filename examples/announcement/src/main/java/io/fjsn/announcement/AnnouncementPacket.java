package io.fjsn.announcement;

import io.fjsn.chirp.annotation.ChirpField;
import io.fjsn.chirp.annotation.ChirpPacket;

@ChirpPacket
public class AnnouncementPacket {

    @ChirpField private String message;

    public String getMessage() {
        return message;
    }

    public AnnouncementPacket() {}

    public AnnouncementPacket(String message) {
        this.message = message;
    }
}
