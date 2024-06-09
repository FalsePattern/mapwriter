package mapwriter.region;


import com.falsepattern.endlessids.mixin.helpers.SubChunkBlockHook;
import lombok.RequiredArgsConstructor;
import lombok.val;
import mapwriter.forge.EndlessIDsCompat;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.Constants;

import static mapwriter.region.Util.fetchSafe;
import static mapwriter.region.Util.fetchSafeNibble;
import static mapwriter.region.Util.nullSafeCopy;

public interface BlockStorage {
    int idAt(int subchunk, int offset);
    int metaAt(int subchunk, int offset);
    boolean hasSubChunk(int subchunk);
    void serialize(NBTTagCompound compound);

    static BlockStorage clone(Chunk chunk) {
        return EndlessIDsCompat.blocks()
               ? EndlessIDs.clone(chunk)
               : Vanilla.clone(chunk);
    }

    static BlockStorage deserialize(NBTTagCompound compound) {
        return EndlessIDsCompat.blocks()
               ? EndlessIDs.deserialize(compound)
               : Vanilla.deserialize(compound);
    }

    @RequiredArgsConstructor
    class Vanilla implements BlockStorage {
        private final byte[][] lsb;
        private final byte[][] msb;
        private final byte[][] meta;

        @Override
        public int idAt(int subchunk, int offset) {
            return fetchSafe(lsb, subchunk, offset) |
                   (fetchSafeNibble(msb, subchunk, offset) << 8);
        }

        @Override
        public int metaAt(int subchunk, int offset) {
            return fetchSafeNibble(meta, subchunk, offset);
        }

        @Override
        public boolean hasSubChunk(int subchunk) {
            return lsb != null && lsb[subchunk] != null;
        }

        @Override
        public void serialize(NBTTagCompound compound) {
            val nbtList = new NBTTagList();
            for (int y = 0; y < 16; ++y) {
                if (this.lsb[y] == null) {
                    continue;
                }
                val subchunk = new NBTTagCompound();
                subchunk.setByte("Y", (byte) y);
                subchunk.setByteArray("Blocks", this.lsb[y]);

                if ((this.msb != null) && (this.msb[y] != null)) {
                    subchunk.setByteArray("Add", this.msb[y]);
                }

                subchunk.setByteArray("Data", this.meta[y]);
                nbtList.appendTag(subchunk);
            }
            compound.setTag("Sections", nbtList);
        }

        public static Vanilla deserialize(NBTTagCompound compound) {
            val list = compound.getTagList("Sections", Constants.NBT.TAG_COMPOUND);
            byte[][] lsb = new byte[16][];
            byte[][] msb = new byte[16][];
            byte[][] meta = new byte[16][];
            for (int k = 0; k < list.tagCount(); k++) {
                val nbt = list.getCompoundTagAt(k);
                int y = nbt.getByte("Y") & 0xF;
                lsb[y] = nbt.getByteArray("Blocks");
                msb[y] = nbt.hasKey("Add") ? nbt.getByteArray("Add") : null;
                meta[y] = nbt.getByteArray("Data");
            }
            return new Vanilla(lsb, msb, meta);
        }

        public static Vanilla clone(Chunk chunk) {
            byte[][] lsb = new byte[16][];
            byte[][] msb = new byte[16][];
            byte[][] meta = new byte[16][];

            val storageArrays = chunk.getBlockStorageArray();
            if (storageArrays != null) {
                for (val storage: storageArrays) {
                    if (storage != null) {
                        int y = (storage.getYLocation() >> 4) & 0xf;
                        lsb[y] = nullSafeCopy(storage.getBlockLSBArray());
                        msb[y] = nullSafeCopy(storage.getBlockMSBArray());
                        meta[y] = nullSafeCopy(storage.getMetadataArray());
                    }
                }
            }
            return new Vanilla(lsb, msb, meta);
        }
    }

    @RequiredArgsConstructor
    class EndlessIDs implements BlockStorage {
        private final byte[][] b1;
        private final byte[][] b2Low;
        private final byte[][] b2High;
        private final byte[][] b3;
        private final byte[][] m1Low;
        private final byte[][] m1High;
        private final byte[][] m2;

        @Override
        public int idAt(int subchunk, int offset) {
            return fetchSafe(b1, subchunk, offset) |
                   (fetchSafeNibble(b2Low, subchunk, offset) << 8) |
                   (fetchSafeNibble(b2High, subchunk, offset) << 12) |
                   (fetchSafe(b3, subchunk, offset) << 16);
        }

        @Override
        public int metaAt(int subchunk, int offset) {
            return fetchSafeNibble(m1Low, subchunk, offset) |
                   (fetchSafeNibble(m1High, subchunk, offset) << 4) |
                   (fetchSafe(m2, subchunk, offset) << 8);
        }

        @Override
        public boolean hasSubChunk(int subchunk) {
            return b1 != null && b1[subchunk] != null;
        }

        public static EndlessIDs deserialize(NBTTagCompound compound) {
            val list = compound.getTagList("Sections", Constants.NBT.TAG_COMPOUND);
            byte[][] b1 = new byte[16][];
            byte[][] b2Low = new byte[16][];
            byte[][] b2High = new byte[16][];
            byte[][] b3 = new byte[16][];
            byte[][] m1Low = new byte[16][];
            byte[][] m1High = new byte[16][];
            byte[][] m2 = new byte[16][];
            for (int y = 0; y < 16; y++) {
                val nbt = list.getCompoundTagAt(y);
                b1[y] = nbt.getByteArray("Blocks");
                b2Low[y] = nbt.hasKey("Add") ? nbt.getByteArray("Add") : null;
                b2High[y] = nbt.hasKey("BlocksB2Hi") ? nbt.getByteArray("BlocksB2Hi") : null;
                b3[y] = nbt.hasKey("BlocksB3") ? nbt.getByteArray("BlocksB3") : null;
                m1Low[y] = nbt.getByteArray("Data");
                m1High[y] = nbt.hasKey("Data1High") ? nbt.getByteArray("Data1High") : null;
                m2[y] = nbt.hasKey("Data2") ? nbt.getByteArray("Data2") : null;
            }
            return new EndlessIDs(b1, b2Low, b2High, b3, m1Low, m1High, m2);
        }


        public static EndlessIDs clone(Chunk chunk) {
            byte[][] b1 = new byte[16][];
            byte[][] b2Low = new byte[16][];
            byte[][] b2High = new byte[16][];
            byte[][] b3 = new byte[16][];
            byte[][] m1Low = new byte[16][];
            byte[][] m1High = new byte[16][];
            byte[][] m2 = new byte[16][];

            val storageArrays = chunk.getBlockStorageArray();
            if (storageArrays != null) {
                for (val vStorage: storageArrays) {
                    if (vStorage != null) {
                        int y = (vStorage.getYLocation() >> 4) & 0xf;
                        val storage = (SubChunkBlockHook) vStorage;
                        b1[y] = nullSafeCopy(storage.getB1());
                        b2Low[y] = nullSafeCopy(storage.getB2Low());
                        b2High[y] = nullSafeCopy(storage.getB2High());
                        b3[y] = nullSafeCopy(storage.getB3());
                        m1Low[y] = nullSafeCopy(storage.getM1Low());
                        m1High[y] = nullSafeCopy(storage.getM1High());
                        m2[y] = nullSafeCopy(storage.getM2());
                    }
                }
            }
            return new EndlessIDs(b1, b2Low, b2High, b3, m1Low, m1High, m2);
        }

        @Override
        public void serialize(NBTTagCompound compound) {
            val nbtList = new NBTTagList();
            for (int y = 0; y < 16; ++y) {
                if (this.b1[y] == null) {
                    continue;
                }
                val subchunk = new NBTTagCompound();
                subchunk.setByte("Y", (byte) y);
                subchunk.setByteArray("Blocks", this.b1[y]);

                if ((this.b2Low != null) && (this.b2Low[y] != null)) {
                    subchunk.setByteArray("Add", this.b2Low[y]);
                }


                if ((this.b2High != null) && (this.b2High[y] != null)) {
                    subchunk.setByteArray("BlocksB2Hi", this.b2High[y]);
                }


                if ((this.b3 != null) && (this.b3[y] != null)) {
                    subchunk.setByteArray("BlocksB3", this.b3[y]);
                }

                subchunk.setByteArray("Data", this.m1Low[y]);


                if ((this.m1High != null) && (this.m1High[y] != null)) {
                    subchunk.setByteArray("Data1High", this.m1High[y]);
                }

                if ((this.m2 != null) && (this.m2[y] != null)) {
                    subchunk.setByteArray("Data2", this.m2[y]);
                }
                nbtList.appendTag(subchunk);
            }
            compound.setTag("Sections", nbtList);
        }
    }

}
