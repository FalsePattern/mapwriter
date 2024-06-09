package mapwriter.region;

import com.falsepattern.endlessids.Hooks;
import com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook;
import lombok.RequiredArgsConstructor;
import lombok.val;
import mapwriter.forge.EndlessIDsCompat;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.Constants;

public interface BiomeStorage {
    int biomeAt(int offset);

    void serialize(NBTTagCompound compound);

    static BiomeStorage clone(Chunk chunk) {
        return EndlessIDsCompat.blocks()
               ? EndlessIDs.clone(chunk)
               : Vanilla.clone(chunk);
    }

    static BiomeStorage deserialize(NBTTagCompound compound) {
        return EndlessIDsCompat.blocks()
               ? EndlessIDs.deserialize(compound)
               : Vanilla.deserialize(compound);
    }

    @RequiredArgsConstructor
    class Vanilla implements BiomeStorage {
        private final byte[] biomeArray;

        @Override
        public int biomeAt(int offset) {
            return biomeArray[offset] & 0xFF;
        }

        @Override
        public void serialize(NBTTagCompound compound) {
            compound.setByteArray("Biomes", this.biomeArray);
        }

        public static Vanilla deserialize(NBTTagCompound compound) {
            return new Vanilla(compound.getByteArray("Biomes"));
        }

        public static Vanilla clone(Chunk chunk) {
            return new Vanilla(Util.nullSafeCopy(chunk.getBiomeArray()));
        }
    }

    @RequiredArgsConstructor
    class EndlessIDs implements BiomeStorage {
        private final short[] biomeArray;

        @Override
        public int biomeAt(int offset) {
            return biomeArray[offset] & 0xFFFF;
        }

        @Override
        public void serialize(NBTTagCompound compound) {
            compound.setByteArray("Biomes16v2", Hooks.shortToByteArray(biomeArray));
        }

        public static EndlessIDs deserialize(NBTTagCompound compound) {
            val biomes = new short[16 * 16];
            if (compound.hasKey("Biomes16v2", Constants.NBT.TAG_BYTE_ARRAY))
                Hooks.byteToShortArray(compound.getByteArray("Biomes16v2"), 0, biomes, 0, biomes.length * 2);
            else
                Hooks.scatter(compound.getByteArray("Biomes"), biomes);
            return new EndlessIDs(biomes);
        }

        public static EndlessIDs clone(Chunk chunk) {
            return new EndlessIDs(Util.nullSafeCopy(((ChunkBiomeHook)chunk).getBiomeShortArray()));
        }
    }
}
