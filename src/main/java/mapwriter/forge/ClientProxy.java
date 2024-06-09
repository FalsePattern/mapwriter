package mapwriter.forge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import mapwriter.Mw;
import mapwriter.api.MwAPI;
import mapwriter.overlay.OverlayGrid;
import mapwriter.overlay.OverlaySlime;
import mapwriter.region.MwChunk;
import net.minecraftforge.common.MinecraftForge;

import java.io.File;

public class ClientProxy extends CommonProxy {

    private MwConfig config;

    public void preInit(File configFile) {
        super.preInit(configFile);
        this.config = new MwConfig(configFile);
    }

    public void load() {
        super.load();
        Mw mw = new Mw(this.config);
        MinecraftForge.EVENT_BUS.register(new EventHandler(mw));

        Object eventhandler = new MwKeyHandler();
        FMLCommonHandler.instance().bus().register(eventhandler);
        MinecraftForge.EVENT_BUS.register(eventhandler);
    }

    public void postInit() {
        super.postInit();
        if (Loader.isModLoaded("CarpentersBlocks")) {
            MwChunk.carpenterData();
        }
        if (Loader.isModLoaded("ForgeMultipart")) {
            MwChunk.fmpData();
        }
        MwAPI.registerDataProvider("Slime", new OverlaySlime());
        MwAPI.registerDataProvider("Grid", new OverlayGrid());
    }
}
