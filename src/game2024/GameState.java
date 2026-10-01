package game2024;

import java.util.Map;

/**
 * The game rules, free of JavaFX so they can be tested headless.
 * Every client runs its own copy and applies the same broadcast commands in the same order.
 */
public class GameState {
    public static final String[] BOARD = {    // 20x20
            "wwwwwwwwwwwwwwwwwwww",
            "w        ww        w",
            "w w  w  www w  w  ww",
            "w w  w   ww w  w  ww",
            "w  w               w",
            "w w w w w w w  w  ww",
            "w w     www w  w  ww",
            "w w     w w w  w  ww",
            "w   w w  w  w  w   w",
            "w     w  w  w  w   w",
            "w ww ww        w  ww",
            "w  w w    w    w  ww",
            "w        ww w  w  ww",
            "w         w w  w  ww",
            "w        w     w  ww",
            "w  w              ww",
            "w  w www  w w  ww ww",
            "w w      ww w     ww",
            "w   w   ww  w      w",
            "wwwwwwwwwwwwwwwwwwww"
    };

    static final String[] SPAWN_POINTS = {"1 1", "17 1", "4 14", "16 17", "11 10", "5 7"};

    public enum MoveResult { MOVED, HIT_WALL, BUMPED_PLAYER }

    public interface ShotListener {
        void beam(int x, int y, boolean start, boolean end);

        void killed(Player shooter, Player victim);
    }

    private final Map<String, Player> players;

    public GameState(Map<String, Player> players) {
        this.players = players;
    }

    public boolean isWall(int x, int y) {
        return BOARD[y].charAt(x) == 'w';
    }

    public Player playerAt(int x, int y) {
        for (Player p : players.values()) {
            if (p.getXpos() == x && p.getYpos() == y) {
                return p;
            }
        }
        return null;
    }

    public MoveResult move(Player player, int deltaX, int deltaY, String direction) {
        player.setDirection(direction);
        int x = player.getXpos() + deltaX, y = player.getYpos() + deltaY;

        if (isWall(x, y)) {
            player.addPoints(-1);
            return MoveResult.HIT_WALL;
        }
        Player other = playerAt(x, y);
        if (other != null) {
            player.addPoints(10);
            other.addPoints(-10);
            return MoveResult.BUMPED_PLAYER;
        }
        player.addPoints(1);
        player.setXpos(x);
        player.setYpos(y);
        return MoveResult.MOVED;
    }

    /** Returns "x y". Known players hash their current position, new players hash their name. */
    public String spawnPointFor(String name) {
        Player player = players.get(name);
        int hashValue = player != null
                ? (player.getXpos() + ":" + player.getYpos()).hashCode()
                : name.hashCode();

        int spawnIndex = Math.abs(hashValue) % SPAWN_POINTS.length;
        String coordinates = SPAWN_POINTS[spawnIndex];
        while (playerAt(x(coordinates), y(coordinates)) != null) {
            spawnIndex = (spawnIndex + 1) % SPAWN_POINTS.length;
            coordinates = SPAWN_POINTS[spawnIndex];
        }
        return coordinates;
    }

    public void respawn(Player player) {
        String coordinates = spawnPointFor(player.getName());
        player.setXpos(x(coordinates));
        player.setYpos(y(coordinates));
    }

    // Hits are resolved cell by cell, so a victim respawned further along the beam can be hit again.
    public void shoot(Player shooter, int deltaX, int deltaY, ShotListener listener) {
        int x = shooter.getXpos() + deltaX, y = shooter.getYpos() + deltaY;
        listener.beam(x, y, true, false);

        while (!isWall(x + deltaX, y + deltaY)) {
            hitAt(shooter, x, y, listener);
            x += deltaX;
            y += deltaY;
            listener.beam(x, y, false, false);
        }
        hitAt(shooter, x, y, listener);
        listener.beam(x, y, false, true);
    }

    private void hitAt(Player shooter, int x, int y, ShotListener listener) {
        Player victim = playerAt(x, y);
        if (victim != null) {
            respawn(victim);
            victim.setPoint(victim.getPoint() - 50);
            shooter.setPoint(shooter.getPoint() + 50);
            listener.killed(shooter, victim);
        }
    }

    private static int x(String coordinates) {
        return Integer.parseInt(coordinates.split(" ")[0]);
    }

    private static int y(String coordinates) {
        return Integer.parseInt(coordinates.split(" ")[1]);
    }
}
