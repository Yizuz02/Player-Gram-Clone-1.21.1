package net.yizuz.playerogrammod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.yizuz.playerogrammod.ai.AiService;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(PlayerOGramMod.MOD_ID)
public class PlayerOGramMod {
    public static final String MOD_ID = "playerogrammod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PlayerOGramMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // React to config file edits without requiring a restart
        modEventBus.addListener(this::onConfigEvent);

        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        AiService.get().init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
    }

    private void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            AiService.get().onConfigChanged();
        }
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        AiService.get().onServerStarted();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        AiService.get().onServerStopping();
    }
}
