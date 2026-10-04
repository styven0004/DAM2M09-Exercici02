package com.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generador de trencaclosques de Sudoku amb solució única.
 *
 * Primer genera una graella completa amb backtracking aleatori i després
 * va buidant caselles, comprovant que el trencaclosques continua tenint
 * una única solució. Així, el valor "correcte" d'una casella és sempre
 * el de la solució i cap jugador pot ser penalitzat per una alternativa vàlida.
 */
final class SudokuPuzzle {

    static final int SIZE = 9;
    static final int BOX = 3;

    /** Solució completa. */
    final int[][] solution = new int[SIZE][SIZE];

    /** True si la casella és una pista inicial (visible des del principi). */
    final boolean[][] given = new boolean[SIZE][SIZE];

    private SudokuPuzzle() {}

    /**
     * Genera un trencaclosques nou.
     *
     * @param holes nombre de caselles buides desitjat (màxim aproximat; es
     *              pot quedar per sota si no es pot mantenir la solució única)
     * @param rnd   font d'aleatorietat
     * @return trencaclosques generat
     */
    static SudokuPuzzle generate(int holes, Random rnd) {
        SudokuPuzzle p = new SudokuPuzzle();
        fill(p.solution, rnd);

        int[][] work = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            work[r] = p.solution[r].clone();
            for (int c = 0; c < SIZE; c++) p.given[r][c] = true;
        }

        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < SIZE * SIZE; i++) positions.add(i);
        Collections.shuffle(positions, rnd);

        int removed = 0;
        for (int pos : positions) {
            if (removed >= holes) break;
            int r = pos / SIZE, c = pos % SIZE;
            int backup = work[r][c];
            work[r][c] = 0;
            if (countSolutions(work, 2) == 1) {
                p.given[r][c] = false;
                removed++;
            } else {
                work[r][c] = backup;
            }
        }
        return p;
    }

    /** Omple la graella completa amb backtracking i dígits barrejats. */
    private static boolean fill(int[][] g, Random rnd) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (g[r][c] != 0) continue;
                List<Integer> digits = new ArrayList<>();
                for (int d = 1; d <= 9; d++) digits.add(d);
                Collections.shuffle(digits, rnd);
                for (int d : digits) {
                    if (canPlace(g, r, c, d)) {
                        g[r][c] = d;
                        if (fill(g, rnd)) return true;
                        g[r][c] = 0;
                    }
                }
                return false;
            }
        }
        return true;
    }

    /** Comprova que d no apareix a la fila, columna ni regió de (r,c). */
    static boolean canPlace(int[][] g, int r, int c, int d) {
        for (int i = 0; i < SIZE; i++) {
            if (g[r][i] == d || g[i][c] == d) return false;
        }
        int br = r - r % BOX, bc = c - c % BOX;
        for (int i = 0; i < BOX; i++) {
            for (int j = 0; j < BOX; j++) {
                if (g[br + i][bc + j] == d) return false;
            }
        }
        return true;
    }

    /** Compta solucions (s'atura en arribar a limit) escollint sempre la casella amb menys candidats. */
    private static int countSolutions(int[][] g, int limit) {
        int bestR = -1, bestC = -1, bestCount = 10;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (g[r][c] != 0) continue;
                int cnt = 0;
                for (int d = 1; d <= 9; d++) if (canPlace(g, r, c, d)) cnt++;
                if (cnt == 0) return 0;
                if (cnt < bestCount) {
                    bestCount = cnt;
                    bestR = r;
                    bestC = c;
                }
            }
        }
        if (bestR == -1) return 1; // graella completa

        int total = 0;
        for (int d = 1; d <= 9 && total < limit; d++) {
            if (!canPlace(g, bestR, bestC, d)) continue;
            g[bestR][bestC] = d;
            total += countSolutions(g, limit - total);
            g[bestR][bestC] = 0;
        }
        return total;
    }
}
