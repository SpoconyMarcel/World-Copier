package com.worldcopier;

import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;
import net.minecraft.world.chunk.WorldChunk;

/** Uproszczona serializacja chunka klienta do formatu Anvil (1.18+ / 1.21.1). */
public final class ChunkSerializerLite {

    private static class Holder {
        static final Codec<PalettedContainer<BlockState>> BLOCKS = PalettedContainer.createPalettedContainerCodec(
                Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE,
                Blocks.AIR.getDefaultState());
    }

    private ChunkSerializerLite() {}

    public static NbtCompound serialize(ClientWorld world, WorldChunk chunk) {
        Registry<Biome> biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
        Codec<ReadableContainer<RegistryEntry<Biome>>> biomeCodec = PalettedContainer.createReadableContainerCodec(
                biomes.getIndexedEntries(), biomes.getEntryCodec(), PalettedContainer.PaletteProvider.BIOME,
                biomes.entryOf(BiomeKeys.PLAINS));

        ChunkPos pos = chunk.getPos();
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());
        root.putInt("xPos", pos.x);
        root.putInt("yPos", world.getBottomSectionCoord());
        root.putInt("zPos", pos.z);
        root.putLong("LastUpdate", 0L);
        root.putLong("InhabitedTime", 0L);
        root.putString("Status", "minecraft:full");
        root.putBoolean("isLightOn", false); // światło zostanie przeliczone przy wczytaniu

        NbtList sections = new NbtList();
        ChunkSection[] arr = chunk.getSectionArray();
        for (int i = 0; i < arr.length; i++) {
            ChunkSection section = arr[i];
            NbtCompound s = new NbtCompound();
            s.putByte("Y", (byte) chunk.sectionIndexToCoord(i));
            NbtElement blocks = Holder.BLOCKS.encodeStart(NbtOps.INSTANCE, section.getBlockStateContainer())
                    .result().orElseThrow();
            NbtElement bio = biomeCodec.encodeStart(NbtOps.INSTANCE, section.getBiomeContainer())
                    .result().orElseThrow();
            s.put("block_states", blocks);
            s.put("biomes", bio);
            sections.add(s);
        }
        root.put("sections", sections);

        NbtList blockEntities = new NbtList();
        for (BlockPos bp : chunk.getBlockEntityPositions()) {
            BlockEntity be = chunk.getBlockEntity(bp);
            if (be != null) {
                blockEntities.add(be.createNbtWithIdentifyingData(world.getRegistryManager()));
            }
        }
        root.put("block_entities", blockEntities);
        return root;
    }
}
