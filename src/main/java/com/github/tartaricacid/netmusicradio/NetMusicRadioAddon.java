package com.github.tartaricacid.netmusicradio;

import com.github.tartaricacid.netmusicradio.network.NetMusicRadioNetwork;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(NetMusicRadioAddon.MODID)
public class NetMusicRadioAddon {
    public static final String MODID = "netmusic_radio_addon";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public NetMusicRadioAddon() {
        LOGGER.info("NetMusic Radio Addon initializing");
        NetMusicRadioNetwork.init();
    }
}
