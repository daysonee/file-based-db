package ru.dota.skins.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import ru.dota.skins.Skin;

public class SecondaryIndex implements AutoCloseable {
    public static final int MAGIC = 0x53494458;
    public static final int HEADER_SIZE = 16;
    public static final int SLOT_SIZE = 1 + Skin.HERO_LEN * 2 + 4;
    public static final int INITIAL_CAPACITY = 16;
    private static final double MAX_LOAD = 0.7;

    public static final byte EMPTY = 0, OCCUPIED = 1, DELETED = 2;

    private final String path;
    private RandomAccessFile file;
    private int capacity;
    private int size;
    private int tombstones;

    private SecondaryIndex(String path, RandomAccessFile file) {
        this.path = path;
        this.file = file;
    }

    public void flush() throws IOException {
        file.getChannel().force(true);
    }

    public static SecondaryIndex create(String path) throws IOException {
        return create(path, INITIAL_CAPACITY);
    }

    private static SecondaryIndex create(String path, int capacity) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path, "rw");
        f.setLength(0);
        f.setLength(HEADER_SIZE + (long) capacity * SLOT_SIZE);
        SecondaryIndex idx = new SecondaryIndex(path, f);
        idx.capacity = capacity;
        idx.size = 0;
        idx.tombstones = 0;
        idx.writeHeader();
        return idx;
    }

    public static SecondaryIndex open(String path) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path, "rw");
        f.seek(0);
        if (f.length() < HEADER_SIZE || f.readInt() != MAGIC) {
            f.close();
            throw new IOException("Не файл Индекса");
        }
        SecondaryIndex pi = new SecondaryIndex(path, f);
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

    private int hash(String hero) {
        int h = hero.hashCode() * 0x9E3779B9;
        h ^= (h >>> 16);
        return Math.floorMod(h, capacity);
    }

    // смещение поля head внутри слота
    private long headOffset(int i) {
        return offset(i) + 1 + Skin.HERO_LEN * 2;
    }

    // номер слота индекса, где лежит герой, или -1
    private int findSlot(String hero) throws IOException {
        int i = hash(hero);
        for (int step = 0; step < capacity; ++step) {
            file.seek(offset(i));
            byte state = file.readByte();
            if (state == EMPTY) {
                return -1;
            }
            String h = Skin.readFixedString(file, Skin.HERO_LEN);
            if (state == OCCUPIED && h.equals(hero)) {
                return i;
            }
            i = (i + 1) % capacity;
        }
        return -1;
    }

    // номер первой ячейки героя в skins.db или -1
    public int getHead(String hero) throws IOException {
        int i = findSlot(hero);
        if (i == -1) {
            return -1;
        }
        file.seek(headOffset(i));
        return file.readInt();
    }

    public void setHead(String hero, int head) throws IOException {
        int i = findSlot(hero);

        if (i != -1) { // герой уже в индексе
            if (head == -1) { // скинов не осталось → удаляем героя
                file.seek(offset(i));
                file.writeByte(DELETED);
                size--;
                tombstones++;
                writeHeader();
            } else { // просто обновляем голову
                file.seek(headOffset(i));
                file.writeInt(head);
            }
            return;
        }

        if (head == -1) { // героя нет и удалять нечего
            return;
        }
        insertNew(hero, head);
    }

    // вставка героя, которого точно нет в индексе
    private void insertNew(String hero, int head) throws IOException {
        if (size + tombstones + 1 > capacity * MAX_LOAD) {
            rehash();
        }
        int i = hash(hero);
        for (int step = 0; step < capacity; ++step) {
            file.seek(offset(i));
            byte state = file.readByte();
            if (state != OCCUPIED) { // EMPTY или DELETED — можно занимать
                if (state == DELETED) {
                    tombstones--;
                }
                file.seek(offset(i));
                file.writeByte(OCCUPIED);
                Skin.writeFixedString(file, hero, Skin.HERO_LEN);
                file.writeInt(head);
                size++;
                writeHeader();
                return;
            }
            i = (i + 1) % capacity;
        }
        throw new IllegalStateException("Индекс переполнен");
    }

    private void rehash() throws IOException {
        SecondaryIndex bigger = create(path + ".tmp", capacity * 2);
        for (int i = 0; i < capacity; ++i) {
            file.seek(offset(i));
            byte state = file.readByte();
            String hero = Skin.readFixedString(file, Skin.HERO_LEN);
            int head = file.readInt();
            if (state == OCCUPIED) {
                bigger.insertNew(hero, head);
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
