package net.iso2013.mlapi.listener;

import com.google.common.base.Preconditions;
import net.iso2013.mlapi.MultiLineAPIPlugin;
import net.iso2013.mlapi.VisibilityStates;
import net.iso2013.mlapi.renderer.TagRenderer;
import net.iso2013.peapi.api.PacketEntityAPI;
import net.iso2013.peapi.api.entity.RealEntityIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Created by iso2013 on 6/7/2018.
 */
public class ServerListener implements Listener {

    private final MultiLineAPIPlugin parent;
    private final VisibilityStates states;
    private final PacketEntityAPI packet;

    public ServerListener(MultiLineAPIPlugin parent, VisibilityStates states, PacketEntityAPI packet) {
        Preconditions.checkArgument(parent != null, "MLAPI instance must not be null");
        Preconditions.checkArgument(states != null, "VisibilityState instance must not be null");
        Preconditions.checkArgument(packet != null, "PacketEntityAPI instance must not be null");

        this.parent = parent;
        this.states = states;
        this.packet = packet;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent e) {
        this.onSpawn(e.getPlayer());
    }

    @EventHandler
    public void onLogin(PlayerRespawnEvent e) {
        this.onSpawn(e.getPlayer());
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        this.onSpawn(e.getEntity());
    }

    @EventHandler
    public void onSpawn(ItemSpawnEvent e) {
        this.onSpawn(e.getEntity());
    }
    
    @EventHandler
    public void onToggle(PlayerToggleSneakEvent e) {
    	if (e.isSneaking()) {
    		this.onDespawn(e.getPlayer());
    	} else {
    		this.onSpawn(e.getPlayer());
    	}
    }

    @EventHandler
    public void onSpawn(SpawnerSpawnEvent e) {
        this.onSpawn(e.getEntity());
    }

    @EventHandler
    public void onSpawn(ProjectileLaunchEvent e) {
        this.onSpawn(e.getEntity());
    }

    @EventHandler
    public void onSpawn(VehicleCreateEvent e) {
        this.onSpawn(e.getVehicle());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        loadChunkEntities(e.getChunk());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.states.purge(e.getPlayer());
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent e) {
        for (Entity entity : e.getChunk().getEntities()) {
            this.onDespawn(entity);
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        if (e.getNewGameMode() == GameMode.SPECTATOR && e.getPlayer().getGameMode() != GameMode.SPECTATOR) {
            // Player is changing into SPECTATOR, so we should despawn all tags.
            TagRenderer.batchDestroyTags(packet, states.getVisible(e.getPlayer()), e.getPlayer());
        } else if (e.getNewGameMode() != GameMode.SPECTATOR && e.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            // Player is changing out of SPECTATOR, so we should spawn all tags.
            this.packet.getVisible(e.getPlayer(), 1, false)
                    .map(identifier -> {
                        if (identifier instanceof RealEntityIdentifier) {
                            return parent.getTag(((RealEntityIdentifier) identifier).getEntity());
                        }
                        return null;
                    }).filter(tag -> {
                        Boolean visible = states.isVisible(tag, e.getPlayer());
                return tag != null && ((visible != null) ? visible : tag.getDefaultVisible());
                    }).forEach(tag -> tag.getRenderer().spawnTag(tag, e.getPlayer(), null));
        }
    }

    private void onSpawn(Entity e) {
        if (parent.hasDefaultTagControllers(e.getType())) {
            this.parent.getOrCreateTag(e, false);
        }
    }

    private void onDespawn(Entity e) {
        Bukkit.getScheduler().runTaskLater(parent, () -> parent.deleteTag(e), 1);
    }

    public void loadAllWorldEntities() {
        for (World w : Bukkit.getWorlds()) loadWorldEntities(w);
    }

    private void loadWorldEntities(World w) {
        for (Chunk c : w.getLoadedChunks()) loadChunkEntities(c);
    }

    private void loadChunkEntities(Chunk c) {
        for (Entity entity : c.getEntities()) {
            if (!entity.isValid()) continue;
            this.onSpawn(entity);
        }
    }
}
