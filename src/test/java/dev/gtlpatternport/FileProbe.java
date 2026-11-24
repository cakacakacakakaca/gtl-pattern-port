package dev.gtlpatternport;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.io.*;
public final class FileProbe {
    public static void main(String[] args) throws Exception {
        byte[] bytes=Files.readAllBytes(Path.of(args[0]));
        CompoundTag p=Codec.decode(bytes);
        System.out.println("File bytes="+bytes.length+", patterns="+p.getList("patterns",10).size());
        CompoundTag item=new CompoundTag(), tag=new CompoundTag(), behavior=new CompoundTag();
        item.putString("id","gtlcore:me_pattern_buffer_copy");item.putByte("Count",(byte)1);
        behavior.put("tag",p);tag.put("GT.Behaviours",behavior);item.put("tag",tag);
        for(int i=0;i<2;i++) {
            if(i==1) tag.put("gtlpatternport_backup",p.copy());
            ByteArrayOutputStream out=new ByteArrayOutputStream();NbtIo.write(item,new DataOutputStream(out));
            NbtAccounter counter=new NbtAccounter(64L*1024*1024);
            NbtIo.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray())),counter);
            System.out.println("backup="+i+", raw="+out.size()+", accounted="+counter.getUsage());
            try {Codec.checkItemSize(item); System.out.println("CHECK PASS");}catch(Exception ex){System.out.println("CHECK FAIL: "+ex.getMessage());}
        }
    }
}
