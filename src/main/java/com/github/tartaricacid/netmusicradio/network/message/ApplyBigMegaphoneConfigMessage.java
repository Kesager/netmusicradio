package com.github.tartaricacid.netmusicradio.network.message;

import com.github.tartaricacid.netmusic.tileentity.TileEntityBigMegaphone;
import com.github.tartaricacid.netmusicradio.NetMusicRadioAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * 自定义的大喇叭配置消息
 * <p>
 * 绕过 NetMusic 原始的 URL 验证限制，支持：
 * - 标准音频链接（.mp3, .ogg 等）
 * - Shoutcast/Icecast 流媒体
 * - 任何 HTTP(S) 音频直链
 * <p>
 * 修复：服务端处理后主动同步 TileEntity 数据到客户端，确保 NBT 读取正确
 */
public class ApplyBigMegaphoneConfigMessage {
    private final BlockPos pos;
    private final String url;
    private final String name;
    private final int range;
    private final Action action;

    public ApplyBigMegaphoneConfigMessage(BlockPos pos, String url, String name, int range, Action action) {
        this.pos = pos;
        this.url = url == null ? "" : url;
        this.name = name == null ? "" : name;
        this.range = range;
        this.action = action;
    }

    public static ApplyBigMegaphoneConfigMessage decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String url = buf.readUtf();
        String name = buf.readUtf();
        int range = buf.readVarInt();
        int actionIndex = buf.readVarInt();
        return new ApplyBigMegaphoneConfigMessage(pos, url, name, range, Action.byIndex(actionIndex));
    }

    public static void encode(ApplyBigMegaphoneConfigMessage message, FriendlyByteBuf buf) {
        buf.writeBlockPos(message.pos);
        buf.writeUtf(message.url);
        buf.writeUtf(message.name);
        buf.writeVarInt(message.range);
        buf.writeVarInt(message.action.ordinal());
    }

    public static void handle(ApplyBigMegaphoneConfigMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_SERVER) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> handleMessage(message, context));
        context.setPacketHandled(true);
    }

    private static void handleMessage(ApplyBigMegaphoneConfigMessage message, NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null) {
            NetMusicRadioAddon.LOGGER.warn("Rejected BigMegaphone config: no sender");
            return;
        }

        double distanceSqr = sender.distanceToSqr(Vec3.atCenterOf(message.pos));
        if (distanceSqr > 64) {
            NetMusicRadioAddon.LOGGER.warn("Rejected BigMegaphone config: sender too far ({} blocks) from {}",
                    Math.sqrt(distanceSqr), message.pos);
            return;
        }

        if (!(sender.level().getBlockEntity(message.pos) instanceof TileEntityBigMegaphone megaphone)) {
            NetMusicRadioAddon.LOGGER.warn("Rejected BigMegaphone config: no megaphone at {}", message.pos);
            return;
        }

        NetMusicRadioAddon.LOGGER.info("Processing BigMegaphone action {} at {} (URL: {})",
                message.action, message.pos, message.url);

        switch (message.action) {
            case STOP -> handleStop(megaphone, message);
            case START -> handleStart(megaphone, message);
            case SAVE -> handleSave(megaphone, message);
        }

        syncTileEntityToClient(sender, megaphone);
    }

    private static void handleStop(TileEntityBigMegaphone megaphone, ApplyBigMegaphoneConfigMessage message) {
        NetMusicRadioAddon.LOGGER.info("Stopping broadcast at {}", message.pos);
        megaphone.stopBroadcast();
    }

    private static void handleStart(TileEntityBigMegaphone megaphone, ApplyBigMegaphoneConfigMessage message) {
        boolean changed = megaphone.applyConfig(message.url, message.name, message.range);
        NetMusicRadioAddon.LOGGER.debug("Config applied, changed: {}", changed);

        startBroadcastWithoutValidation(megaphone);
    }

    private static void handleSave(TileEntityBigMegaphone megaphone, ApplyBigMegaphoneConfigMessage message) {
        boolean changed = megaphone.applyConfig(message.url, message.name, message.range);
        NetMusicRadioAddon.LOGGER.info("Config saved at {} (changed: {}, URL: {})",
                message.pos, changed, message.url);
    }

    /**
     * 主动发送 TileEntity 数据给客户端，确保 NBT 同步
     */
    private static void syncTileEntityToClient(ServerPlayer sender, TileEntityBigMegaphone megaphone) {
        try {
            ClientboundBlockEntityDataPacket updatePacket = ClientboundBlockEntityDataPacket.create(megaphone);
            sender.connection.send(updatePacket);
            NetMusicRadioAddon.LOGGER.debug("Synced TileEntity data to client at {}", megaphone.getBlockPos());
        } catch (Throwable t) {
            NetMusicRadioAddon.LOGGER.error("Failed to sync TileEntity to client", t);
        }
    }

    /**
     * 在不进行 URL 验证的情况下启动广播
     */
    private static void startBroadcastWithoutValidation(TileEntityBigMegaphone megaphone) {
        try {
            invokePrivateMethod(megaphone, "stopAllListeners");

            long sessionId = getPrivateField(megaphone, "sessionId", Long.class);
            setPrivateField(megaphone, "sessionId", sessionId + 1L);
            setPrivateField(megaphone, "broadcasting", true);
            megaphone.markDirty();

            invokePrivateMethod(megaphone, "refreshAudience");

            NetMusicRadioAddon.LOGGER.debug("Successfully started broadcast without URL validation");
        } catch (Throwable t) {
            NetMusicRadioAddon.LOGGER.error("Failed to start broadcast without validation", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPrivateField(Object instance, String fieldName, Class<T> type) throws ReflectiveOperationException {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(instance));
    }

    private static void setPrivateField(Object instance, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Object invokePrivateMethod(Object instance, String methodName, Class<?>... parameterTypes) throws ReflectiveOperationException {
        Method method = instance.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(instance);
    }

    public enum Action {
        SAVE,
        START,
        STOP;

        public static Action byIndex(int index) {
            if (index < 0 || index >= values().length) {
                return SAVE;
            }
            return values()[index];
        }
    }
}