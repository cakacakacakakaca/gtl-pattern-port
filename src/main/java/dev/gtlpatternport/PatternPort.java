package dev.gtlpatternport;

import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Mod(PatternPort.ID)
public final class PatternPort {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final String ID = "gtlpatternport", BEHAVIOURS = "GT.Behaviours";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(ID, "files"), () -> "2", "2"::equals, "2"::equals);
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    public PatternPort() {
        CHANNEL.messageBuilder(Chunk.class, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder(Chunk::encode).decoder(Chunk::decode).consumerMainThread((m, ctx) -> receive(m, ctx.get().getSender())).add();
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> UPLOADS.remove(e.getEntity().getUUID()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> { if(e.phase == TickEvent.Phase.END) UPLOADS.values().removeIf(u -> System.nanoTime() > u.deadline); });
    }
    public static boolean isTool(ItemStack stack) {
        return new ResourceLocation("gtlcore", "me_pattern_buffer_copy").equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
    public static CompoundTag payload(ItemStack stack) {
        CompoundTag b = stack.getTagElement(BEHAVIOURS);
        return b != null && b.contains("tag", Tag.TAG_COMPOUND) ? b.getCompound("tag").copy() : null;
    }
    public record Chunk(UUID transfer, int slot, byte[] fingerprint, int index, int total, byte[] bytes) {
        void encode(FriendlyByteBuf b) { b.writeUUID(transfer); b.writeInt(slot); b.writeByteArray(fingerprint); b.writeInt(index); b.writeInt(total); b.writeByteArray(bytes); }
        static Chunk decode(FriendlyByteBuf b) { return new Chunk(b.readUUID(), b.readInt(), b.readByteArray(32), b.readInt(), b.readInt(), b.readByteArray(24000)); }
    }
    private static final class Upload {
        final Chunk first;
        final long deadline = System.nanoTime() + 30_000_000_000L;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int next;
        Upload(Chunk first) { this.first = first; }
    }
    private static void receive(Chunk m, net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        try {
            if (m.slot < 0 || m.slot > 8 || player.getInventory().selected != m.slot || m.total < 1 || m.total > 44 || m.index < 0 || m.index >= m.total || m.fingerprint.length != 32)
                throw new Exception("传输参数无效或手持栏已改变");
            ItemStack stack = player.getMainHandItem();
            if (!isTool(stack) || !Arrays.equals(m.fingerprint, Codec.fingerprint(stack.save(new CompoundTag())))) throw new Exception("工具已改变，请重新打开界面");
            Upload u = UPLOADS.get(player.getUUID());
            if (m.index == 0) { u = new Upload(m); UPLOADS.put(player.getUUID(), u); }
            if (u == null || System.nanoTime() > u.deadline || !u.first.transfer.equals(m.transfer) || u.next != m.index || u.first.total != m.total || u.first.slot != m.slot || !Arrays.equals(u.first.fingerprint,m.fingerprint)) throw new Exception("传输已过期或顺序错误");
            if (u.bytes.size() + m.bytes.length > Codec.MAX_FILE) throw new Exception("样板文件过大");
            u.bytes.write(m.bytes); u.next++;
            if (u.next != m.total) return;
            UPLOADS.remove(player.getUUID());
            CompoundTag imported = Codec.decode(u.bytes.toByteArray());
            var validator = Class.forName("appeng.api.crafting.PatternDetailsHelper").getMethod("isEncodedPattern", ItemStack.class);
            for (Tag t : imported.getList("patterns", Tag.TAG_COMPOUND)) {
                ItemStack pattern = ItemStack.of(((CompoundTag)t).getCompound("pattern"));
                if (pattern.isEmpty() || !(Boolean)validator.invoke(null, pattern)) throw new Exception("文件包含缺失或非编码样板物品");
            }
            CompoundTag replacement = ImportData.prepare(stack.save(new CompoundTag()), imported);
            stack.setTag(replacement.getCompound("tag"));
            player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
            player.sendSystemMessage(Component.literal("样板导入成功：" + imported.getList("patterns", Tag.TAG_COMPOUND).size() + " 个。右键样板总成即可粘贴。"));
        } catch (Exception e) {
            LOGGER.warn("Pattern import rejected before replacing the held tool", e);
            UPLOADS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("样板导入失败，原数据未更改：" + e.getMessage()));
        }
    }
}
