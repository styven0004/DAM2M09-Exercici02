package com.clientFX;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class CtrlGame implements Initializable {

    private static final int SIZE = 9;
    private static final int CELL = 50;

    @FXML
    private Label txtPlayer, txtMessage;

    @FXML
    private GridPane boardGrid;

    @FXML
    private HBox padBox;

    @FXML
    private VBox playersBox;

    private final Label[][] cells = new Label[SIZE][SIZE];
    private final int[][] values = new int[SIZE][SIZE];
    private final String[][] kinds = new String[SIZE][SIZE]; // "empty", "given", "locked"

    // Valors incorrectes que es mostren en vermell un moment (clau = fila * 9 + columna)
    private final Map<Integer, Integer> wrongFlash = new HashMap<>();

    private int selRow = -1;
    private int selCol = -1;
    private String playerName = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Tauler 9x9
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                kinds[r][c] = "empty";

                Label cell = new Label();
                cell.setMinSize(CELL, CELL);
                cell.setPrefSize(CELL, CELL);
                cell.setMaxSize(CELL, CELL);
                cell.setAlignment(Pos.CENTER);

                final int row = r;
                final int col = c;
                cell.setOnMouseClicked(event -> select(row, col));

                cells[r][c] = cell;
                boardGrid.add(cell, c, r);
                refreshCell(r, c);
            }
        }

        // Teclat numèric en pantalla (1..9)
        for (int v = 1; v <= SIZE; v++) {
            final int value = v;
            Button btn = new Button(String.valueOf(v));
            btn.setMinSize(40, 40);
            btn.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
            btn.setFocusTraversable(false);
            btn.setOnAction(event -> enterValue(value));
            padBox.getChildren().add(btn);
        }
    }

    // ----------------- Estat que arriba del servidor -----------------

    public void setPlayerName(String name) {
        this.playerName = name;
        txtPlayer.setText("Juga: " + name);
    }

    /** Actualitza tauler i llista de jugadors amb el missatge "state" del servidor. */
    public void updateState(JSONObject state) {
        JSONArray board = state.getJSONArray("board");
        for (int r = 0; r < SIZE; r++) {
            JSONArray row = board.getJSONArray(r);
            for (int c = 0; c < SIZE; c++) {
                JSONObject cell = row.getJSONObject(c);
                values[r][c] = cell.getInt("v");
                kinds[r][c] = cell.getString("k");
                if (!kinds[r][c].equals("empty")) {
                    wrongFlash.remove(r * SIZE + c);
                }
                refreshCell(r, c);
            }
        }

        // Llista de jugadors (el servidor ja l'envia ordenada per punts)
        JSONArray players = state.getJSONArray("players");
        playersBox.getChildren().clear();
        for (int i = 0; i < players.length(); i++) {
            JSONObject p = players.getJSONObject(i);
            String name = p.getString("name");
            boolean me = name.equals(playerName);

            Label lblName = new Label(name);
            Label lblScore = new Label(String.valueOf(p.getInt("score")));
            String weight = me ? "bold" : "normal";
            lblName.setStyle("-fx-font-size: 16px; -fx-font-weight: " + weight + ";");
            lblScore.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox line = new HBox(8, lblName, spacer, lblScore);
            line.setAlignment(Pos.CENTER_LEFT);
            line.setPadding(new Insets(4, 8, 4, 8));
            if (me) {
                line.setStyle("-fx-background-color: #dbeafe; -fx-background-radius: 6;");
            }
            playersBox.getChildren().add(line);
        }
    }

    /** Resposta a una jugada pròpia: missatge de feedback i, si és incorrecta, parpelleig en vermell. */
    public void showResult(JSONObject result) {
        String status = result.getString("status");
        int row = result.optInt("row", -1);
        int col = result.optInt("col", -1);
        int value = result.optInt("value", 0);

        switch (status) {
            case "correct" -> showMessage("Correcte! +" + result.optInt("delta", 2), Color.web("#1b7a3d"));
            case "wrong" -> {
                showMessage("Incorrecte, " + result.optInt("delta", -1), Color.web("#c62828"));
                int key = row * SIZE + col;
                wrongFlash.put(key, value);
                refreshCell(row, col);
                PauseTransition pause = new PauseTransition(Duration.millis(800));
                pause.setOnFinished(event -> {
                    wrongFlash.remove(key);
                    refreshCell(row, col);
                });
                pause.play();
            }
            case "locked" -> showMessage("Aquesta casella ja està bloquejada", Color.web("#8a6d00"));
            default -> { }
        }
    }

    public void showMessage(String text, Color color) {
        txtMessage.setTextFill(color);
        txtMessage.setText(text);
    }

    // ----------------- Interacció del jugador -----------------

    private void select(int row, int col) {
        int oldRow = selRow;
        int oldCol = selCol;
        selRow = row;
        selCol = col;
        if (oldRow >= 0) refreshCell(oldRow, oldCol);
        refreshCell(row, col);
    }

    /** Teclat: 1-9 per posar valor, fletxes per moure la selecció. */
    public void handleKey(KeyEvent event) {
        KeyCode code = event.getCode();
        int dr = 0;
        int dc = 0;
        if (code == KeyCode.UP) dr = -1;
        else if (code == KeyCode.DOWN) dr = 1;
        else if (code == KeyCode.LEFT) dc = -1;
        else if (code == KeyCode.RIGHT) dc = 1;

        if (dr != 0 || dc != 0) {
            int r = selRow < 0 ? 0 : Math.max(0, Math.min(SIZE - 1, selRow + dr));
            int c = selCol < 0 ? 0 : Math.max(0, Math.min(SIZE - 1, selCol + dc));
            select(r, c);
            event.consume();
            return;
        }

        String text = event.getText();
        if (text != null && text.length() == 1 && text.charAt(0) >= '1' && text.charAt(0) <= '9') {
            enterValue(text.charAt(0) - '0');
            event.consume();
        }
    }

    private void enterValue(int value) {
        if (selRow < 0) {
            showMessage("Primer selecciona una casella", Color.web("#555555"));
            return;
        }
        if (!kinds[selRow][selCol].equals("empty")) {
            showMessage("Aquesta casella no es pot canviar", Color.web("#8a6d00"));
            return;
        }

        JSONObject obj = new JSONObject();
        obj.put("type", "move");
        obj.put("row", selRow);
        obj.put("col", selCol);
        obj.put("value", value);
        Main.wsClient.safeSend(obj.toString());
    }

    // ----------------- Pintat de caselles -----------------

    private void refreshCell(int r, int c) {
        Label cell = cells[r][c];
        Integer flash = wrongFlash.get(r * SIZE + c);

        String text = "";
        if (values[r][c] != 0) text = String.valueOf(values[r][c]);
        else if (flash != null) text = String.valueOf(flash);
        cell.setText(text);

        boolean selected = (r == selRow && c == selCol);
        String bg;
        String fg;
        String weight = "bold";

        if (flash != null && values[r][c] == 0) {
            bg = "#ffb3b3";
            fg = "#b00020";
        } else {
            switch (kinds[r][c]) {
                case "given" -> {
                    bg = selected ? "#c2c2c2" : "#e4e4e4";
                    fg = "#111111";
                }
                case "locked" -> { // encertada: fons verd i bloquejada
                    bg = selected ? "#6fcf97" : "#b7e4c7";
                    fg = "#1b4332";
                }
                default -> {
                    bg = selected ? "#a9d6ff" : "#ffffff";
                    fg = "#0b3d91";
                    weight = "normal";
                }
            }
        }

        // Línies gruixudes per marcar les 9 regions de 3x3
        int top = (r % 3 == 0) ? 3 : 1;
        int left = (c % 3 == 0) ? 3 : 1;
        int bottom = (r == SIZE - 1) ? 3 : 0;
        int right = (c == SIZE - 1) ? 3 : 0;

        cell.setStyle("-fx-background-color: " + bg + ";"
                + "-fx-text-fill: " + fg + ";"
                + "-fx-font-size: 22px;"
                + "-fx-font-weight: " + weight + ";"
                + "-fx-border-color: #222222;"
                + "-fx-border-width: " + top + " " + right + " " + bottom + " " + left + ";");
    }
}
