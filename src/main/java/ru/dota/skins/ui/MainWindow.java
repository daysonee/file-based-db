package ru.dota.skins.ui;

import ru.dota.skins.Skin;
import ru.dota.skins.export.XlsxExporter;
import ru.dota.skins.storage.Database;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.function.Predicate;

public class MainWindow extends JFrame {
    @FunctionalInterface
    interface DbAction {
        void run() throws Exception;
    }

    private static final String[] RARITIES = { "Common", "Uncommon", "Rare", "Mythical", "Legendary", "Immortal",
            "Arcana" };
    private static final String[] FIELDS = { "id", "name", "hero", "rarity", "price", "tradable" };

    private Database db;
    private Integer editingId = null;

    private final SkinTableModel tableModel = new SkinTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel status = new JLabel("База не открыта");

    private final JTextField idField = new JTextField(8);
    private final JTextField nameField = new JTextField(15);
    private final JTextField heroField = new JTextField(15);
    private final JComboBox<String> rarityBox = new JComboBox<>(RARITIES);
    private final JTextField priceField = new JTextField(8);
    private final JCheckBox tradableBox = new JCheckBox("Можно обменять");

    private final JComboBox<String> fieldBox = new JComboBox<>(FIELDS);
    private final JTextField valueField = new JTextField(15);

    public MainWindow() {
        super("Каталог скинов Dota 2");

        setJMenuBar(createMenu());
        setLayout(new BorderLayout());
        add(createSearchPanel(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(createFormPanel(), BorderLayout.EAST);
        add(status, BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true); // сортировка кликом по заголовку
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                fillFormFromSelection();
            }
        });

        // при закрытии окна — закрыть файлы базы
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                run(() -> {
                    closeDb();
                    dispose();
                });
            }
        });

        setSize(1050, 600);
        setLocationRelativeTo(null);

    }

    private JMenuBar createMenu() {
        JMenu file = new JMenu("Файл");
        file.add(menuItem("Создать...", this::onCreate));
        file.add(menuItem("Открыть...", this::onOpen));
        file.add(menuItem("Сохранить", this::onSave));
        file.addSeparator();
        file.add(menuItem("Очистить", this::onClear));
        file.add(menuItem("Удалить базу", this::onDeleteDb));
        file.addSeparator();
        file.add(menuItem("Создать backup...", this::onBackup));
        file.add(menuItem("Восстановить из backup...", this::onRestore));
        file.add(menuItem("Экспорт в Excel...", this::onExport));
        JMenuBar bar = new JMenuBar();
        bar.add(file);
        return bar;
    }

    private JPanel createSearchPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.add(new JLabel("Поле:"));
        p.add(fieldBox);
        p.add(new JLabel("Значение:"));
        p.add(valueField);
        p.add(button("Найти", this::onSearch));
        p.add(button("Удалить", this::onDelete));
        p.add(button("Показать все", this::refresh));
        return p;
    }

    private JPanel createFormPanel() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 5, 5)); // 0 строк = сколько понадобится
        grid.add(new JLabel("ID:"));
        grid.add(idField);
        grid.add(new JLabel("Название:"));
        grid.add(nameField);
        grid.add(new JLabel("Герой:"));
        grid.add(heroField);
        grid.add(new JLabel("Редкость:"));
        grid.add(rarityBox);
        grid.add(new JLabel("Цена:"));
        grid.add(priceField);
        grid.add(new JLabel(""));
        grid.add(tradableBox);
        grid.add(button("Добавить", this::onAdd));
        grid.add(button("Изменить", this::onUpdate));
        grid.add(button("Очистить форму", this::clearForm));

        // обёртка: прижать сетку к верху, иначе GridLayout растянет поля на всю высоту
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        wrapper.add(grid, BorderLayout.NORTH);
        return wrapper;
    }

    private JMenuItem menuItem(String text, DbAction action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> run(action));
        return item;
    }

    private JButton button(String text, DbAction action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> run(action));
        return b;
    }

    // выполнить действие; любую ошибку показать в диалоге
    private void run(DbAction action) {
        try {
            action.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================= вспомогательное =================

    private void requireDb() {
        if (db == null) {
            throw new IllegalStateException("Сначала создайте или откройте базу");
        }
    }

    private void closeDb() throws Exception {
        if (db != null) {
            db.close();
            db = null;
        }
    }

    // перечитать все записи из файла в таблицу
    private void refresh() throws Exception {
        if (db == null) {
            tableModel.setRows(List.of());
            status.setText("База не открыта");
        } else {
            tableModel.setRows(db.getAll());
            status.setText("База: " + db.getBasePath() + " | записей: " + db.size());
        }
        editingId = null;
    }

    private boolean confirm(String question) {
        return JOptionPane.showConfirmDialog(this, question, "Подтверждение",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    // диалог выбора файла; возвращает путь без ".db" или null, если нажали "Отмена"
    private String chooseBasePath(boolean forSave) {
        JFileChooser ch = new JFileChooser(new File("."));
        ch.setFileFilter(new FileNameExtensionFilter("База скинов (*.db)", "db"));
        int r = forSave ? ch.showSaveDialog(this) : ch.showOpenDialog(this);
        if (r != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        String path = ch.getSelectedFile().getAbsolutePath();
        if (path.endsWith(".db")) {
            path = path.substring(0, path.length() - 3);
        }
        return path;
    }

    // собрать Skin из формы (конструктор Skin сам проверит длины и диапазоны)
    private Skin readForm() {
        int id;
        double price;
        try {
            id = Integer.parseInt(idField.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID должен быть целым числом");
        }
        try {
            price = Double.parseDouble(priceField.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Цена должна быть числом");
        }
        return new Skin(id, nameField.getText().trim(), heroField.getText().trim(),
                (String) rarityBox.getSelectedItem(), price, tradableBox.isSelected());
    }

    private void fillFormFromSelection() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            editingId = null;
            return;
        }
        // при сортировке номер строки на экране и в модели различаются
        Skin s = tableModel.getSkinAt(table.convertRowIndexToModel(viewRow));
        idField.setText(String.valueOf(s.getId()));
        nameField.setText(s.getName());
        heroField.setText(s.getHero());
        rarityBox.setSelectedItem(s.getRarity());
        priceField.setText(String.valueOf(s.getPrice()));
        tradableBox.setSelected(s.isTradable());
        editingId = s.getId();
    }

    private void clearForm() {
        idField.setText("");
        nameField.setText("");
        heroField.setText("");
        rarityBox.setSelectedIndex(0);
        priceField.setText("");
        tradableBox.setSelected(false);
        table.clearSelection();
        editingId = null;
    }

    // условие для поиска/удаления по полям без индекса
    private Predicate<Skin> condition(String field, String value) {
        return switch (field) {
            case "name" -> s -> s.getName().equals(value);
            case "rarity" -> s -> s.getRarity().equalsIgnoreCase(value);
            case "price" -> {
                double p;
                try {
                    p = Double.parseDouble(value.replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Цена должна быть числом");
                }
                yield s -> s.getPrice() == p;
            }
            case "tradable" -> {
                boolean b = parseBool(value);
                yield s -> s.isTradable() == b;
            }
            default -> throw new IllegalArgumentException("Неизвестное поле: " + field);
        };
    }

    private boolean parseBool(String v) {
        return switch (v.toLowerCase()) {
            case "да", "true", "1" -> true;
            case "нет", "false", "0" -> false;
            default -> throw new IllegalArgumentException("Для tradable введите да/нет");
        };
    }

    private int parseId(String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID должен быть целым числом");
        }
    }

    // ================= обработчики меню =================

    private void onCreate() throws Exception {
        String base = chooseBasePath(true);
        if (base == null)
            return;
        closeDb();
        db = Database.create(base);
        refresh();
    }

    private void onOpen() throws Exception {
        String base = chooseBasePath(false);
        if (base == null)
            return;
        closeDb();
        db = Database.open(base);
        refresh();
    }

    private void onSave() throws Exception {
        requireDb();
        db.save();
        JOptionPane.showMessageDialog(this, "Сохранено");
    }

    private void onClear() throws Exception {
        requireDb();
        if (!confirm("Удалить все записи из базы?"))
            return;
        db.clear();
        refresh();
    }

    private void onDeleteDb() throws Exception {
        requireDb();
        if (!confirm("Удалить файлы базы с диска? Это необратимо."))
            return;
        String base = db.getBasePath();
        closeDb();
        Database.delete(base);
        refresh();
    }

    // ================= обработчики кнопок =================

    private void onAdd() throws Exception {
        requireDb();
        db.add(readForm());
        refresh();
        clearForm();
    }

    private void onUpdate() throws Exception {
        requireDb();
        if (editingId == null) {
            throw new IllegalArgumentException("Выберите скин в таблице");
        }
        db.update(editingId, readForm());
        refresh();
        clearForm();
    }

    private void onSearch() throws Exception {
        requireDb();
        String field = (String) fieldBox.getSelectedItem();
        String value = valueField.getText().trim();

        List<Skin> result = switch (field) {
            case "id" -> {
                Skin s = db.findById(parseId(value));
                yield s == null ? List.of() : List.of(s);
            }
            case "hero" -> db.findByHero(value);
            default -> db.findWhere(condition(field, value));
        };

        tableModel.setRows(result);
        status.setText("Найдено: " + result.size() + " (поиск по " + field + " = " + value + ")");
    }

    private void onDelete() throws Exception {
        requireDb();
        String field = (String) fieldBox.getSelectedItem();
        String value = valueField.getText().trim();
        if (!confirm("Удалить записи, где " + field + " = " + value + "?"))
            return;
        int count = switch (field) {
            case "id" -> db.deleteById(parseId(value)) ? 1 : 0;
            case "hero" -> db.deleteByHero(value);
            default -> db.deleteWhere(condition(field, value));
        };
        refresh();
        JOptionPane.showMessageDialog(this, "Удалено записей: " + count);
    }

    // выбор файла с заданным расширением; добавляет расширение, если его не ввели
    private String chooseFile(String description, String ext, boolean forSave) {
        JFileChooser ch = new JFileChooser(new File("."));
        ch.setFileFilter(new FileNameExtensionFilter(description, ext));
        int r = forSave ? ch.showSaveDialog(this) : ch.showOpenDialog(this);
        if (r != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        String path = ch.getSelectedFile().getAbsolutePath();
        if (forSave && !path.endsWith("." + ext)) {
            path += "." + ext;
        }
        return path;
    }

    private void onBackup() throws Exception {
        requireDb();
        String path = chooseFile("Backup базы (*.zip)", "zip", true);
        if (path == null)
            return;
        db.backup(path);
        JOptionPane.showMessageDialog(this, "Backup сохранён: " + path);
    }

    private void onRestore() throws Exception {
        requireDb();
        String path = chooseFile("Backup базы (*.zip)", "zip", false);
        if (path == null)
            return;
        if (!confirm("Текущие данные будут заменены данными из backup. Продолжить?"))
            return;
        String base = db.getBasePath();
        closeDb();
        db = Database.restore(path, base);
        refresh();
    }

    private void onExport() throws Exception {
        requireDb();
        String path = chooseFile("Excel (*.xlsx)", "xlsx", true);
        if (path == null)
            return;
        int n = XlsxExporter.export(db, path);
        JOptionPane.showMessageDialog(this, "Выгружено записей: " + n);
    }
}
