package com.devpilot.ui;

import com.devpilot.service.GitHubFileService.RepoFile;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Folders and files, as in a code editor. Click or Enter opens a folder or file. */
class FileTreeView extends TreeView<FileTreeView.Entry> {
    /** file is null for a folder. */
    record Entry(String name, String path, RepoFile file) { }

    private static final Map<String, String[]> ICONS = Map.ofEntries( // ending -> text, color
            Map.entry("java", new String[]{"J", "#b07219"}), Map.entry("py", new String[]{"Py", "#3572a5"}),
            Map.entry("js", new String[]{"JS", "#8a7400"}), Map.entry("ts", new String[]{"TS", "#3178c6"}),
            Map.entry("html", new String[]{"<>", "#c23d17"}), Map.entry("xml", new String[]{"<>", "#a33b2e"}),
            Map.entry("css", new String[]{"#", "#563d7c"}), Map.entry("sql", new String[]{"S", "#8a5a00"}),
            Map.entry("md", new String[]{"M", "#4a6785"}), Map.entry("json", new String[]{"{}", "#6b6b00"}));

    private final Consumer<RepoFile> onOpen;

    FileTreeView(Consumer<RepoFile> onOpen) {
        this.onOpen = onOpen;
        setId("files");
        setShowRoot(false);
        setFixedCellSize(28);
        setCellFactory(view -> new EntryCell());
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            TreeItem<Entry> item = getSelectionModel().getSelectedItem();
            if (e.getCode() != KeyCode.ENTER || item == null) return;
            activate(item);
            e.consume(); // otherwise Enter would also press Send
        });
    }

    /** Replaces the files but keeps folders open that were open before. */
    void show(List<RepoFile> files) {
        Set<String> open = new HashSet<>();
        if (getRoot() != null) collectOpen(getRoot(), open);
        TreeItem<Entry> root = new TreeItem<>(new Entry("", "", null));
        Folder top = new Folder();
        for (RepoFile file : files) top.add(file);
        fill(root, top, "", open);
        root.setExpanded(true);
        setRoot(root);
    }

    void clearFileSelection() {
        getSelectionModel().clearSelection();
    }

    private void activate(TreeItem<Entry> item) {
        getSelectionModel().select(item);
        if (item.getValue().file() != null) onOpen.accept(item.getValue().file());
        else item.setExpanded(!item.isExpanded());
    }

    private static void collectOpen(TreeItem<Entry> item, Set<String> open) {
        for (TreeItem<Entry> child : item.getChildren()) {
            if (child.isExpanded()) open.add(child.getValue().path());
            collectOpen(child, open);
        }
    }

    private static void fill(TreeItem<Entry> parent, Folder folder, String path, Set<String> open) {
        folder.folders.forEach((name, child) -> {
            String label = name;
            Folder shown = child;
            // "src" containing only "main" is shown as "src/main"
            while (shown.files.isEmpty() && shown.folders.size() == 1) {
                Map.Entry<String, Folder> only = shown.folders.firstEntry();
                label += "/" + only.getKey();
                shown = only.getValue();
            }
            TreeItem<Entry> item = new TreeItem<>(new Entry(label, path + label, null));
            fill(item, shown, path + label + "/", open);
            item.setExpanded(open.contains(path + label));
            parent.getChildren().add(item);
        });
        folder.files.forEach((name, file) ->
                parent.getChildren().add(new TreeItem<>(new Entry(name, file.path(), file))));
    }

    /** Used only while turning paths into a tree. */
    private static class Folder {
        private static final Comparator<String> BY_NAME =
                String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
        final TreeMap<String, Folder> folders = new TreeMap<>(BY_NAME);
        final TreeMap<String, RepoFile> files = new TreeMap<>(BY_NAME);

        void add(RepoFile file) {
            Folder folder = this;
            String[] parts = file.path().split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                folder = folder.folders.computeIfAbsent(parts[i], name -> new Folder());
            }
            folder.files.put(parts[parts.length - 1], file);
        }
    }

    private class EntryCell extends TreeCell<Entry> {
        private final Label icon = new Label();

        EntryCell() {
            icon.getStyleClass().add("file-icon");
            // Handle clicks here so that one click opens, wherever on the row it lands
            addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (e.getButton() != MouseButton.PRIMARY || getTreeItem() == null) return;
                FileTreeView.this.requestFocus();
                activate(getTreeItem());
                e.consume();
            });
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) { // cells are reused, so clear old content
                setText(null);
                setGraphic(null);
                return;
            }
            setText(entry.name());
            if (entry.file() == null) {
                setGraphic(null);
                return;
            }
            String ending = entry.name().substring(entry.name().lastIndexOf('.') + 1).toLowerCase();
            String[] style = ICONS.getOrDefault(ending, new String[]{"·", "#66666b"});
            icon.setText(style[0]);
            icon.setStyle("-fx-text-fill: " + style[1] + ";");
            setGraphic(icon);
        }
    }
}
