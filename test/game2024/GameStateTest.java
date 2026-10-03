package game2024;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {
    private Map<String, Player> players;
    private GameState state;

    @BeforeEach
    void setUp() {
        players = new HashMap<>();
        state = new GameState(players);
    }

    private Player add(String name, int x, int y, String direction) {
        Player p = new Player(name, x, y, direction);
        players.put(name, p);
        return p;
    }

    @Test
    void boardIs20x20AndWalledIn() {
        assertEquals(20, GameState.BOARD.length);
        for (int i = 0; i < 20; i++) {
            assertEquals(20, GameState.BOARD[i].length());
            assertTrue(state.isWall(i, 0));
            assertTrue(state.isWall(i, 19));
            assertTrue(state.isWall(0, i));
            assertTrue(state.isWall(19, i));
        }
        assertFalse(state.isWall(1, 1));
        assertTrue(state.isWall(9, 1));
    }

    @Test
    void spawnPointsAreFloor() {
        for (String spawn : GameState.SPAWN_POINTS) {
            String[] xy = spawn.split(" ");
            assertFalse(state.isWall(Integer.parseInt(xy[0]), Integer.parseInt(xy[1])), spawn);
        }
    }

    @Test
    void moveOntoFloorGivesOnePointAndMoves() {
        Player p = add("a", 1, 1, "up");

        assertEquals(GameState.MoveResult.MOVED, state.move(p, 1, 0, "right"));

        assertEquals(2, p.getXpos());
        assertEquals(1, p.getYpos());
        assertEquals(1, p.getPoint());
        assertEquals("right", p.getDirection());
    }

    @Test
    void walkingIntoWallCostsOnePointAndOnlyTurns() {
        Player p = add("a", 1, 1, "up");

        assertEquals(GameState.MoveResult.HIT_WALL, state.move(p, -1, 0, "left"));

        assertEquals(1, p.getXpos());
        assertEquals(1, p.getYpos());
        assertEquals(-1, p.getPoint());
        assertEquals("left", p.getDirection());
    }

    @Test
    void bumpingIntoPlayerScoresTenAndNobodyMoves() {
        Player a = add("a", 1, 1, "up");
        Player b = add("b", 2, 1, "up");

        assertEquals(GameState.MoveResult.BUMPED_PLAYER, state.move(a, 1, 0, "right"));

        assertEquals(10, a.getPoint());
        assertEquals(-10, b.getPoint());
        assertEquals(1, a.getXpos());
        assertEquals(2, b.getXpos());
    }

    @Test
    void scoresAccumulateAndCanGoNegative() {
        Player p = add("a", 1, 1, "up");
        state.move(p, 0, -1, "up");
        state.move(p, 0, -1, "up");
        state.move(p, 1, 0, "right");

        assertEquals(-1, p.getPoint());
    }

    @Test
    void playerAtFindsByPosition() {
        Player a = add("a", 3, 4, "up");

        assertSame(a, state.playerAt(3, 4));
        assertNull(state.playerAt(4, 3));
    }

    @Test
    void newPlayerSpawnsByNameHashAndSkipsOccupiedPoints() {
        String name = nameWithSpawnIndex(0);
        assertEquals("1 1", state.spawnPointFor(name));

        add("blocker", 1, 1, "up");
        assertEquals("17 1", state.spawnPointFor(name));
    }

    @Test
    void spawnSearchWrapsAround() {
        String name = nameWithSpawnIndex(GameState.SPAWN_POINTS.length - 1);
        add("blocker", 5, 7, "up");

        assertEquals("1 1", state.spawnPointFor(name));
    }

    @Test
    void laserTravelsUntilTheWall() {
        Player shooter = add("s", 1, 1, "right");
        List<String> beams = new ArrayList<>();

        state.shoot(shooter, 1, 0, recorder(beams, new ArrayList<>()));

        assertEquals(List.of("2,1 start", "3,1", "4,1", "5,1", "6,1", "7,1", "8,1", "8,1 end"), beams);
        assertEquals(0, shooter.getPoint());
    }

    @Test
    void laserHitTransfersFiftyPointsAndRespawnsVictim() {
        Player shooter = add("s", 1, 1, "right");
        Player victim = add("v", 5, 1, "left");
        List<String> kills = new ArrayList<>();

        state.shoot(shooter, 1, 0, recorder(new ArrayList<>(), kills));

        assertEquals(List.of("s>v"), kills);
        assertEquals(50, shooter.getPoint());
        assertEquals(-50, victim.getPoint());
        assertTrue(List.of(GameState.SPAWN_POINTS).contains(victim.getXpos() + " " + victim.getYpos()));
        assertFalse(victim.getXpos() == 5 && victim.getYpos() == 1);
        assertEquals(1, shooter.getXpos());
    }

    @Test
    void laserHitsPlayerStandingNextToTheWall() {
        Player shooter = add("s", 1, 1, "right");
        Player victim = add("v", 8, 1, "left");

        state.shoot(shooter, 1, 0, recorder(new ArrayList<>(), new ArrayList<>()));

        assertEquals(-50, victim.getPoint());
    }

    @Test
    void laserHitsEveryPlayerInLine() {
        Player shooter = add("s", 1, 1, "right");
        Player v1 = add("v1", 3, 1, "up");
        Player v2 = add("v2", 6, 1, "up");

        state.shoot(shooter, 1, 0, recorder(new ArrayList<>(), new ArrayList<>()));

        assertEquals(100, shooter.getPoint());
        assertEquals(-50, v1.getPoint());
        assertEquals(-50, v2.getPoint());
    }

    @Test
    void stateLineMatchesRegisterProtocol() {
        Player p = new Player("bob", 4, 14, "down");
        p.addPoints(7);

        assertEquals("bob 4 14 down 7", p.getState());
        String[] tokens = ("REGISTER " + p.getState()).split(" ");
        assertEquals(6, tokens.length);
        assertEquals("bob", tokens[1]);
        assertEquals(7, Integer.parseInt(tokens[5]));
    }

    private static String nameWithSpawnIndex(int index) {
        for (int i = 0; ; i++) {
            String candidate = "player" + i;
            if (Math.abs(candidate.hashCode()) % GameState.SPAWN_POINTS.length == index) {
                return candidate;
            }
        }
    }

    private static GameState.ShotListener recorder(List<String> beams, List<String> kills) {
        return new GameState.ShotListener() {
            @Override
            public void beam(int x, int y, boolean start, boolean end) {
                beams.add(x + "," + y + (start ? " start" : "") + (end ? " end" : ""));
            }

            @Override
            public void killed(Player shooter, Player victim) {
                kills.add(shooter.getName() + ">" + victim.getName());
            }
        };
    }
}
