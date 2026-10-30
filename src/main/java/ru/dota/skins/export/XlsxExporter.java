package ru.dota.skins.export;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import ru.dota.skins.storage.Database;

import java.io.FileOutputStream;
import java.io.IOException;

public class XlsxExporter {
    private static final String[] HEADERS = {"ID", "Название", "Герой", "Редкость", "Цена", "Обмен"};

    public static int export(Database db, String path) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100);          
             FileOutputStream out = new FileOutputStream(path)) {

            Sheet sheet = wb.createSheet("Skins");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }

            int[] rowNum = {1};   
            db.forEach(s -> {
                Row r = sheet.createRow(rowNum[0]++);
                r.createCell(0).setCellValue(s.getId());
                r.createCell(1).setCellValue(s.getName());
                r.createCell(2).setCellValue(s.getHero());
                r.createCell(3).setCellValue(s.getRarity());
                r.createCell(4).setCellValue(s.getPrice());
                r.createCell(5).setCellValue(s.isTradable() ? "да" : "нет");
            });

            wb.write(out);
            return rowNum[0] - 1;   
        }
    }

    
}