package mapwriter.region;

public interface IChunk {
    long getBlockAndMetadataPacked(int x, int y, int z);

    int getBiome(int x, int z);

    int getLightValue(int x, int y, int z);

    int getMaxY();
}
