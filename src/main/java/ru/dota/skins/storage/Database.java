package ru.dota.skins.storage;

import ru.dota.skins.Skin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Database implements AutoCloseable {
    private final String basePath;
    private DataFile data;
    private PrimaryIndex pidx;
    private SecondaryIndex sidx;

    private Database(String basePath) {
        this.basePath = basePath;
    }

    private static String dbPath(String base) {
        return base + ".db";
    }

    private static String pidxPath(String base) {
        return base + ".pidx";
    }

    private static String sidxPath(String base) {
        return base + ".sidx";
    }

    public static Database create(String basePath) throws IOException {
        Database db = new Database(basePath);
        db.data = DataFile.create(dbPath(basePath));
        db.pidx = PrimaryIndex.create(pidxPath(basePath));
        db.sidx = SecondaryIndex.create(sidxPath(basePath));
        return db;
    }

    public static Database open(String basePath) throws IOException {
        Database db = new Database(basePath);
        try {
            db.data = DataFile.open(dbPath(basePath));
            db.pidx = PrimaryIndex.open(pidxPath(basePath));
            db.sidx = SecondaryIndex.open(sidxPath(basePath));
        } catch (IOException e) {
            if (db.data != null)
                db.data.close();
            if (db.pidx != null)
                db.pidx.close();
            throw e;
        }
        return db;
    }

    public static void delete(String basePath) {
        new File(dbPath(basePath)).delete();
        new File(pidxPath(basePath)).delete();
        new File(sidxPath(basePath)).delete();
    }

    public void clear() throws IOException {
        data.close();
        pidx.close();
        sidx.close();

        data = DataFile.create(dbPath(basePath));
        pidx = PrimaryIndex.create(pidxPath(basePath));
        sidx = SecondaryIndex.create(sidxPath(basePath));
    }

    public void save() throws IOException {
        data.flush();
        pidx.flush();
        sidx.flush();
    }

    @Override
    public void close() throws IOException {
        data.close();
        pidx.close();
        sidx.close();
    }

    public String getBasePath() {
        return basePath;
    }

    public void add(Skin s) throws IOException {
        if (pidx.find(s.getId()) != -1) {
            throw new IllegalArgumentException("Скин с id " + s.getId() + " уже существует");
        }
        int slot = data.insert(s);
        pidx.insert(s.getId(), slot);
        link(slot, s.getHero());
    }

    public Skin findById(int id) throws IOException {
        int slot = pidx.find(id);
        return slot == -1 ? null : data.read(slot);
    }

    public List<Skin> findByHero(String hero) throws IOException {
        List<Skin> result = new ArrayList<>();
        int slot = sidx.getHead(hero);
        while (slot != -1) {
            result.add(data.read(slot));
            slot = data.getNext(slot);
        }
        return result;
    }

    public List<Skin> findWhere(Predicate<Skin> condition) throws IOException {
        List<Skin> result = new ArrayList<>();
        for (int slot = 0; slot < data.getSlotCount(); ++slot) {
            Skin s = data.read(slot);
            if (s != null && condition.test(s)) {
                result.add(s);
            }
        }

        return result;
    }

    public List<Skin> getAll() throws IOException {
        return findWhere(s -> true);
    }

    public boolean deleteById(int id) throws IOException {
        int slot = pidx.remove(id);
        if (slot == -1) {
            return false;
        }
        Skin s = data.read(slot);
        unlink(slot, s.getHero());
        data.delete(slot);
        return true;
    }

    public int deleteByHero(String hero) throws IOException {
        int slot = sidx.getHead(hero);
        int count = 0;
        while (slot != -1) {
            int next = data.getNext(slot);
            Skin s = data.read(slot);
            pidx.remove(s.getId());
            data.delete(slot);
            slot = next;
            count++;
        }
        sidx.setHead(hero, -1);
        return count;
    }

    public int deleteWhere(Predicate<Skin> condition) throws IOException {
        int count = 0;
        for (int slot = 0; slot < data.getSlotCount(); ++slot) {
            Skin s = data.read(slot);
            if (s != null && condition.test(s)) {
                deleteById(s.getId());
                count++;
            }
        }
        return count;

    }

    public void update(int oldId, Skin newSkin) throws IOException {
        int slot = pidx.find(oldId);
        if (slot == -1) {
            throw new IllegalArgumentException("Нет такого скина");
        }
        if (newSkin.getId() != oldId && pidx.find(newSkin.getId()) != -1) {
            throw new IllegalArgumentException("Такой скин уже есть в базе");
        }

        Skin old = data.read(slot);
        if (old.getId() != newSkin.getId()) {
            pidx.remove(oldId);
            pidx.insert(newSkin.getId(), slot);
        }
        if (!old.getHero().equals(newSkin.getHero())) {
            unlink(slot, old.getHero());
            data.update(slot, newSkin);
            link(slot, newSkin.getHero());
        } else {
            data.update(slot, newSkin);
        }

    }

    private void link(int slot, String hero) throws IOException {
        int head = sidx.getHead(hero);
        data.setPrev(slot, -1);
        data.setNext(slot, head);
        if (head != -1) {
            data.setPrev(head, slot);
        }
        sidx.setHead(hero, slot);
    }

    private void unlink(int slot, String hero) throws IOException {
        int p = data.getPrev(slot);
        int n = data.getNext(slot);
        if (p != -1) {
            data.setNext(p, n);
        } else {
            sidx.setHead(hero, n);
        }
        if (n != -1) {
            data.setPrev(n, p);
        }
    }

    public int size() {
        return data.getRecordCount();
    }

    public void backup(String zipPath) throws IOException {
        save();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipPath))) {
            addToZip(zip, dbPath(basePath), "skins.db");
            addToZip(zip, pidxPath(basePath), "skins.pidx");
            addToZip(zip, sidxPath(basePath), "skins.sidx");
        }
    }

    private static void addToZip(ZipOutputStream zip, String file, String entryName) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(Path.of(file), zip);
        zip.closeEntry();
    }

    public static Database restore(String zipPath, String basePath) throws IOException {
        int found = 0;
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String target = switch (entry.getName()) {
                    case "skins.db" -> dbPath(basePath);
                    case "skins.pidx" -> pidxPath(basePath);
                    case "skins.sidx" -> sidxPath(basePath);
                    default -> throw new IOException("Неизвестный файл в архиве: " + entry.getName());
                };
                Files.copy(zip, Path.of(target), StandardCopyOption.REPLACE_EXISTING);
                found++;
            }
        }
        if (found != 3) {
            throw new IOException("Архив повреждён или это не backup базы");
        }
        return open(basePath);
    }

    public void forEach(Consumer<Skin> action) throws IOException {
        for (int slot = 0; slot < data.getSlotCount(); ++slot) {
            Skin s = data.read(slot);
            if (s != null) {
                action.accept(s);
            }
        }
    }
}