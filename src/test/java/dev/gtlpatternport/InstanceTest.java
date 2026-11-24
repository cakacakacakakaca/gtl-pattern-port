package dev.gtlpatternport;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.io.*;

/** Replays real exports under vanilla and the instance's Packet Fixer reader budgets. */
public class InstanceTest {
    private static int checks;
    private static void check(boolean b) { if(!b) throw new AssertionError(); checks++; }
    public static void main(String[] args) throws Exception {
        int files=0, vanillaRejected=0;
        try(var paths=Files.list(Path.of(args[0]).resolve("gtl-patterns"))) {
            for(Path path:paths.filter(p->p.toString().endsWith(".gtlp")).sorted().toList()) {
                files++;
                CompoundTag payload=Codec.decode(Files.readAllBytes(path));
                CompoundTag item=new CompoundTag();item.putString("id","gtlcore:me_pattern_buffer_copy");item.putByte("Count",(byte)1);
                CompoundTag tag=new CompoundTag();tag.putString("testUnrelated","retained");item.put("tag",tag);
                // Model Packet Fixer 2.0.0's allSizesUnlimited=true modification to readNbt().
                ImportData.Validator network=itemData -> Codec.checkItemSize(itemData, b -> b.readNbt(new NbtAccounter(Long.MAX_VALUE)));
                try {ImportData.prepare(item,payload);}
                catch(IOException expected) {vanillaRejected++;System.out.println("vanilla limit rejected: "+path.getFileName());}
                CompoundTag old=item.copy();
                for(int i=0;i<12;i++) {
                    CompoundTag next=payload.copy();next.putString("name","import-"+i);
                    if(i==3) item.getCompound("tag").put(ImportData.BACKUP,payload.copy());
                    if(i==6) item.getCompound("tag").putByteArray(ImportData.BACKUP,Codec.encode(payload));
                    CompoundTag snapshot=item.copy();
                    CompoundTag result=ImportData.prepare(item,next,network);
                    check(item.equals(snapshot));
                    check(result.getCompound("tag").getCompound("GT.Behaviours").getCompound("tag").equals(next));
                    check(!result.getCompound("tag").contains(ImportData.BACKUP));
                    check(result.getCompound("tag").getString("testUnrelated").equals("retained"));
                    item=result;
                }
                check(!old.getCompound("tag").contains("GT.Behaviours"));
                CompoundTag before=item.copy();
                try {ImportData.prepare(item,payload,t -> {throw new IOException("client limit smaller than server");});throw new AssertionError();}catch(IOException expected){checks++;}
                check(item.equals(before));
                System.out.println("PASS "+path.getFileName()+" patterns="+payload.getList("patterns",10).size()+" accounted="+InstanceProbe.usage(item));
            }
        }
        check(files==7);check(vanillaRejected==1);
        // The hard cap must still apply when the network reader is unlimited.
        CompoundTag bomb=new CompoundTag();ListTag list=new ListTag();
        for(int i=0;i<400000;i++) list.add(new CompoundTag());bomb.put("list",list);
        try {Codec.checkItemSize(bomb,b -> b.readNbt(new NbtAccounter(Long.MAX_VALUE)));throw new AssertionError("Local bound bypassed");}
        catch(RuntimeException expected){checks++;}
        System.out.println("PASS: "+checks+" real-instance assertions; "+files+" files, 12 overwrites each. Packet Fixer budget simulated; no game launched.");
    }
}
