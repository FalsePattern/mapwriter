package mapwriter;

import mapwriter.region.BiomeStorage;
import mapwriter.region.BlockStorage;
import mapwriter.region.MwChunk;
import mapwriter.tasks.SaveChunkTask;
import mapwriter.tasks.UpdateSurfaceChunksTask;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ChunkManager {
    private static final int VISIBLE_FLAG = 0x01;
    private static final int VIEWED_FLAG = 0x02;
    private final CircularHashMap<Chunk, Integer> chunkMap = new CircularHashMap<>();
    public Mw mw;
    private boolean closed = false;

    public ChunkManager(Mw mw) {
        this.mw = mw;
    }

    public synchronized void close() {
        this.closed = true;
        this.saveChunks();
        this.chunkMap.clear();
    }

    // create MwChunk from Minecraft chunk.
    // only MwChunk's should be used in the background thread.
    // make this a full copy of chunk data to prevent possible race conditions <-- done
    public static MwChunk copyToMwChunk(Chunk chunk) {

        BlockStorage blocks;
        BiomeStorage biomes;
        byte[][] lightingArray = new byte[16][];
        @SuppressWarnings("unchecked")
        Map<ChunkPosition, TileEntity> tileEntityMap = new HashMap<ChunkPosition, TileEntity>(chunk.chunkTileEntityMap);

        blocks = BlockStorage.clone(chunk);
        biomes = BiomeStorage.clone(chunk);
        ExtendedBlockStorage[] storageArrays = chunk.getBlockStorageArray();
        if (storageArrays != null) {
            for (ExtendedBlockStorage storage : storageArrays) {
                if (storage != null) {
                    int y = (storage.getYLocation() >> 4) & 0xf;
                    lightingArray[y] = (storage.getBlocklightArray() != null) ? Arrays.copyOf(storage.getBlocklightArray().data, storage.getBlocklightArray().data.length) : null;
                }
            }
        }

        return new MwChunk(chunk.xPosition, chunk.zPosition, chunk.worldObj.provider.dimensionId,
                           blocks, biomes, lightingArray, tileEntityMap);
    }

    public synchronized void addChunk(Chunk chunk) {
        if (!this.closed && (chunk != null)) {
            this.chunkMap.put(chunk, 0);
        }
    }

    public synchronized void removeChunk(Chunk chunk) {
        if (!this.closed && (chunk != null)) {
            if (!this.chunkMap.containsKey(chunk)) return; //FIXME: Is this failsafe enough for unloading?
            int flags = this.chunkMap.get(chunk);
            if ((flags & VIEWED_FLAG) != 0) {
                this.addSaveChunkTask(chunk);
            }
            this.chunkMap.remove(chunk);
        }
    }

    public synchronized void saveChunks() {
        for (Map.Entry<Chunk, Integer> entry : this.chunkMap.entrySet()) {
            int flags = entry.getValue();
            if ((flags & VIEWED_FLAG) != 0) {
                this.addSaveChunkTask(entry.getKey());
            }
        }
    }

    public void updateUndergroundChunks() {
        int chunkArrayX = (this.mw.playerXInt >> 4) - 1;
        int chunkArrayZ = (this.mw.playerZInt >> 4) - 1;
        MwChunk[] chunkArray = new MwChunk[9]; //FIXME: useless method?
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                Chunk chunk = this.mw.mc.theWorld.getChunkFromChunkCoords(
                        chunkArrayX + x,
                        chunkArrayZ + z
                );
                if (!chunk.isEmpty()) {
                    chunkArray[(z * 3) + x] = copyToMwChunk(chunk);
                }
            }
        }
    }

    public void updateSurfaceChunks() {
        int chunksToUpdate = Math.min(this.chunkMap.size(), this.mw.chunksPerTick);
        MwChunk[] chunkArray = new MwChunk[chunksToUpdate];
        for (int i = 0; i < chunksToUpdate; i++) {
            Map.Entry<Chunk, Integer> entry = this.chunkMap.getNextEntry();
            if (entry != null) {
                // if this chunk is within a certain distance to the player then
                // add it to the viewed set
                Chunk chunk = entry.getKey();

                int flags = entry.getValue();
                if (MwUtil.distToChunkSq(this.mw.playerXInt, this.mw.playerZInt, chunk) <= this.mw.maxChunkSaveDistSq) {
                    flags |= (VISIBLE_FLAG | VIEWED_FLAG);
                } else {
                    flags &= ~VISIBLE_FLAG;
                }
                entry.setValue(flags);

                if ((flags & VISIBLE_FLAG) != 0) {
                    chunkArray[i] = copyToMwChunk(chunk);
                } else {
                    chunkArray[i] = null;
                }
            }
        }

        this.mw.executor.addTask(new UpdateSurfaceChunksTask(this.mw, chunkArray));
    }

    public void onTick() {
        if (!this.closed) {
            if ((this.mw.tickCounter & 0xf) == 0) {
                this.updateUndergroundChunks();
            } else {
                this.updateSurfaceChunks();
            }
        }
    }

    public void forceChunks(MwChunk[] chunkArray) {
        this.mw.executor.addTask(new UpdateSurfaceChunksTask(this.mw, chunkArray));
    }

    private void addSaveChunkTask(Chunk chunk) {
        if ((this.mw.multiplayer && this.mw.regionFileOutputEnabledMP) ||
                (!this.mw.multiplayer && this.mw.regionFileOutputEnabledSP)) {
            if (!chunk.isEmpty()) {
                this.mw.executor.addTask(new SaveChunkTask(copyToMwChunk(chunk), this.mw.regionManager));
            }
        }
    }
}