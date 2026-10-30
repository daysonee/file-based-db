package ru.dota.skins.storage;

import ru.dota.skins.Skin;
import java.io.IOException;
import java.io.RandomAccessFile;

public class DataFile implements AutoCloseable {
    public static final int MAGIC = 0x534B494E;
    public static final int HEADER_SIZE = 16;
    public static final int RECORD_SIZE = 1 + Skin.RECORD_SIZE;

    private static final byte FREE = 0;
    private static final byte USED = 1;

    private final RandomAccessFile file;
    private int slotCount;
    private int recordCount;
    private int freeHead;

    private DataFile(RandomAccessFile file) {
        this.file = file;
    }

    public static DataFile create(String path) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path, "rw");
        f.setLength(0);
        DataFile df = new DataFile(f);
        df.slotCount = 0;
        df.recordCount = 0;
        df.freeHead = -1;
        df.writeHeader();
        return df;
    }

    public static DataFile open(String path) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path, "rw");
        f.seek(0);
        if (f.length() < HEADER_SIZE || f.readInt() != MAGIC) {
            throw new IOException("Не файл БД");
        }
        DataFile df = new DataFile(f);
        df.slotCount = f.readInt();
        df.recordCount = f.readInt();
        df.freeHead = f.readInt();
        return df;
    }

    private void writeHeader() throws IOException {
        file.seek(0);
        file.writeInt(MAGIC);
        file.writeInt(slotCount);
        file.writeInt(recordCount);
        file.writeInt(freeHead);
    }

    private long offset(int slot) {
        return HEADER_SIZE + (long) slot * RECORD_SIZE;
    }

    public int insert(Skin s) throws IOException {
        int slot;
        if (freeHead != -1) {
            slot = freeHead;
            file.seek(offset(slot) + 1);
            freeHead = file.readInt();
        } else {
            slot = slotCount;
            slotCount++;
        }
        file.seek(offset(slot));
        file.writeByte(USED);
        Skin.writeTo(file, s);
        recordCount++;
        writeHeader();
        return slot;
    }

    public Skin read(int slot) throws IOException {
        file.seek(offset(slot));
        if (file.readByte() == FREE) {
            return null;
        }
        return Skin.readFrom(file);
    }

    private void checkUsed(int slot) throws IOException {
        if (slot < 0 || slot >= slotCount) {
            throw new IllegalArgumentException("Нет ячейки " + slot);
        }
        file.seek(offset(slot));
        if (file.readByte() != USED) {
            throw new IllegalArgumentException("Ячейка " + slot + " пуста");
        }
    }

    public void update(int slot, Skin s) throws IOException {
        checkUsed(slot);
        file.seek(offset(slot) + 1);
        Skin.writeTo(file, s);
    }

    public void delete(int slot) throws IOException {
        checkUsed(slot);
        file.seek(slot);
        file.writeByte(FREE);
        file.writeInt(freeHead);
        freeHead = slot;
        recordCount--;
        writeHeader();
    }

    public int getSlotCount() {
        return slotCount;
    }

    public int getRecordCount() {
        return recordCount;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
