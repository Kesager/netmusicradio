package com.github.tartaricacid.netmusicradio.network;

import com.github.tartaricacid.netmusicradio.NetMusicRadioAddon;
import com.github.tartaricacid.netmusicradio.network.message.ApplyBigMegaphoneConfigMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class NetMusicRadioNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NetMusicRadioAddon.MODID, "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private NetMusicRadioNetwork() {
    }

    public static void init() {
        int index = 0;
        INSTANCE.registerMessage(index++, ApplyBigMegaphoneConfigMessage.class,
                ApplyBigMegaphoneConfigMessage::encode,
                ApplyBigMegaphoneConfigMessage::decode,
                ApplyBigMegaphoneConfigMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
