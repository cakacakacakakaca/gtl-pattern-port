package dev.gtlpatternport;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.io.*;
public class InstanceProbe {
    static long usage(CompoundTag item) throws Exception {
        var out=new ByteArrayOutputStream();NbtIo.write(item,new DataOutputStream(out));
        var counter=new NbtAccounter(64L*1024*1024);
        NbtIo.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray())),counter);
        return counter.getUsage();
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        try(var files=Files.list(root.resolve("gtl-patterns"))) {
            for(Path path:files.filter(p->p.toString().endsWith(".gtlp")).toList()) {
                var p=Codec.decode(Files.readAllBytes(path));var item=new CompoundTag();var tag=new CompoundTag();var b=new CompoundTag();
                b.put("tag",p);tag.put("GT.Behaviours",b);item.put("tag",tag);item.putString("id","gtlcore:me_pattern_buffer_copy");item.putByte("Count",(byte)1);
                System.out.println(path.getFileName()+" patterns="+p.getList("patterns",10).size()+" activeUsage="+usage(item));
                tag.putByteArray("gtlpatternport_backup",Codec.encode(p));System.out.println("  withCompressedBackup="+usage(item));
            }
        }
        var level=NbtIo.readCompressed(root.resolve("saves/新的世界/level.dat").toFile());
        for(Tag t:level.getCompound("Data").getCompound("Player").getList("Inventory",10)) {
            var item=(CompoundTag)t;
            if(item.getString("id").equals("gtlcore:me_pattern_buffer_copy")) {
                System.out.println("saved tool slot="+item.getByte("Slot")+" usage="+usage(item)+" keys="+item.getCompound("tag").getAllKeys());
                var payload=item.getCompound("tag").getCompound("GT.Behaviours").getCompound("tag");
                System.out.println("  patterns="+payload.getList("patterns",10).size());
            }
        }
    }
}
