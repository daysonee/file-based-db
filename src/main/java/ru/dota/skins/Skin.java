package ru.dota.skins;

import java.io.IOException;
import java.io.RandomAccessFile;

public class Skin {
    public static final int NAME_LEN = 40;
    public static final int HERO_LEN = 30;
    public static final int RARITY_LEN = 15;
    public static final int RECORD_SIZE = (NAME_LEN + HERO_LEN + RARITY_LEN) * 2 + 4 + 8 + 1;

    private int id;
    private String name;
    private String hero;
    private String rarity;
    private double price;
    private boolean tradable;

    public Skin(int id, String name, String hero, String rarity, double price, boolean tradable) {
        if (name == null || name.isBlank() || name.length() > NAME_LEN)
            throw new IllegalArgumentException("Название: от 1 до " + NAME_LEN + " символов");
        if (hero == null || hero.isBlank() || hero.length() > HERO_LEN)
            throw new IllegalArgumentException("Герой: от 1 до " + HERO_LEN + " символов");
        if (rarity == null || rarity.isBlank() || rarity.length() > RARITY_LEN)
            throw new IllegalArgumentException("Редкость: от 1 до " + NAME_LEN + " символов");
        if (id <= 0)
            throw new IllegalArgumentException("Id предмета не может быть меньше 1");
        if (price < 0){
            throw new IllegalArgumentException("Цена предмета не может быть отрицательной");
        }
        this.id = id;
        this.name = name;
        this.hero = hero;
        this.rarity = rarity;
        this.price = price;
        this.tradable = tradable;
    }

    @Override
    public String toString() {
        return id + " | " + name + " | " + hero + " | " + rarity + " | " + price + " | " + (tradable ? "да" : "нет");
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getHero() {
        return this.hero;
    }

    public String getRarity() {
        return this.rarity;
    }

    public double getPrice() {
        return this.price;
    }

    public boolean isTradable() {
        return this.tradable;
    }

    public static void writeFixedString(RandomAccessFile f, String s, int len) throws IOException {
        for (int i = 0; i < len; ++i) {
            char c = (i < s.length() ? s.charAt(i) : '\0');
            f.writeChar(c);
        }
    }

    public static String readFixedString(RandomAccessFile f, int len) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; ++i) {
            char c = f.readChar();
            if (c != '\0') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void writeTo(RandomAccessFile f, Skin s) throws IOException {
        f.writeInt(s.getId());
        writeFixedString(f, s.getName(), Skin.NAME_LEN);
        writeFixedString(f, s.getHero(), Skin.HERO_LEN);
        writeFixedString(f, s.getRarity(), Skin.RARITY_LEN);
        f.writeDouble(s.getPrice());
        f.writeBoolean(s.isTradable());
    }

    public static Skin readFrom(RandomAccessFile f) throws IOException {
        int id = f.readInt();
        String name = readFixedString(f, Skin.NAME_LEN);
        String hero = readFixedString(f, Skin.HERO_LEN);
        String rarity = readFixedString(f, Skin.RARITY_LEN);
        double price = f.readDouble();
        boolean tradable = f.readBoolean();
        return new Skin(id, name, hero, rarity, price, tradable);

    }
}
