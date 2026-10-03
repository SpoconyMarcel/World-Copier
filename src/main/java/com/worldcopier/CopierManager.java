package com.worldcopier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.Chunk;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CopierManager {

    /** Jedna sesja kopiowania (własny wątek IO i własne pliki regionów). */
    private static final class Session {
        final Path root;
        final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WorldCopier-IO");
            t.setDaemon(true);
            return t;
        });
        final Map<RegistryKey<World>, RegionStore> stores = new HashMap<>(); // tylko wątek IO
        final Map<RegistryKey<World>, Set<Long>> saved = new HashMap<>();    // tylko wątek klienta

        Session(Path root) { this.root = root; }

        void write(RegistryKey<World> dim, int x, int z, NbtCompound nbt) {
            try {
                stores.computeIfAbsent(dim, k -> new RegionStore(dimDir(root, k).resolve("region"))).put(x, z, nbt);
            } catch (Exception e) {
                System.err.println("[WorldCopier] Błąd zapisu chunka: " + e);
            }
        }

        void flush() {
            for (RegionStore s : stores.values()) {
                try { s.flush(); } catch (Exception e) { System.err.println("[WorldCopier] Błąd flush: " + e); }
            }
        }
    }

    private static Session session;

    private CopierManager() {}

    public static boolean isEnabled() { return session != null; }

    public static int count() {
        if (session == null) return lastCount;
        int n = 0;
        for (Set<Long> s : session.saved.values()) n += s.size();
        return n;
    }

    private static int lastCount = 0;
    private static String lastPath = null;

    public static String rootPathString() {
        return session != null ? session.root.toString() : lastPath;
    }

    public static void toggle(MinecraftClient client) {
        if (isEnabled()) stop(client, true); else start(client);
    }

    private static void start(MinecraftClient client) {
        if (client.world == null) return;
        ServerInfo info = client.getCurrentServerEntry();
        String name = info != null ? info.address : "singleplayer";
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path root = FabricLoader.getInstance().getGameDir().resolve("world_copier").resolve(name);

        session = new Session(root);
        Session s = session;
        s.io.scheduleWithFixedDelay(s::flush, 5, 5, TimeUnit.SECONDS);
        lastPath = root.toString();

        saveAllLoaded(client.world);
        chat(client, "Kopiowanie świata WŁĄCZONE. Folder: " + root, Formatting.GREEN);
    }

    private static void stop(MinecraftClient client, boolean announce) {
        Session s = session;
        if (s == null) return;
        if (client.world != null) saveAllLoaded(client.world);
        lastCount = count();
        session = null;
        s.io.execute(s::flush);
        s.io.shutdown(); // dokończy zakolejkowane zapisy, potem zakończy wątek
        if (announce) {
            chat(client, "Kopiowanie świata WYŁĄCZONE. Zapisano chunków: " + lastCount, Formatting.RED);
        } else {
            System.out.println("[WorldCopier] Rozłączono. Zapisano chunków: " + lastCount);
        }
    }

    public static void onDisconnect(MinecraftClient client) {
        if (session != null) stop(client, false);
    }

    public static void onChunk(ClientWorld world, WorldChunk chunk) {
        if (session != null) save(world, chunk);
    }

    private static void saveAllLoaded(ClientWorld world) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        int r = mc.options.getViewDistance().getValue() + 2;
        int px = mc.player.getChunkPos().x;
        int pz = mc.player.getChunkPos().z;
        for (int x = px - r; x <= px + r; x++) {
            for (int z = pz - r; z <= pz + r; z++) {
                Chunk c = world.getChunk(x, z, ChunkStatus.FULL, false);
                if (c instanceof WorldChunk wc) save(world, wc);
            }
        }
    }

    private static void save(ClientWorld world, WorldChunk chunk) {
        Session s = session;
        if (s == null) return;
        try {
            NbtCompound nbt = ChunkSerializerLite.serialize(world, chunk);
            RegistryKey<World> dim = world.getRegistryKey();
            int x = chunk.getPos().x, z = chunk.getPos().z;
            s.saved.computeIfAbsent(dim, k -> new HashSet<>()).add(chunk.getPos().toLong());
            s.io.execute(() -> s.write(dim, x, z, nbt));
        } catch (Exception e) {
            System.err.println("[WorldCopier] Błąd serializacji chunka: " + e);
        }
    }

    private static Path dimDir(Path root, RegistryKey<World> key) {
        if (key == World.OVERWORLD) return root;
        if (key == World.NETHER) return root.resolve("DIM-1");
        if (key == World.END) return root.resolve("DIM1");
        Identifier id = key.getValue();
        return root.resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath());
    }

    private static void chat(MinecraftClient client, String msg, Formatting color) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[World Copier] " + msg).formatted(color), false);
        }
    }
}
