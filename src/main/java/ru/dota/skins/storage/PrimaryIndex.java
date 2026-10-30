package ru.dota.skins.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class PrimaryIndex implements AutoCloseable {
    public static final int MAGIC = 0x50494458;
    public static final int HEADER_SIZE = 16;
    public static final int SLOT_SIZE = 1 + 4 + 4;
    public static final int INITIAL_CAPACITY = 16;
    private static final double MAX_LOAD = 0.7;

    public static final byte EMPTY = 0, OCCUPIED = 1, DELETED = 2;

    private final String path;
    private RandomAccessFile file;
    private int capacity;
    private int size;
    private int tombstones;

    private PrimaryIndex(String path, RandomAccessFile file) {
        this.path = path;
        this.file = file;
    }

    public void flush() throws IOException {
        file.getChannel().force(true); // принудительно сбросить данные на диск
    }

    public static PrimaryIndex create(String path) throws IOException {
        return create(path, INITIAL_CAPACITY);
    }

    private static PrimaryIndex create(String path, int capacity) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path, "rw");
        f.setLength(0);
        f.setLength(HEADER_SIZE + (long) capacity * SLOT_SIZE);
        PrimaryIndex idx = new PrimaryIndex(path, f);
        idx.capacity = capacity;
        idx.size = 0;
        idx.tombstones = 0;
        idx.writeHeader();
        return idx;
    }

    public static PrimaryIndex open(String path) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path, "rw");
        f.seek(0);
        if (f.length() < HEADER_SIZE || f.readInt() != MAGIC) {
            f.close();
            throw new IOException("Не файл Индекса");
        }
        PrimaryIndex pi = new PrimaryIndex(path, f);
        pi.capacity = f.readInt();
        pi.size = f.readInt();
        pi.tombstones = f.readInt();
        return pi;
    }

    private void writeHeader() throws IOException {
        file.seek(0);
        file.writeInt(MAGIC);
        file.writeInt(capacity);
        file.writeInt(size);
        file.writeInt(tombstones);
    }

    private long offset(int i) {
        return HEADER_SIZE + (long) i * SLOT_SIZE;
    }

    private int hash(int key) {
        int h = key * 0x9E3779B9;
        h ^= (h >>> 16);
        return Math.floorMod(h, capacity);
    }

    public int find(int key) throws IOException {
        int i = hash(key);
        for (int step = 0; step < capacity; ++step) {
            file.seek(offset(i));
            byte state = file.readByte();
            if (state == EMPTY) {
                return -1;
            }
            int k = file.readInt();
            int dataSlot = file.readInt();
            if (state == OCCUPIED && k == key)
                return dataSlot;
            i = (i + 1) % capacity;
        }
        return -1;
    }

    public void insert(int key, int dataSlot) throws IOException {
        if ((size + tombstones + 1) > capacity * MAX_LOAD) {
            rehash();
        }
        int i = hash(key);
        int firstDeleted = -1;
        int emptySlot = -1;

        for (int step = 0; step < capacity; ++step) {
            file.seek(offset(i));
            byte state = file.readByte();
            if (state == EMPTY) {
                emptySlot = i;
                break;
            }
            int k = file.readInt();
            if (state == OCCUPIED && k == key) {
                throw new IllegalArgumentException("Ключ " + key + " уже существует");
            }
            if (state == DELETED && firstDeleted == -1) {
                firstDeleted = i;
            }

            i = (i + 1) % capacity;

        }

        int target;
        if (firstDeleted != -1) {
            target = firstDeleted;
            tombstones--;
        } else if (emptySlot != -1) {
            target = emptySlot;
        } else {
            throw new IllegalStateException("Индекс переполнен");
        }
        file.seek(offset(target));
        file.writeByte(OCCUPIED);
        file.writeInt(key);
        file.writeInt(dataSlot);
        size++;
        writeHeader();

    }

    public int remove(int key) throws IOException {
        int i = hash(key);
        for (int step = 0; step < capacity; ++step) {
            file.seek(offset(i));
            byte state = file.readByte();
            if (state == EMPTY) {
                return -1;
            }
            int k = file.readInt();
            int dataSlot = file.readInt();
            if (state == OCCUPIED && k == key) {
                file.seek(offset(i));
                file.writeByte(DELETED);
                size--;
                tombstones++;
                writeHeader();
                return dataSlot;
            }
            i = (i + 1) % capacity;
        }
        return -1;
    }

    private void rehash() throws IOException {
        PrimaryIndex bigger = create(path + ".tmp", capacity * 2);
        for (int i = 0; i < capacity; ++i) {
            file.seek(offset(i));
            file.seek(offset(i));
            byte state = file.readByte();
            int key = file.readInt();
            int dataSlot = file.readInt();
            if (state == OCCUPIED) {
                bigger.insert(key, dataSlot);
            }
        }
        file.close();
        bigger.close();
        new File(path).delete();
        new File(path + ".tmp").renameTo(new File(path));
        this.file = new RandomAccessFile(path, "rw");
        capacity = bigger.capacity;
        size = bigger.size;
        tombstones = 0;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getSize() {
        return size;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
