package com.github.tartaricacid.netmusicradio.network.message;

import com.github.tartaricacid.netmusic.tileentity.TileEntityBigMegaphone;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Supplier;

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
        if (sender == null || sender.distanceToSqr(Vec3.atCenterOf(message.pos)) > 64) {
            return;
        }
        if (!(sender.level().getBlockEntity(message.pos) instanceof TileEntityBigMegaphone megaphone)) {
            return;
        }

        boolean wasBroadcasting = megaphone.isBroadcasting();
        boolean changed = megaphone.applyConfig(message.url, message.name, message.range);

        if (message.action == Action.STOP) {
            megaphone.stopBroadcast();
            return;
        }

        if (message.action == Action.START || (changed && wasBroadcasting)) {
            startBroadcastWithoutValidation(megaphone);
        }
    }

    private static void startBroadcastWithoutValidation(TileEntityBigMegaphone megaphone) {
        try {
            // Replicate the broadcast restart process without NetMusic's URL suffix check.
            invokePrivateMethod(megaphone, "stopAllListeners");
            int sessionId = getPrivateField(megaphone, "sessionId", Integer.class);
            setPrivateField(megaphone, "sessionId", sessionId + 1);
            setPrivateField(megaphone, "broadcasting", true);
            megaphone.markDirty();
            invokePrivateMethod(megaphone, "refreshAudience");
        } catch (Throwable ignored) {
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
