package com.devpilot.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows an answer with simple Markdown: paragraphs, headings, lists, bold, inline code
 * and code blocks. Anything else is shown as plain text.
 */
class AnswerView extends VBox {
    private static final String SOURCES = "\n\nSearched excerpts:\n"; // added by the RAG service
    private static final Pattern INLINE = Pattern.compile("`([^`\n]+)`|\\*\\*(.+?)\\*\\*");
    private static final Pattern HEADING = Pattern.compile("#{1,6}\\s+(.+)");
    private static final Pattern ITEM = Pattern.compile("(\\s*)([-*•]|\\d{1,3}[.)])\\s+(.+)");
    private static final Pattern FENCE = Pattern.compile("\\s*```\\s*(\\S*).*");

    AnswerView(String answer) {
        super(14);
        getStyleClass().add("answer-view");
        String text = answer == null ? "" : answer.replace("\r\n", "\n");
        int split = text.lastIndexOf(SOURCES);
        addBlocks(split < 0 ? text : text.substring(0, split));
        if (split >= 0) addSources(text.substring(split + SOURCES.length()).strip());
    }

    private void addBlocks(String text) {
        List<String> paragraph = new ArrayList<>();
        List<String> code = null; // not null while inside a code block
        String language = "";
        for (String line : text.split("\n", -1)) {
            Matcher fence = FENCE.matcher(line);
            if (code != null) {
                if (line.strip().startsWith("```")) {
                    addCode(language, code);
                    code = null;
                } else {
                    code.add(line);
                }
                continue;
            }
            Matcher heading = HEADING.matcher(line);
            Matcher item = ITEM.matcher(line);
            if (fence.matches()) {
                addParagraph(paragraph);
                language = fence.group(1);
                code = new ArrayList<>();
            } else if (line.isBlank()) {
                addParagraph(paragraph);
            } else if (heading.matches()) {
                addParagraph(paragraph);
                TextFlow flow = inline(heading.group(1));
                flow.getStyleClass().add("answer-heading");
                getChildren().add(flow);
            } else if (item.matches()) {
                addParagraph(paragraph);
                addItem(item.group(1).length(), item.group(2), item.group(3));
            } else {
                paragraph.add(line.strip());
            }
        }
        if (code != null) addCode(language, code); // the closing ``` is missing
        addParagraph(paragraph);
    }

    private void addParagraph(List<String> lines) {
        if (lines.isEmpty()) return;
        getChildren().add(inline(String.join("\n", lines)));
        lines.clear();
    }

    private void addItem(int indent, String marker, String text) {
        Label bullet = new Label(Character.isDigit(marker.charAt(0)) ? marker : "•");
        bullet.setMinWidth(22);
        TextFlow flow = inline(text);
        HBox.setHgrow(flow, Priority.ALWAYS);
        HBox row = new HBox(4, bullet, flow);
        row.setStyle("-fx-padding: 0 0 0 " + Math.min(indent, 8) * 6 + "px;");
        getChildren().add(row);
    }

    private void addCode(String language, List<String> lines) {
        Label code = new Label(String.join("\n", lines));
        code.getStyleClass().add("code-text");
        code.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE); // never shortened with "..."
        ScrollPane scroll = new ScrollPane(code); // long lines scroll sideways
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinHeight(Region.USE_PREF_SIZE);
        VBox block = new VBox();
        block.getStyleClass().add("code-block");
        if (!language.isBlank()) {
            Label title = new Label(language);
            title.getStyleClass().add("code-title");
            title.setMaxWidth(Double.MAX_VALUE);
            block.getChildren().add(title);
        }
        block.getChildren().add(scroll);
        block.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().add(block);
    }

    private void addSources(String text) {
        if (text.isEmpty()) return;
        List<String> sources = text.lines().toList();
        VBox list = new VBox(4);
        for (String source : sources) {
            Label label = new Label(source);
            label.setWrapText(true);
            label.setMinHeight(Region.USE_PREF_SIZE);
            label.getStyleClass().add("source");
            list.getChildren().add(label);
        }
        TitledPane pane = new TitledPane("Searched excerpts (" + sources.size() + ")", list);
        pane.setAnimated(false);
        pane.setExpanded(false);
        pane.getStyleClass().add("sources");
        getChildren().add(pane);
    }

    /** One wrapping paragraph with bold and code parts. */
    private static TextFlow inline(String text) {
        List<Node> parts = new ArrayList<>();
        Matcher match = INLINE.matcher(text);
        int end = 0;
        while (match.find()) {
            add(parts, text.substring(end, match.start()), "answer-text");
            if (match.group(1) != null) add(parts, match.group(1), "answer-code");
            // **`name`** is bold code: keep the code look and drop the marks
            else if (match.group(2).matches("`[^`]+`")) add(parts, match.group(2).replace("`", ""), "answer-code");
            else add(parts, match.group(2).replace("`", ""), "answer-bold");
            end = match.end();
        }
        add(parts, text.substring(end), "answer-text");
        TextFlow flow = new TextFlow(parts.toArray(Node[]::new));
        flow.setLineSpacing(4);
        flow.setMinHeight(Region.USE_PREF_SIZE);
        return flow;
    }

    private static void add(List<Node> parts, String text, String styleClass) {
        if (text.isEmpty()) return;
        Text part = new Text(text);
        part.getStyleClass().add(styleClass);
        parts.add(part);
    }
}
