package com.convallyria.schematics.extended.example;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.convallyria.schematics.extended.Scheduler;
import com.convallyria.schematics.extended.Schematic;
import com.convallyria.schematics.extended.Schematic.Options;

/**
 * Per-player task that previews schematics async
 * @author SamB440
 */
public class BuildTask {

    private SchematicPlugin plugin;

    private Player player;

    private Map<UUID, List<Location>> cache = new HashMap<>();

    public BuildTask(SchematicPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @SuppressWarnings("deprecation")
    public BuildTask start() {
        Scheduler scheduler = new Scheduler();
        scheduler.setTask(Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
            if (!player.isOnline()) {
                scheduler.cancel();
                return;
            }
            PlayerManagement pm = plugin.getPlayerManagement();
            if (pm.isBuilding(player.getUniqueId())) {
                Schematic schematic = pm.getBuilding(player.getUniqueId());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder(ChatColor.RED + "Left Click to cancel" + ChatColor.GRAY + " : " + ChatColor.GREEN + "Right Click to place").create());
                List<Location> locations = new ArrayList<>(schematic.pasteSchematic(player.getTargetBlock(null, 10).getLocation().add(0, 1, 0),
                        player, Options.USE_GAME_MARKER, Options.PREVIEW, Options.IGNORE_TRANSPARENT, Options.REALISTIC));
                if (cache.containsKey(player.getUniqueId()) && !cache.get(player.getUniqueId()).equals(locations)) {
                    cache.get(player.getUniqueId()).forEach(location -> player.sendBlockChange(location, location.getBlock().getBlockData()));
                    cache.remove(player.getUniqueId());
                }
                cache.put(player.getUniqueId(), locations);
            } else if (!pm.isBuilding(player.getUniqueId())) {
                for (Location location : cache.get(player.getUniqueId())) {
                    player.sendBlockChange(location, location.getBlock().getBlockData());
                }
                scheduler.cancel();
            }
        }, 5L, 1L));
        return this;
    }
}
