package com.clientFX;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    public static UtilsWS wsClient;

    public static String playerName = "";

    public static CtrlConfig ctrlConfig;
    public static CtrlGame ctrlGame;
    public static CtrlRanking ctrlRanking;

    public static void main(String[] args) {

        // Iniciar app JavaFX
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 800;
        final int windowHeight = 700;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml");
        UtilsViews.addView(getClass(), "ViewGame", "/assets/viewGame.fxml");
        UtilsViews.addView(getClass(), "ViewRanking", "/assets/viewRanking.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlGame = (CtrlGame) UtilsViews.getController("ViewGame");
        ctrlRanking = (CtrlRanking) UtilsViews.getController("ViewRanking");

        Scene scene = new Scene(UtilsViews.parentContainer, windowWidth, windowHeight);

        // El teclat només s'envia al tauler quan la vista de partida és la activa
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if ("ViewGame".equals(UtilsViews.getActiveView())) {
                ctrlGame.handleKey(event);
            }
        });

        stage.setScene(scene);
        stage.setTitle("Sudoku multijugador");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Add icon only if not Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            // try-with-resources: si no es tanca el stream, Windows deixa icon.png
            // bloquejat i un altre "mvn clean" no pot esborrar target/
            try (var iconStream = Main.class.getResourceAsStream("/icons/icon.png")) {
                if (iconStream != null) {
                    stage.getIcons().add(new Image(iconStream));
                }
            }
        }
    }

    @Override
    public void stop() {
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(0); // Kill all executor services
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    public static <T> List<T> jsonArrayToList(JSONArray array, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            T value = clazz.cast(array.get(i));
            list.add(value);
        }
        return list;
    }

    // ----------------- Connexió -----------------

    public static void connectToServer() {

        String name = ctrlConfig.txtName.getText().trim();
        if (name.isEmpty()) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText("Escriu el teu nom");
            return;
        }
        playerName = name;

        ctrlConfig.btnConnect.setDisable(true);
        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connecting ...");

        pauseDuring(1000, () -> { // Give time to show connecting message ...

            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            wsClient = UtilsWS.getSharedInstance(protocol + "://" + host + ":" + port);

            // Platform.runlater assegura que el codi s'executi
            // al fil de la UI, per evitar problemes de concurrència amb JavaFX
            wsClient.onOpen((response) -> sendJoin());
            wsClient.onMessage((response) -> { Platform.runLater(() -> { wsMessage(response); }); });
            wsClient.onError((response) -> { Platform.runLater(() -> { wsError(response); }); });
            wsClient.onClose((response) -> { Platform.runLater(() -> { wsClose(); }); });

            // Si la connexió ja s'ha obert abans de registrar els callbacks
            // (el servidor ignora un join repetit)
            if (wsClient.isOpen()) {
                sendJoin();
            }
        });
    }

    // Presentar-se al servidor amb el nom del jugador
    private static void sendJoin() {
        JSONObject obj = new JSONObject();
        obj.put("type", "join");
        obj.put("name", playerName);
        wsClient.safeSend(obj.toString());
    }

    // Botó "Tornar a jugar" de la vista de ranking
    public static void playAgain() {
        JSONObject obj = new JSONObject();
        obj.put("type", "playAgain");
        wsClient.safeSend(obj.toString());
        ctrlGame.showMessage("", Color.BLACK);
        UtilsViews.setViewAnimating("ViewGame");
    }

    // ----------------- Missatges del servidor -----------------

    private static void wsMessage(String response) {
        // Fer aquí els canvis a la interficie
        JSONObject msgObj = new JSONObject(response);
        String type = msgObj.optString("type", "");

        switch (type) {
            case "joined" -> {
                ctrlGame.showMessage("", Color.BLACK);
                // El servidor pot haver canviat el nom si ja n'hi havia un igual
                playerName = msgObj.getString("id");
                ctrlGame.setPlayerName(playerName);
            }
            case "state" -> handleState(msgObj);
            case "result" -> ctrlGame.showResult(msgObj);
            case "error" -> System.out.println("Server error: " + msgObj.optString("message"));
            default -> { }
        }
    }

    private static void handleState(JSONObject state) {
        ctrlGame.updateState(state);

        String active = UtilsViews.getActiveView();
        boolean finished = "finished".equals(state.getString("status"));

        if (finished) {
            // Partida acabada: ranking final de tots els jugadors
            ctrlRanking.setRanking(state.getJSONArray("players"), playerName);
            if (!"ViewRanking".equals(active)) {
                UtilsViews.setViewAnimating("ViewRanking");
            }
        } else if ("ViewConfig".equals(active)) {
            // Primer estat rebut després de connectar: a jugar
            UtilsViews.setViewAnimating("ViewGame");
        }
    }

    private static void wsError(String response) {

        // Només es mostra a la vista de configuració (mentre encara no s'ha connectat)
        if (!"ViewConfig".equals(UtilsViews.getActiveView())) {
            return;
        }

        String connectionRefused = "Connection refused";
        String text = response.contains(connectionRefused) ? connectionRefused : "Error de connexió";

        ctrlConfig.txtMessage.setTextFill(Color.RED);
        ctrlConfig.txtMessage.setText(text);

        // Descartem la connexió per poder provar una altra adreça
        UtilsWS.resetSharedInstance();
        ctrlConfig.btnConnect.setDisable(false);

        pauseDuring(2500, () -> {
            ctrlConfig.txtMessage.setText("");
        });
    }

    private static void wsClose() {
        // Si es perd la connexió durant la partida, UtilsWS reintenta cada 5 s
        if ("ViewGame".equals(UtilsViews.getActiveView())) {
            ctrlGame.showMessage("Connexió perduda, reconnectant ...", Color.web("#c62828"));
        }
    }
}
