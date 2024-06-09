package mapwriter.forge;

import java.io.File;

public class CommonProxy {
    public void preInit(File configFile) {
        EndlessIDsCompat.init();
    }

    public void load() {
    }

    public void postInit() {
    }
}
