package io.fjsn.announcement;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AnnounceCommand implements CommandExecutor {

    private final AnnouncementPlugin plugin;

    public AnnounceCommand(AnnouncementPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /announce <message>");
            return true;
        }

        String message = String.join(" ", args);
        AnnouncementPacket packet = new AnnouncementPacket(message);

        plugin.getChirp().publish(packet, true);
        return true;
    }
}
