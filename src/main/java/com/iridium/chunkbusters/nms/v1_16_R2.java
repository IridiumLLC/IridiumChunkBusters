package com.iridium.chunkbusters.nms;

import com.iridium.chunkbusters.IridiumChunkBusters;
import net.minecraft.server.v1_16_R2.*;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.*;
import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.shorts.ShortArraySet;
import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.shorts.ShortSet;
import org.bukkit.craftbukkit.v1_16_R2.CraftChunk;
import org.bukkit.craftbukkit.v1_16_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R2.legacy.CraftLegacy;
import org.bukkit.entity.Player;

import java.util.*;

public class v1_16_R2 implements NMS {

    static {
        CraftLegacy.init();
    }

    @Override
    public void setBlockFast(World world, int x, int y, int z, int blockId, byte data, boolean applyPhysics) {
        net.minecraft.server.v1_16_R2.World nmsWorld = ((CraftWorld) world).getHandle();
        net.minecraft.server.v1_16_R2.Chunk nmsChunk = nmsWorld.getChunkAt(x >> 4, z >> 4);
        IBlockData ibd = net.minecraft.server.v1_16_R2.Block.getByCombinedId(blockId + (data << 12));

        net.minecraft.server.v1_16_R2.ChunkSection cs = nmsChunk.getSections()[y >> 4];
        if (cs == null) {
            cs = new ChunkSection(y >> 4 << 4);
            nmsChunk.getSections()[y >> 4] = cs;
        }
        cs.setType(x & 15, y & 15, z & 15, ibd);
        nmsChunk.getWorld().getChunkProvider().getLightEngine().a(new BlockPosition(x, y, z));
    }

    @Override
    public void sendChunk(Chunk chunk, List<Location> blocks, List<Player> players) {
        net.minecraft.server.v1_16_R2.Chunk nmsChunk = ((CraftChunk) chunk).getHandle();
        Map<Integer, Set<Short>> changedBlocks = new HashMap<>();

        for (Location location : blocks) {
            Set<Short> shortSet = changedBlocks.computeIfAbsent(location.getBlockY() >> 4, i -> new ShortArraySet());
            shortSet.add((short) ((location.getBlockX() & 15) << 8 | (location.getBlockZ() & 15) << 4 | (location.getBlockY() & 15)));
        }

        Set<PacketPlayOutMultiBlockChange> packetsToSend = new HashSet<>();

        for (Map.Entry<Integer, Set<Short>> entry : changedBlocks.entrySet()) {
            PacketPlayOutMultiBlockChange packetPlayOutMultiBlockChange = new PacketPlayOutMultiBlockChange(SectionPosition.a(nmsChunk.getPos(), entry.getKey()), (ShortSet) entry.getValue(), nmsChunk.getSections()
                    [entry.getKey()], true);
            packetsToSend.add(packetPlayOutMultiBlockChange);
        }

        players.forEach(player -> {
            EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
            packetsToSend.forEach(packet -> entityPlayer.playerConnection.sendPacket(packet));
        });
    }

    @Override
    public void sendChunk(Chunk chunk, List<Player> players) {
        Bukkit.getScheduler().runTaskAsynchronously(IridiumChunkBusters.getInstance(), () -> players.forEach(player -> {
            PacketPlayOutMapChunk packetPlayOutMapChunk = new PacketPlayOutMapChunk(((CraftChunk) chunk).getHandle(), 65535);
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packetPlayOutMapChunk);
        }));
    }

    @Override
    public void sendActionBar(Player player, String message) {
        IChatBaseComponent iChatBaseComponent = IChatBaseComponent.ChatSerializer.a(ChatColor.translateAlternateColorCodes('&', "{\"text\":\"" + message + "\"}"));
        PacketPlayOutTitle packetPlayOutTitle = new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.ACTIONBAR, iChatBaseComponent, 0, 20, 0);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packetPlayOutTitle);
    }
}
