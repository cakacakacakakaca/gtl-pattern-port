package dev.gtlpatternport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.io.IOException;

/** Builds and validates a replacement without modifying the original item. */
public final class ImportData {
    public static final String BACKUP = "gtlpatternport_backup";
    private static final String BEHAVIOURS = "GT.Behaviours";

    public static CompoundTag prepare(CompoundTag item, CompoundTag imported) throws IOException {
        return prepare(item, imported, Codec::checkItemSize);
    }
    @FunctionalInterface interface Validator { void validate(CompoundTag item) throws IOException; }
    static CompoundTag prepare(CompoundTag item, CompoundTag imported, Validator validator) throws IOException {
        CompoundTag result = item.copy();
        CompoundTag tag = result.getCompound("tag");
        CompoundTag behaviours = tag.getCompound(BEHAVIOURS);
        // A successful import is a replacement, with no retained previous payload.
        tag.remove(BACKUP);
        behaviours.put("tag", imported.copy());
        tag.put(BEHAVIOURS, behaviours);
        result.put("tag", tag);
        validator.validate(result);
        return result;
    }

    public static CompoundTag backup(CompoundTag itemTag) throws IOException {
        if (itemTag == null) return null;
        if (itemTag.contains(BACKUP, Tag.TAG_BYTE_ARRAY)) return Codec.decode(itemTag.getByteArray(BACKUP));
        if (itemTag.contains(BACKUP, Tag.TAG_COMPOUND)) return itemTag.getCompound(BACKUP).copy();
        return null;
    }
}
