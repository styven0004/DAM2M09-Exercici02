package com.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Estat de la partida de Sudoku compartida per tots els jugadors.
 *
 * Tots els mètodes públics són sincronitzats: es poden cridar des de
 * diferents fils de WebSocket. Qui necessiti un instantani coherent
 * (p. ex. per serialitzar l'estat) pot fer synchronized(game).
 */
final class SudokuGame {

    /** Resultat d'intentar posar un valor en una casella. */
    enum MoveResult { CORRECT, WRONG, LOCKED, FINISHED, INVALID }

    static final int SIZE = SudokuPuzzle.SIZE;
    static final int HOLES = 45;
    static final int POINTS_CORRECT = 2;
    static final int POINTS_WRONG = -1;

    private final Random rnd = new Random();
    private final Map<String, Integer> scores = new HashMap<>();

    private SudokuPuzzle puzzle;
    private boolean[][] filled = new boolean[SIZE][SIZE];
    private String[][] owner = new String[SIZE][SIZE];
    private boolean finished;

    SudokuGame() {
        newGame();
    }

    /** Genera un trencaclosques nou i posa a zero els punts de tots els jugadors. */
    synchronized void newGame() {
        puzzle = SudokuPuzzle.generate(HOLES, rnd);
        filled = new boolean[SIZE][SIZE];
        owner = new String[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) filled[r][c] = puzzle.given[r][c];
        }
        scores.replaceAll((name, pts) -> 0);
        finished = false;
    }

    /** Afegeix un jugador (si la sala estava buida i la partida acabada, comença una de nova). */
    synchronized void addPlayer(String name) {
        if (scores.isEmpty() && finished) newGame();
        scores.putIfAbsent(name, 0);
    }

    /** Treu un jugador. Si la sala es queda buida, es prepara una partida nova. */
    synchronized void removePlayer(String name) {
        scores.remove(name);
        if (scores.isEmpty()) newGame();
    }

    /**
     * Intenta posar un valor. Si és correcte la casella queda bloquejada
     * i el jugador suma 2 punts; si no, resta 1 punt.
     */
    synchronized MoveResult move(String player, int row, int col, int value) {
        if (!scores.containsKey(player)) return MoveResult.INVALID;
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE || value < 1 || value > 9) {
            return MoveResult.INVALID;
        }
        if (finished) return MoveResult.FINISHED;
        if (filled[row][col]) return MoveResult.LOCKED;

        if (puzzle.solution[row][col] == value) {
            filled[row][col] = true;
            owner[row][col] = player;
            scores.merge(player, POINTS_CORRECT, Integer::sum);
            finished = allFilled();
            return MoveResult.CORRECT;
        }
        scores.merge(player, POINTS_WRONG, Integer::sum);
        return MoveResult.WRONG;
    }

    private boolean allFilled() {
        for (boolean[] row : filled) {
            for (boolean b : row) if (!b) return false;
        }
        return true;
    }

    synchronized boolean isFinished() { return finished; }

    /** Valor visible de la casella (0 si està buida). */
    synchronized int valueAt(int r, int c) { return filled[r][c] ? puzzle.solution[r][c] : 0; }

    synchronized boolean isGiven(int r, int c) { return puzzle.given[r][c]; }

    /** Jugador que ha encertat la casella (null si és pista o buida). */
    synchronized String ownerAt(int r, int c) { return owner[r][c]; }

    /** Jugadors ordenats per punts (descendent) i després per nom. */
    synchronized List<Map.Entry<String, Integer>> ranking() {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            return cmp != 0 ? cmp : a.getKey().compareToIgnoreCase(b.getKey());
        });
        return list;
    }

    // Només per a proves
    synchronized int solutionAt(int r, int c) { return puzzle.solution[r][c]; }
}
