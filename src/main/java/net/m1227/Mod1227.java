package net.m1227;

import java.io.File;
import net.m1227.core.CombatTracker;
import net.m1227.core.Command1227;
import net.m1227.core.InputManager;
import net.m1227.core.ConfigManager;
import net.m1227.core.ModuleManager;
import net.m1227.vfx.ParticleSystem;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1227: a client-side, purely visual mod. It never sends packets,
 * never draws through walls and never changes how the game plays.
 */
@Mod(modid = Mod1227.MODID, name = Mod1227.NAME, version = Mod1227.VERSION,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class Mod1227 {
    public static final String MODID = "m1227";
    public static final String NAME = "1227";
    public static final String VERSION = "1.18.0";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    /** Forge's config folder (set in preInit, before modules are created). */
    public static File configDir;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        configDir = e.getModConfigurationDirectory();
        // Item Physics: our renderer replaces the dropped-item renderer, and draws exactly like
        // vanilla while the module is off
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                net.minecraft.entity.item.EntityItem.class,
                new net.minecraftforge.fml.client.registry.IRenderFactory<net.minecraft.entity.item.EntityItem>() {
                    @Override
                    public net.minecraft.client.renderer.entity.Render<? super net.minecraft.entity.item.EntityItem> createRenderFor(
                            net.minecraft.client.renderer.entity.RenderManager manager) {
                        return new net.m1227.render.PhysicsItemRender(manager, net.minecraft.client.Minecraft.getMinecraft().getRenderItem());
                    }
                });
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        ModuleManager.init();
        ConfigManager.init(configDir);
        net.m1227.core.Keybinds.INSTANCE.register();
        net.m1227.core.Notifier.ready = true;

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ParticleSystem.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new CombatTracker());
        MinecraftForge.EVENT_BUS.register(InputManager.INSTANCE);
        MinecraftForge.EVENT_BUS.register(net.m1227.core.Keybinds.INSTANCE);
        MinecraftForge.EVENT_BUS.register(net.m1227.hud.ClickCounter.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new net.m1227.vfx.BodyFx.Hider());
        MinecraftForge.EVENT_BUS.register(net.m1227.render.HandRenderer.INSTANCE);
        MinecraftForge.EVENT_BUS.register(net.m1227.render.Silhouette.INSTANCE);
        MinecraftForge.EVENT_BUS.register(net.m1227.vfx.BlockWave.INSTANCE);
        // client systems: menus, server history, performance, benchmark
        MinecraftForge.EVENT_BUS.register(net.m1227.client.menu.MenuSystem.INSTANCE);
        MinecraftForge.EVENT_BUS.register(net.m1227.client.menu.ServerStore.INSTANCE);
        MinecraftForge.EVENT_BUS.register(net.m1227.client.perf.PerfSystem.INSTANCE);
        MinecraftForge.EVENT_BUS.register(net.m1227.client.perf.Benchmark.INSTANCE);
        MinecraftForge.EVENT_BUS.register(net.m1227.vfx.CubeBurst.INSTANCE);
        ClientCommandHandler.instance.registerCommand(new Command1227());

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                ConfigManager.save();
            }
        }, "Mod1227-ConfigSave"));

        LOGGER.info("{} {} loaded with {} modules", NAME, VERSION, ModuleManager.all().size());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) ConfigManager.tick();
    }
}
