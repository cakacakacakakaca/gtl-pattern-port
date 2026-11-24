package dev.gtlpatternport;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

@Mod.EventBusSubscriber(modid=PatternPort.ID, value=Dist.CLIENT)
public final class Client {
    private static final KeyMapping KEY = new KeyMapping("key.gtlpatternport.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.gtlpatternport");
    @Mod.EventBusSubscriber(modid=PatternPort.ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if(e.phase != TickEvent.Phase.END) return;
        while(KEY.consumeClick()) if(mc.screen == null && mc.player != null && PatternPort.isTool(mc.player.getMainHandItem())) mc.setScreen(new FilesScreen());
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent e) {
        if(PatternPort.isTool(e.getItemStack())) e.getToolTip().add(Component.translatable("tooltip.gtlpatternport.open", KEY.getTranslatedKeyMessage()));
    }
    static Path directory() throws IOException {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("gtl-patterns");
        Files.createDirectories(dir); return dir.toRealPath();
    }
    static Path file(String name) throws IOException {
        if(!name.matches("[\\p{L}\\p{N}_-]{1,64}") || name.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])")) throw new IOException("文件名限 1–64 位文字、数字、下划线、短横线");
        Path result = directory().resolve(name + ".gtlp");
        if(Files.isSymbolicLink(result)) throw new IOException("不支持符号链接文件");
        return result;
    }
    static byte[] read(Path p) throws IOException {
        try(var in = Files.newInputStream(p)) {
            byte[] bytes = in.readNBytes(Codec.MAX_FILE + 1);
            if(bytes.length > Codec.MAX_FILE) throw new IOException("文件超过 1 MiB");
            return bytes;
        }
    }
    static final class FilesScreen extends Screen {
        private EditBox name;
        private String status = "导入会替换工具中保存的样板；不会直接修改机器";
        private int page;
        FilesScreen() { super(Component.literal("样板文件管理")); }
        @Override protected void init() {
            int x = width / 2 - 150, y = height / 2 - 95;
            String old = name == null ? "patterns" : name.getValue();
            name = new EditBox(font, x, y + 22, 300, 20, Component.literal("文件名"));
            name.setMaxLength(64); name.setValue(old); addRenderableWidget(name);
            addRenderableWidget(Button.builder(Component.literal("导出当前样板"), b -> export(false)).bounds(x, y+48, 145, 20).build());
            addRenderableWidget(Button.builder(Component.literal("导入所选文件"), b -> preview()).bounds(x+155, y+48,145,20).build());
            addRenderableWidget(Button.builder(Component.literal("打开文件夹"), b -> { try { net.minecraft.Util.getPlatform().openFile(directory().toFile()); } catch(Exception ex) { status=ex.getMessage(); }}).bounds(x,y+74,145,20).build());
            addRenderableWidget(Button.builder(Component.literal("导出旧版遗留备份"), b -> export(true)).bounds(x+155,y+74,145,20).build());
            try {
                List<String> files;
                try(var stream = Files.list(directory())) { files = stream.filter(p -> p.getFileName().toString().endsWith(".gtlp")).map(p -> p.getFileName().toString().replaceFirst("\\.gtlp$", "")).sorted().toList(); }
                int pages = Math.max(1, (files.size()+3)/4); page = Math.min(page, pages-1);
                for(int i=page*4;i<Math.min(files.size(),page*4+4);i++) {
                    String f=files.get(i);
                    addRenderableWidget(Button.builder(Component.literal(f),b -> name.setValue(f)).bounds(x,y+102+(i%4)*22,300,20).build());
                }
                addRenderableWidget(Button.builder(Component.literal("上一页"),b -> {page=Math.max(0,page-1); rebuildWidgets();}).bounds(x,y+194,90,20).build());
                addRenderableWidget(Button.builder(Component.literal("下一页"),b -> {page=Math.min(pages-1,page+1); rebuildWidgets();}).bounds(x+105,y+194,90,20).build());
                addRenderableWidget(Button.builder(Component.literal("关闭"),b -> onClose()).bounds(x+210,y+194,90,20).build());
            } catch(Exception ex) { status=ex.getMessage(); }
        }
        private ItemStack tool() throws IOException {
            if(minecraft.player == null || !PatternPort.isTool(minecraft.player.getMainHandItem())) throw new IOException("请在主手拿着样板总成复制工具");
            return minecraft.player.getMainHandItem();
        }
        private void export(boolean backup) {
            try {
                ItemStack stack=tool();
                CompoundTag p=backup ? ImportData.backup(stack.getTag()) : PatternPort.payload(stack);
                if(p==null) throw new IOException(backup ? "没有导入前备份" : "工具尚未复制样板");
                byte[] bytes=Codec.encode(p);
                Path dest=file(name.getValue());
                // CREATE_NEW deliberately refuses replacement, preserving existing exports.
                Files.write(dest,bytes,StandardOpenOption.CREATE_NEW);
                status="已导出 " + dest.getFileName() + "（" + p.getList("patterns",Tag.TAG_COMPOUND).size() + " 个样板）";
                rebuildWidgets();
            } catch(FileAlreadyExistsException ex) {status="同名文件已存在，请更换文件名";}
            catch(Exception ex) {status="导出失败："+ex.getMessage();}
        }
        private void preview() {
            try {
                ItemStack stack=tool();
                byte[] bytes=read(file(name.getValue()));
                CompoundTag p=Codec.decode(bytes);
                ImportData.prepare(stack.save(new CompoundTag()), p); // Check this client's actual network limit too.
                int slot=minecraft.player.getInventory().selected;
                byte[] fingerprint=Codec.fingerprint(stack.save(new CompoundTag()));
                minecraft.setScreen(new ConfirmScreen(ok -> {
                    minecraft.setScreen(this);
                    if(!ok) return;
                    try {
                        if(!PatternPort.CHANNEL.isRemotePresent(minecraft.getConnection().getConnection())) throw new IOException("服务器未安装附属模组");
                        int total=(bytes.length+23999)/24000;
                        UUID id=UUID.randomUUID();
                        for(int i=0;i<total;i++) PatternPort.CHANNEL.sendToServer(new PatternPort.Chunk(id,slot,fingerprint,i,total,Arrays.copyOfRange(bytes,i*24000,Math.min(bytes.length,(i+1)*24000))));
                        onClose();
                    } catch(Exception ex) {status="导入失败："+ex.getMessage();}
                },Component.literal("确认覆盖工具中保存的数据？"),Component.literal("文件："+name.getValue()+"；名称："+p.getString("name")+"；样板数："+p.getList("patterns",Tag.TAG_COMPOUND).size()+"。覆盖原数据并清除旧版备份，不生成新备份。需要保留时请先导出。")));
            } catch(Exception ex) {status="无法导入："+ex.getMessage();}
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
            renderBackground(g); super.render(g,mouseX,mouseY,delta);
            g.drawCenteredString(font,title,width/2,height/2-95,0xffffff);
            g.drawWordWrap(font,Component.literal(status),Math.max(8,width/2-190),height/2+128,380,0xffdd88);
        }
        @Override public boolean isPauseScreen() {return false;}
    }
}
