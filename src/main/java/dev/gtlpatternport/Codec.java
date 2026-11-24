package dev.gtlpatternport;

import net.minecraft.nbt.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.zip.*;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;

public final class Codec {
    public static final int MAX_FILE = 1024 * 1024, MAX_RAW = 4 * 1024 * 1024;
    public static byte[] encode(CompoundTag payload) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("format", 1);
        root.putString("source", "gtlcore:me_pattern_buffer_copy");
        root.put("payload", payload.copy());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        if (out.size() > MAX_FILE) throw new IOException("文件超过 1 MiB 限制");
        byte[] bytes = out.toByteArray();
        decode(bytes); // Apply the same limits on export and import.
        return bytes;
    }
    public static CompoundTag decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_FILE) throw new IOException("文件超过 1 MiB 限制");
        byte[] raw;
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            raw = gzip.readNBytes(MAX_RAW + 1);
        }
        if (raw.length > MAX_RAW) throw new IOException("解压数据超过 4 MiB 限制");
        CompoundTag root = NbtIo.read(new DataInputStream(new ByteArrayInputStream(raw)), new NbtAccounter(16L * MAX_RAW));
        if (root == null || root.getInt("format") != 1 || !root.getString("source").equals("gtlcore:me_pattern_buffer_copy") || !root.contains("payload", Tag.TAG_COMPOUND))
            throw new IOException("不是受支持的样板文件（格式版本 1）");
        CompoundTag p = root.getCompound("payload");
        if (!p.contains("patterns", Tag.TAG_LIST) || !p.contains("name", Tag.TAG_STRING)) throw new IOException("缺少样板列表或名称");
        ListTag list = p.getList("patterns", Tag.TAG_COMPOUND);
        if (list.size() != ((ListTag)p.get("patterns")).size() || list.size() > 4096) throw new IOException("样板列表类型错误或超过 4096 个");
        if (p.getString("name").length() > 1024) throw new IOException("名称过长");
        CompoundTag clean = new CompoundTag();
        clean.putString("name", p.getString("name"));
        ListTag patterns = new ListTag();
        for (Tag t : list) {
            CompoundTag entry = (CompoundTag)t;
            if (!entry.contains("pattern", Tag.TAG_COMPOUND) || !entry.contains("cacheCount", Tag.TAG_BYTE) || entry.getByte("cacheCount") <= 0)
                throw new IOException("样板结构或缓存数量无效");
            CompoundTag stack = entry.getCompound("pattern");
            if (!stack.contains("id", Tag.TAG_STRING) || stack.getByte("Count") != 1) throw new IOException("样板物品无效");
            CompoundTag copy = new CompoundTag();
            copy.put("pattern", stack.copy());
            copy.putByte("cacheCount", entry.getByte("cacheCount"));
            patterns.add(copy);
        }
        clean.put("patterns", patterns);
        return clean;
    }
    public static byte[] fingerprint(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            canonical(tag, new DataOutputStream(out));
            return MessageDigest.getInstance("SHA-256").digest(out.toByteArray());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static void canonical(Tag tag, DataOutputStream out) throws IOException {
        out.writeByte(tag.getId());
        if (tag instanceof CompoundTag compound) {
            var keys = new java.util.ArrayList<>(compound.getAllKeys());
            java.util.Collections.sort(keys);
            out.writeInt(keys.size());
            for (String key : keys) { out.writeUTF(key); canonical(compound.get(key), out); }
        } else if (tag instanceof ListTag list) {
            out.writeInt(list.size());
            for (Tag child : list) canonical(child, out);
        } else {
            byte[] bytes = tag.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.writeInt(bytes.length); out.write(bytes);
        }
    }
    public static void checkItemSize(CompoundTag tag) throws IOException {
        checkItemSize(tag, FriendlyByteBuf::readNbt);
    }
    @FunctionalInterface interface NetworkReader { CompoundTag read(FriendlyByteBuf buffer); }
    static void checkItemSize(CompoundTag tag, NetworkReader reader) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(out));
        if (out.size() > 1024 * 1024) throw new IOException("工具数据超过 1 MiB 序列化限制，请分批导入");
        byte[] bytes = out.toByteArray();
        // Keep a bounded local allocation even if Packet Fixer permits unlimited NBT.
        NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)), new NbtAccounter(16L * 1024 * 1024));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            // Use the actual game's network reader, including any Packet Fixer mixins.
            if (!tag.equals(reader.read(buffer))) throw new IOException("工具数据同步校验不一致");
        } catch (RuntimeException e) {
            throw new IOException("超过当前游戏的 NBT 同步读取限制；请分批导入，或确保客户端与服务器的 Packet Fixer 设置一致", e);
        } finally { buffer.release(); }
    }
}
