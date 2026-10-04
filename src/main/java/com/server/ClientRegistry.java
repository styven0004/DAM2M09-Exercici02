package com.server;

import org.java_websocket.WebSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre de clients connectats.
 *
 * Manté dos mapes bidireccionals:
 * - WebSocket a nom de jugador
 * - Nom de jugador a WebSocket
 *
 * A diferència de l'exemple original (que assignava noms d'un pool), aquí
 * el nom el tria el jugador a la vista de configuració. Si ja està en ús
 * se li afegeix un sufix numèric: "Albert", "Albert (2)", ...
 */
final class ClientRegistry {

    static final int MAX_NAME_LENGTH = 16;
    static final String DEFAULT_NAME = "Jugador";

    /** Mapa de sockets a noms de jugador. */
    private final Map<WebSocket, String> bySocket = new ConcurrentHashMap<>();

    /** Mapa de noms de jugador a sockets. */
    private final Map<String, WebSocket> byName = new ConcurrentHashMap<>();

    /**
     * Registra un socket amb el nom desitjat (net i únic).
     *
     * @param socket  socket del client
     * @param desired nom que vol el jugador (pot ser buit o null)
     * @return el nom finalment assignat
     */
    synchronized String register(WebSocket socket, String desired) {
        String base = desired == null ? "" : desired.trim();
        if (base.isEmpty()) base = DEFAULT_NAME;
        if (base.length() > MAX_NAME_LENGTH) base = base.substring(0, MAX_NAME_LENGTH).trim();

        String name = base;
        int n = 2;
        while (byName.containsKey(name)) {
            name = base + " (" + n + ")";
            n++;
        }
        bySocket.put(socket, name);
        byName.put(name, socket);
        return name;
    }

    /**
     * Elimina un client del registre.
     *
     * @param socket socket del client a eliminar
     * @return el nom que tenia, o null si no estava registrat
     */
    synchronized String remove(WebSocket socket) {
        String name = bySocket.remove(socket);
        if (name != null) byName.remove(name);
        return name;
    }

    /** Nom associat a un socket, o null si encara no s'ha fet join. */
    String nameBySocket(WebSocket socket) {
        return bySocket.get(socket);
    }

    /** Socket associat a un nom, o null si no existeix. */
    WebSocket socketByName(String name) {
        return byName.get(name);
    }

    /**
     * Neteja el registre per a un socket desconnectat.
     * Equivalent a remove(socket).
     */
    String cleanupDisconnected(WebSocket socket) {
        return remove(socket);
    }

    /** Còpia immutable de l'estat actual del mapa socket a nom. */
    Map<WebSocket, String> snapshot() {
        return Map.copyOf(bySocket);
    }
}
