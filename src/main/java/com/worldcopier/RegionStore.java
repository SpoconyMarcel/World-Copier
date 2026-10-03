package com.worldcopier;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

/** Własny, minimalny zapis plików regionów .mca (Anvil). Używany tylko z jednego wątku. */
final class RegionStore {

    private static final class Region {
        final byte[][] data = new byte[1024][]; // surowe: [typ kompresji][dane]
        final int[] ts = new int[1024];
        boolean dirty;
    }

    private final Path dir;
    private final Map<Long, Region> regions = new HashMap<>();

    RegionStore(Path dir) { this.dir = dir; }

    void put(int cx, int cz, NbtCompound nbt) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(2); // zlib
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bos))) {
            NbtIo.write(nbt, out);
        }
        Region r = region(cx >> 5, cz >> 5);
        int idx = (cx & 31) + (cz & 31) * 32;
        r.data[idx] = bos.toByteArray();
        r.ts[idx] = (int) (System.currentTimeMillis() / 1000L);
        r.dirty = true;
    }

    void flush() throws IOException {
        for (Map.Entry<Long, Region> e : regions.entrySet()) {
            Region r = e.getValue();
            if (!r.dirty) continue;
            int rx = (int) (e.getKey() >> 32);
            int rz = (int) (long) e.getKey();
            write(rx, rz, r);
            r.dirty = false;
        }
    }

    private Path file(int rx, int rz) { return dir.resolve("r." + rx + "." + rz + ".mca"); }

    private Region region(int rx, int rz) throws IOException {
        long key = ((long) rx << 32) | (rz & 0xFFFFFFFFL);
        Region r = regions.get(key);
        if (r == null) {
            r = new Region();
            load(file(rx, rz), r);
            regions.put(key, r);
        }
        return r;
    }

    private void load(Path f, Region r) throws IOException {
        if (!Files.exists(f)) return;
        byte[] all = Files.readAllBytes(f);
        if (all.length < 8192) return;
        ByteBuffer buf = ByteBuffer.wrap(all);
        for (int i = 0; i < 1024; i++) {
            int loc = buf.getInt(i * 4);
            int offset = loc >>> 8, count = loc & 0xFF;
            if (offset == 0 || count == 0) continue;
            long pos = (long) offset * 4096;
            if (pos + 5 > all.length) continue;
            int len = buf.getInt((int) pos);
            byte comp = buf.get((int) pos + 4);
            if (len <= 1 || comp < 0 || pos + 4 + len > all.length) continue; // pomijamy zewnętrzne (.mcc)
            byte[] d = new byte[len];
            System.arraycopy(all, (int) pos + 4, d, 0, len);
            r.data[i] = d;
            r.ts[i] = buf.getInt(4096 + i * 4);
        }
    }

    private void write(int rx, int rz, Region r) throws IOException {
        Files.createDirectories(dir);
        ByteBuffer header = ByteBuffer.allocate(8192);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int sector = 2;
        for (int i = 0; i < 1024; i++) {
            byte[] d = r.data[i];
            if (d == null) continue;
            int sectors = (d.length + 4 + 4095) / 4096;
            if (sectors > 255) continue;
            header.putInt(i * 4, (sector << 8) | sectors);
            header.putInt(4096 + i * 4, r.ts[i]);
            ByteBuffer c = ByteBuffer.allocate(sectors * 4096);
            c.putInt(d.length);
            c.put(d);
            body.write(c.array());
            sector += sectors;
        }
        Path f = file(rx, rz);
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try (OutputStream o = Files.newOutputStream(tmp)) {
            o.write(header.array());
            body.writeTo(o);
        }
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
    }
}
