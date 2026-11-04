package ru.dota.skins;

import ru.dota.skins.storage.Database;
import java.io.IOException;
import java.util.Random;

public class Benchmark {
    private static final String[] RARITIES =
            {"Common", "Uncommon", "Rare", "Mythical", "Legendary", "Immortal", "Arcana"};
    private static final int HERO_COUNT = 120;
    private static final int QUERIES = 1000;

    private static final String HEADER_FORMAT = "%8s | %10s | %12s | %13s | %10s | %11s%n";
    private static final String ROW_FORMAT    = "%8d | %10.1f | %12.1f | %13.1f | %10.1f | %11.1f%n";
    private static final int LINE_WIDTH = 8 + 10 + 12 + 13 + 10 + 11 + 5 * 3;

    public static void main(String[] args) throws IOException {
        run(1_000, false);

        System.out.printf(HEADER_FORMAT,
                "n", "add, мкс", "find id, мкс", "hero, мкс/зап", "rarity, мс", "del id, мкс");
        System.out.println("-".repeat(LINE_WIDTH));
        for (int n : new int[]{1_000, 10_000, 50_000, 100_000}) {
            run(n, true);
        }
    }

    private static Skin randomSkin(int id, Random rnd) {
        return new Skin(id, "Skin " + id, "Hero" + rnd.nextInt(HERO_COUNT),
                RARITIES[rnd.nextInt(RARITIES.length)],
                Math.round(rnd.nextDouble() * 10000) / 100.0, rnd.nextBoolean());
    }

    private static void run(int n, boolean print) throws IOException {
        Random rnd = new Random(42);
        try (Database db = Database.create("bench")) {
            // добавление
            long t = System.nanoTime();
            for (int id = 1; id <= n; id++) {
                db.add(randomSkin(id, rnd));
            }
            double addUs = (System.nanoTime() - t) / 1000.0 / n;

            // поиск по ключу
            t = System.nanoTime();
            for (int i = 0; i < QUERIES; i++) {
                db.findById(1 + rnd.nextInt(n));
            }
            double findIdUs = (System.nanoTime() - t) / 1000.0 / QUERIES;

            long found = 0;
            t = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                found += db.findByHero("Hero" + rnd.nextInt(HERO_COUNT)).size();
            }
            double heroUs = (System.nanoTime() - t) / 1000.0 / Math.max(found, 1);

            t = System.nanoTime();
            db.findWhere(s -> s.getRarity().equals("Arcana"));
            double rarityMs = (System.nanoTime() - t) / 1_000_000.0;

            t = System.nanoTime();
            for (int id = 1; id <= QUERIES; id++) {
                db.deleteById(id);
            }
            double delUs = (System.nanoTime() - t) / 1000.0 / QUERIES;

            if (print) {
                System.out.printf(ROW_FORMAT,
                        n, addUs, findIdUs, heroUs, rarityMs, delUs);
            }
        }
        Database.delete("bench");
    }
}