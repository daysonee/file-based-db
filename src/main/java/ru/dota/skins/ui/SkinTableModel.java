package ru.dota.skins.ui;

import ru.dota.skins.Skin;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class SkinTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = { "ID", "Название", "Герой", "Редкость", "Цена", "Обмен" };

    private List<Skin> rows = new ArrayList<>();

    public void setRows(List<Skin> rows){
        this.rows = rows;
        fireTableDataChanged();
    }

    public Skin getSkinAt(int row) {
        return rows.get(row);
    } 

    @Override public int getRowCount() {return rows.size();}
    @Override public int getColumnCount() {return COLUMNS.length;}
    @Override public String getColumnName(int col) {return COLUMNS[col];}

    @Override public Object getValueAt(int row, int col) {
        Skin s = rows.get(row);
        return switch (col) {
            case 0 -> s.getId();
            case 1 -> s.getName();
            case 2 -> s.getHero();
            case 3 -> s.getRarity();
            case 4 -> s.getPrice();
            case 5 -> s.isTradable();
            default -> null;
        };
    }

    @Override 
    public Class<?> getColumnClass(int col) {
        return switch (col) {
            case 0 -> Integer.class;
            case 4 -> Double.class;
            case 5 -> Boolean.class;
            default -> String.class;
        };
    }
}
