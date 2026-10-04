package com.clientFX;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class CtrlRanking implements Initializable {

    @FXML
    private Label txtWinner;

    @FXML
    private VBox rankingBox;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    /** Mostra la llista de jugadors (ja ordenada per punts pel servidor). */
    public void setRanking(JSONArray players, String me) {
        rankingBox.getChildren().clear();

        int best = Integer.MIN_VALUE;
        for (int i = 0; i < players.length(); i++) {
            best = Math.max(best, players.getJSONObject(i).getInt("score"));
        }
        List<String> winners = new ArrayList<>();

        for (int i = 0; i < players.length(); i++) {
            JSONObject p = players.getJSONObject(i);
            String name = p.getString("name");
            int score = p.getInt("score");
            if (score == best) winners.add(name);

            boolean isMe = name.equals(me);
            String style = "-fx-font-size: 20px; -fx-font-weight: " + (isMe ? "bold" : "normal") + ";";

            Label lblPos = new Label((i + 1) + ".");
            lblPos.setMinWidth(36);
            lblPos.setStyle(style);
            Label lblName = new Label(name);
            lblName.setStyle(style);
            Label lblScore = new Label(score + " punts");
            lblScore.setStyle(style);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox line = new HBox(8, lblPos, lblName, spacer, lblScore);
            line.setAlignment(Pos.CENTER_LEFT);
            line.setPadding(new Insets(6, 12, 6, 12));
            if (isMe) {
                line.setStyle("-fx-background-color: #dbeafe; -fx-background-radius: 6;");
            }
            rankingBox.getChildren().add(line);
        }

        if (winners.isEmpty()) {
            txtWinner.setText("");
        } else if (winners.size() == 1) {
            txtWinner.setText("Guanyador: " + winners.get(0));
        } else {
            txtWinner.setText("Empat: " + String.join(", ", winners));
        }
    }

    @FXML
    private void playAgain() {
        Main.playAgain();
    }
}
