package com.github.tartaricacid.netmusicradio;

import com.github.tartaricacid.netmusicradio.client.api.AudioStreamHandlerRegistrar;
import com.github.tartaricacid.netmusicradio.network.NetMusicRadioNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

/**
 * NetMusic Radio Addon 主类
 * <p>
 * 功能：
 * 1. 初始化网络通信
 * 2. 注册自定义音频流处理器
 * 3. 绕过 NetMusic 的 URL 验证限制
 */
@Mod(NetMusicRadioAddon.MODID)
public class NetMusicRadioAddon {
    public static final String MODID = "netmusic_radio_addon";
    public static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger(MODID);

    public NetMusicRadioAddon() {
        LOGGER.info("NetMusic Radio Addon initializing");
        NetMusicRadioNetwork.init();
    }

    /**
     * 客户端事件处理器
     * <p>
     * 在客户端加载完成后注册自定义音频流处理器
     */
    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {

        /**
         * 在 FMLLoadCompleteEvent 时注册自定义音频流处理器
         * <p>
         * 此时 NetMusic 的 AudioStreamHandlerManager 已经初始化完成
         * 可以安全地注册自定义处理器
         */
        @SubscribeEvent
        public static void onLoadComplete(FMLLoadCompleteEvent event) {
            LOGGER.info("FMLLoadCompleteEvent fired, registering audio stream handlers...");

            try {
                AudioStreamHandlerRegistrar.registerAll();
                LOGGER.info("Audio stream handlers registered successfully");
            } catch (Throwable t) {
                LOGGER.error("Failed to register audio stream handlers", t);
            }
        }
    }
}