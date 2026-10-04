package com.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Servidor WebSocket del Sudoku multijugador.
 *
 * El servidor és l'autoritat de la partida: genera el trencaclosques, valida
 * cada jugada, porta les puntuacions i avisa tots els clients de l'estat.
 *
 * Missatges client -> servidor:
 *  - join:      {type:"join", name:"Albert"}           entra a la partida
 *  - move:      {type:"move", row:0, col:2, value:4}   intenta posar un valor
 *  - playAgain: {type:"playAgain"}                     demana una partida nova (si l'actual ha acabat)
 *
 * Missatges servidor -> client:
 *  - joined:  {type:"joined", id:"Albert"}             nom final assignat
 *  - state:   {type:"state", status, board, players}   estat complet de la partida
 *  - result:  {type:"result", status, row, col, value, delta}   resposta a un move (només a qui l'ha fet)
 *  - error:   {type:"error", message}
 */
public class Main extends WebSocketServer {

    /** Port per defecte on escolta el servidor. */
    public static final int DEFAULT_PORT = 3000;

    // Claus JSON
    private static final String K_TYPE = "type";
    private static final String K_MESSAGE = "message";
    private static final String K_ID = "id";
    private static final String K_NAME = "name";
    private static final String K_ROW = "row";
    private static final String K_COL = "col";
    private static final String K_VALUE = "value";
    private static final String K_DELTA = "delta";
    private static final String K_STATUS = "status";
    private static final String K_BOARD = "board";
    private static final String K_PLAYERS = "players";
    private static final String K_SCORE = "score";
    private static final String K_CELL_VALUE = "v"; // valor dins de cada casella del tauler
    private static final String K_KIND = "k";
    private static final String K_OWNER = "o";

    // Tipus de missatge
    private static final String T_JOIN = "join";
    private static final String T_JOINED = "joined";
    private static final String T_MOVE = "move";
    private static final String T_PLAY_AGAIN = "playAgain";
    private static final String T_STATE = "state";
    private static final String T_RESULT = "result";
    private static final String T_ERROR = "error";

    /** Registre de clients (noms únics). */
    private final ClientRegistry clients = new ClientRegistry();

    /** Partida compartida per tots els jugadors. */
    private final SudokuGame game = new SudokuGame();

    public Main(InetSocketAddress address) {
        super(address);
    }

    // ----------------- Helpers JSON -----------------

    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    /**
     * Envia de forma segura un payload i, si el socket no està connectat,
     * el neteja del registre.
     */
    private void sendSafe(WebSocket to, String payload) {
        if (to == null) return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            String name = clients.cleanupDisconnected(to);
            if (name != null) game.removePlayer(name);
            System.out.println("Client desconnectat durant send: " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Construeix el missatge d'estat complet (tauler + jugadors ordenats per punts). */
    private JSONObject buildState() {
        synchronized (game) {
            JSONArray board = new JSONArray();
            for (int r = 0; r < SudokuGame.SIZE; r++) {
                JSONArray row = new JSONArray();
                for (int c = 0; c < SudokuGame.SIZE; c++) {
                    int value = game.valueAt(r, c);
                    JSONObject cell = new JSONObject().put(K_CELL_VALUE, value);
                    if (value == 0) {
                        cell.put(K_KIND, "empty");
                    } else if (game.isGiven(r, c)) {
                        cell.put(K_KIND, "given");
                    } else {
                        cell.put(K_KIND, "locked");
                        cell.put(K_OWNER, game.ownerAt(r, c));
                    }
                    row.put(cell);
                }
                board.put(row);
            }

            JSONArray players = new JSONArray();
            for (Map.Entry<String, Integer> e : game.ranking()) {
                players.put(new JSONObject().put(K_NAME, e.getKey()).put(K_SCORE, e.getValue()));
            }

            return msg(T_STATE)
                    .put(K_STATUS, game.isFinished() ? "finished" : "playing")
                    .put(K_BOARD, board)
                    .put(K_PLAYERS, players);
        }
    }

    /** Envia l'estat actual a tots els jugadors registrats. */
    private void broadcastState() {
        String payload = buildState().toString();
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            sendSafe(e.getKey(), payload);
        }
    }

    // ----------------- WebSocketServer overrides -----------------

    /** El jugador no existeix fins que envia "join" amb el seu nom. */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Connexió oberta: " + conn.getRemoteSocketAddress());
    }

    /** Treu el jugador de la partida i notifica l'estat actualitzat. */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        if (name != null) {
            game.removePlayer(name);
            System.out.println("Jugador desconnectat: " + name);
            broadcastState();
        }
    }

    /** Processa el missatge rebut i el ruteja segons el seu type. */
    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "JSON invàlid").toString());
            return;
        }

        String type = obj.optString(K_TYPE, "");
        String player = clients.nameBySocket(conn);

        if (!T_JOIN.equals(type) && player == null) {
            sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Cal enviar 'join' abans de jugar").toString());
            return;
        }

        switch (type) {
            case T_JOIN -> {
                if (player == null) {
                    player = clients.register(conn, obj.optString(K_NAME, ""));
                    game.addPlayer(player);
                    System.out.println("Jugador connectat: " + player);
                    sendSafe(conn, msg(T_JOINED).put(K_ID, player).toString());
                    broadcastState();
                } else {
                    // join repetit: no es registra de nou, només es torna a informar
                    sendSafe(conn, msg(T_JOINED).put(K_ID, player).toString());
                    sendSafe(conn, buildState().toString());
                }
            }
            case T_MOVE -> {
                int row = obj.optInt(K_ROW, -1);
                int col = obj.optInt(K_COL, -1);
                int value = obj.optInt(K_VALUE, -1);
                SudokuGame.MoveResult res = game.move(player, row, col, value);

                int delta = switch (res) {
                    case CORRECT -> SudokuGame.POINTS_CORRECT;
                    case WRONG -> SudokuGame.POINTS_WRONG;
                    default -> 0;
                };
                sendSafe(conn, msg(T_RESULT)
                        .put(K_STATUS, res.name().toLowerCase())
                        .put(K_ROW, row)
                        .put(K_COL, col)
                        .put(K_VALUE, value)
                        .put(K_DELTA, delta)
                        .toString());

                // Si ha canviat el tauler o els punts, tothom ho ha de veure
                if (res == SudokuGame.MoveResult.CORRECT || res == SudokuGame.MoveResult.WRONG) {
                    broadcastState();
                }
            }
            case T_PLAY_AGAIN -> {
                if (game.isFinished()) {
                    game.newGame();
                    System.out.println("Partida nova (demanada per " + player + ")");
                    broadcastState();
                } else {
                    // Algú ja ha començat la partida nova: només sincronitzem aquest client
                    sendSafe(conn, buildState().toString());
                }
            }
            default -> sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Tipus desconegut: " + type).toString());
        }
    }

    /** Log d'error global o de socket concret. */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    /** Arrencada: log i configuració del timeout de connexió perduda. */
    @Override
    public void onStart() {
        System.out.println("Servidor WebSocket engegat al port: " + getPort());
        setConnectionLostTimeout(100);
    }

    // ----------------- Lifecycle util -----------------

    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Aturant servidor (shutdown hook)...");
            try {
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor aturat.");
        }));
    }

    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Servidor WebSocket en execució al port " + DEFAULT_PORT + ". Prem Ctrl+C per aturar-lo.");
        awaitForever();
    }
}
