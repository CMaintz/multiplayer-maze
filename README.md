# Multiplayer Maze

A small networked maze game in Java 17 + JavaFX, built as coursework for *Distribuerede Informationssystemer* (3rd semester, Datamatiker, autumn 2024). The goal of the assignment was to practise sockets, threads and synchronisation.

Players move around a shared maze and shoot each other; every client renders the same game state.

## Architecture

- **`Server`** accepts TCP connections on port 6789 and starts one `ServerThread` per client.
- **`ServerThread`** reads commands from its client and hands them to `Server.broadcast`, which relays each command to every connected client. Clients apply commands locally (`MOVE`, `CONNECT`, `DISCONNECT`, `PEWPEW`, …).
- **`GUI` / `App`** is the JavaFX client.

### Concurrency

- The client list is a `CopyOnWriteArrayList`; additions, removals and broadcasts are serialised on the `Server` class lock, so every client receives messages in the same order.
- `broadcast` only *enqueues* each message. Each client has its own outbound queue drained by a writer thread, so one slow or stalled client cannot block the others.
- A disconnect (EOF, read error or write error) closes the socket and removes the client exactly once.

## Running

Open in IntelliJ (JavaFX SDK required), run `game2024.Server`, then start one `game2024.App` per player and enter the server's IP.

## Scoring

+1 per move, −1 for walking into a wall, +10 for hitting another player, −10 for being hit.
