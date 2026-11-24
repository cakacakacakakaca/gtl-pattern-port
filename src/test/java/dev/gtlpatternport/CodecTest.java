package dev.gtlpatternport;

import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;
import java.nio.file.*;

public final class CodecTest {
    private static int checks;
    private static void check(boolean b) { if(!b) throw new AssertionError(); checks++; }
    private static void rejects(CompoundTag p) throws Exception {
        try { Codec.encode(p); throw new AssertionError("Accepted invalid payload"); }
        catch(IOException expected) {checks++;}
    }
    public static void main(String[] args) throws Exception {
        CompoundTag p=new CompoundTag(); p.putString("name","测试 · 化工线");
        ListTag list=new ListTag();
        CompoundTag entry=new CompoundTag(), stack=new CompoundTag(), data=new CompoundTag();
        stack.putString("id","ae2:processing_pattern"); stack.putByte("Count",(byte)1);
        data.putLong("fluidAmount",9876543210L); data.putIntArray("ints",new int[]{1,2,3});
        stack.put("tag",data); entry.put("pattern",stack); entry.putByte("cacheCount",(byte)16); list.add(entry); p.put("patterns",list);
        check(p.equals(Codec.decode(Codec.encode(p))));
        check(Arrays.equals(Codec.fingerprint(p),Codec.fingerprint(p.copy())));
        CompoundTag invalid=p.copy(); invalid.getList("patterns",10).getCompound(0).putByte("cacheCount",(byte)0); rejects(invalid);
        invalid=p.copy(); invalid.getList("patterns",10).getCompound(0).getCompound("pattern").putByte("Count",(byte)64); rejects(invalid);
        invalid=p.copy(); invalid.put("patterns",new IntArrayTag(new int[]{1})); rejects(invalid);
        invalid=p.copy(); ListTag bad=new ListTag(); bad.add(StringTag.valueOf("wrong")); invalid.put("patterns",bad); rejects(invalid);
        invalid=p.copy(); invalid.remove("name"); rejects(invalid);
        CompoundTag extra=p.copy(); extra.putLong("bufferPos",100); extra.put("proxies",new LongArrayTag(new long[]{123})); check(Codec.decode(Codec.encode(extra)).equals(p));
        try {Codec.decode(new byte[]{1,2,3}); throw new AssertionError();} catch(IOException expected){checks++;}
        byte[] random=new byte[100000];new Random(42).nextBytes(random);data.putByteArray("large",random);
        byte[] encoded=Codec.encode(p); check(encoded.length>24000);
        ByteArrayOutputStream reconstructed=new ByteArrayOutputStream();
        for(int i=0;i<encoded.length;i+=24000) reconstructed.write(Arrays.copyOfRange(encoded,i,Math.min(encoded.length,i+24000)));
        check(Codec.decode(reconstructed.toByteArray()).equals(p));
        data.putByteArray("large",new byte[Codec.MAX_RAW+1]); rejects(p);
        CompoundTag huge=new CompoundTag();huge.putByteArray("large",new byte[1024*1024+1]);
        try {Codec.checkItemSize(huge);throw new AssertionError();}catch(IOException expected){checks++;}
        CompoundTag empty=new CompoundTag();empty.putString("name","");empty.put("patterns",new ListTag());check(Codec.decode(Codec.encode(empty)).equals(empty));
        CompoundTag large = empty.copy();
        ListTag many = new ListTag();
        for (int i=0;i<700;i++) {
            CompoundTag e = new CompoundTag(), s = new CompoundTag(), details = new CompoundTag();
            s.putString("id", "ae2:processing_pattern"); s.putByte("Count", (byte)1);
            for(int j=0;j<10;j++) details.putString("field"+j, "value"+i);
            s.put("tag", details); e.put("pattern", s); e.putByte("cacheCount", (byte)1); many.add(e);
        }
        large.put("patterns", many);
        regression(large, "synthetic");
        if(args.length > 0) regression(Codec.decode(Files.readAllBytes(Path.of(args[0]))), "user fixture");
        System.out.println("PASS: " + checks + " codec / size / repeated-import checks");
    }

    private static void regression(CompoundTag payload, String label) throws Exception {
        CompoundTag item = new CompoundTag(); item.putString("id", "gtlcore:me_pattern_buffer_copy"); item.putByte("Count", (byte)1);
        CompoundTag first = ImportData.prepare(item, payload);
        check(!item.contains("tag")); // Preparation never mutates the original.
        CompoundTag legacy = first.copy();
        legacy.getCompound("tag").put(ImportData.BACKUP, payload.copy());
        try { Codec.checkItemSize(legacy); throw new AssertionError("Expected legacy second import to exceed NBT budget: " + label); }
        catch (IOException expected) { checks++; System.out.println(label + " old failure reproduced: " + expected.getMessage()); }

        CompoundTag original = first.copy();
        CompoundTag current = first;
        CompoundTag previous = payload;
        for(int i=0;i<12;i++) {
            CompoundTag next = payload.copy();
            // Exercise both identical imports and replacement by a different file.
            if(i%2==1) next.putString("name", "replacement " + i);
            CompoundTag prepared = ImportData.prepare(current, next);
            check(prepared.getCompound("tag").getCompound("GT.Behaviours").getCompound("tag").equals(next));
            check(!prepared.getCompound("tag").contains(ImportData.BACKUP));
            Codec.checkItemSize(prepared);
            current = prepared; previous = next;
        }
        check(first.equals(original));
        check(ImportData.backup(legacy.getCompound("tag")).equals(payload));
        CompoundTag migrated = ImportData.prepare(legacy, payload);
        check(!migrated.getCompound("tag").contains(ImportData.BACKUP));
        legacy.getCompound("tag").getCompound("GT.Behaviours").remove("tag");
        check(!ImportData.prepare(legacy,payload).getCompound("tag").contains(ImportData.BACKUP));
        CompoundTag oversize = payload.copy(); oversize.putByteArray("extra",new byte[1024*1024+1]);
        CompoundTag before = current.copy();
        try {ImportData.prepare(current,oversize);throw new AssertionError("Oversize active payload accepted");}catch(IOException expected){checks++;}
        check(current.equals(before));
        System.out.println(label + ": " + payload.getList("patterns",Tag.TAG_COMPOUND).size() + " patterns; 12 replacement imports and legacy backup removal passed");
    }
}
