package game2024;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class GUI extends Application {
    private static String serverIP = "localhost";
    private static Socket clientSocket;
    private static BufferedReader inFromServer;
    private static DataOutputStream outToServer;

    public static final int size = 20;
    public static final int scene_height = size * 20 + 100;
    public static final int scene_width = size * 20 + 200;

    public static Image image_floor;
    public static Image image_wall;
    public static Image hero_right, hero_left, hero_up, hero_down;
    public static Image lazer_right, lazer_left, lazer_up, lazer_down;
    public static Image lazer_horizontal, lazer_vertical;
    public static Image wall_hit_north, wall_hit_south, wall_hit_east, wall_hit_west;

    private static String myName;
    public static Player me;
    public static Map<String, Player> playerMap = new HashMap<>();
    private int connectedClients = -1;
    private boolean isFrozen = false;
    private Label[][] fields;
    private TextArea scoreList;
    private final GameState state = new GameState(playerMap);


    // -------------------------------------------
    // | Maze: (0,0)              | Score: (1,0) |
    // |-----------------------------------------|
    // | boardGrid (0,1)          | scorelist    |
    // |                          | (1,1)        |
    // -------------------------------------------

    @Override
    public void start(Stage primaryStage) {
        try {
            clientSocket = new Socket(serverIP, 6789);
            outToServer = new DataOutputStream(clientSocket.getOutputStream());
            inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            loadImages();

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(0, 10, 0, 10));

            Text mazeLabel = new Text("Maze:");
            mazeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));

            Text scoreLabel = new Text("Score:");
            scoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));

            scoreList = new TextArea();

            GridPane boardGrid = new GridPane();

            fields = new Label[20][20];
            for (int j = 0; j < 20; j++) {
                for (int i = 0; i < 20; i++) {
                    switch (GameState.BOARD[j].charAt(i)) {
                        case 'w':
                            fields[i][j] = new Label("", new ImageView(image_wall));
                            break;
                        case ' ':
                            fields[i][j] = new Label("", new ImageView(image_floor));
                            break;
                        default:
                            throw new Exception("Illegal field value: " + GameState.BOARD[j].charAt(i));
                    }
                    boardGrid.add(fields[i][j], i, j);
                }
            }
            scoreList.setEditable(false);

            grid.add(mazeLabel, 0, 0);
            grid.add(scoreLabel, 1, 0);
            grid.add(boardGrid, 0, 1);
            grid.add(scoreList, 1, 1);

            Scene scene = new Scene(grid, scene_width, scene_height);
            primaryStage.setScene(scene);
            primaryStage.show();

            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                try {
                    switch (event.getCode()) {
                        case SPACE:
                            sendPewpewCommand();
                            break;
                        case UP:
                            sendMoveCommand("0 -1 up");
                            break;
                        case DOWN:
                            sendMoveCommand("0 +1 down");
                            break;
                        case LEFT:
                            sendMoveCommand("-1 0 left");
                            break;
                        case RIGHT:
                            sendMoveCommand("+1 0 right");
                            break;
                        default:
                            break;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            primaryStage.setOnCloseRequest(event -> closeClient());
            GuiThread gt = new GuiThread();
            gt.start();
            sendConnectCommand();
            registerName();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public String getScoreList() {
        StringBuffer b = new StringBuffer(100);
        for (Player p : playerMap.values()) {
            b.append(p + "\r\n");
        }
        return b.toString();
    }

    public void playerMoved(int delta_x, int delta_y, String direction, Player player) {
        int x = player.getXpos(), y = player.getYpos();
        if (state.move(player, delta_x, delta_y, direction) == GameState.MoveResult.MOVED) {
            resetFloor(x, y);
        }
        renderPlayer(player.getXpos(), player.getYpos(), direction);
        System.out.println("X-pos:" + player.getXpos() + " Y-pos: " + player.getYpos());
        scoreList.setText(getScoreList());
    }

    public void renderPlayer(int x, int y, String direction) {
        if (direction.equals("right")) {
            fields[x][y].setGraphic(new ImageView(hero_right));
        }
        ;
        if (direction.equals("left")) {
            fields[x][y].setGraphic(new ImageView(hero_left));
        }
        ;
        if (direction.equals("up")) {
            fields[x][y].setGraphic(new ImageView(hero_up));
        }
        ;
        if (direction.equals("down")) {
            fields[x][y].setGraphic(new ImageView(hero_down));
        }
        ;

    }


    private void resetFloor(int x, int y) {
        fields[x][y].setGraphic(new ImageView(image_floor));
    }

    private void pewPew(String name, int delta_x, int delta_y, String direction) {
        state.shoot(playerMap.get(name), delta_x, delta_y, new GameState.ShotListener() {
            @Override
            public void beam(int x, int y, boolean start, boolean end) {
                renderLazer(x, y, direction, start, end);
            }

            @Override
            public void killed(Player shooter, Player victim) {
                renderPlayer(victim.getXpos(), victim.getYpos(), "up");
                scoreList.setText(getScoreList());
            }
        });
    }

    private void renderLazer(int x, int y, String direction, boolean start, boolean end) {
        if (direction.equals("right")) {
            if (start) {
                fields[x][y].setGraphic(new ImageView(lazer_right));
            } else if (end) {
                fields[x][y].setGraphic(new ImageView(wall_hit_east));
            } else {
                fields[x][y].setGraphic(new ImageView(lazer_horizontal));
            }
        } else if (direction.equals("left")) {
            if (start) {
                fields[x][y].setGraphic(new ImageView(lazer_left));
            } else if (end) {
                fields[x][y].setGraphic(new ImageView(wall_hit_west));
            } else {
                fields[x][y].setGraphic(new ImageView(lazer_horizontal));
            }
        } else if (direction.equals("up")) {
            if (start) {
                fields[x][y].setGraphic(new ImageView(lazer_up));
            } else if (end) {
                fields[x][y].setGraphic(new ImageView(wall_hit_north));
            } else {
                fields[x][y].setGraphic(new ImageView(lazer_vertical));
            }
        } else if (direction.equals("down")) {
            if (start) {
                fields[x][y].setGraphic(new ImageView(lazer_down));
            } else if (end) {
                fields[x][y].setGraphic(new ImageView(wall_hit_south));
            } else {
                fields[x][y].setGraphic(new ImageView(lazer_vertical));
            }
        }

        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(event -> {
            resetFloor(x, y);
            isFrozen = false;
        });
        pause.play();
    }

    public void closeClient() {
        try {
            sendDisconnectCommand();
            if (outToServer != null) {
                outToServer.close(); // Luk DataOutputStream
            }
            if (inFromServer != null) {
                inFromServer.close(); // Luk BufferedReader
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close(); // Luk selve socket-forbindelsen
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void registerName() throws IOException {
        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Indtast dit navn");
        nameDialog.setHeaderText("Velkommen til spillet!");
        nameDialog.setContentText("Indtast dit navn:");

        while (myName == null) {
            Optional<String> result = nameDialog.showAndWait();
            if (result.isPresent()) {
                String enteredName = result.get().trim();
                if (playerMap.containsKey(enteredName)) {
                    showAlert("Navnet er allerede brugt", "Hov hov, du! Navnet er allerede brugt!\nPrøv venligst et andet navn.");
                } else {
                    myName = enteredName;
                    if ((connectedClients - 1) == playerMap.size() && me == null) {
                        System.out.println("i registerName if");
                        Platform.runLater(() -> createAndRegisterSelf());
                        System.out.println("Efter en run-later i registerName");
                    }
                }
//            } else {
//                nameDialog = new TextInputDialog();
//                nameDialog.setTitle("Fy! Indtast dit navn");
//                nameDialog.setHeaderText("Hov hov, du. Der kan kun være én spiller med et givent navn");
//                nameDialog.setContentText("FEJL: Tidligere indtastet navn var allerede taget.\n Venligst indtast et andet navn:");
//            }
//                if (playerMap.get(result) == null) {
//                }
                //TODO: evt en IP dialog? HMM

                //TODO: evt. send en besked til serveren, som bare er "CONNECT", som gør at alle sender deres info til én.
                // Så når man har fået det antal spillere der er i threadsize-1, så kan man oprette sin egen spiller, i tilfælde
                // af at navnet ikke er lig med navnet på en eksisterende spiller..? Og ellers, så laver den navnet om til navn + i++
//Alternativt kan der være en navneliste på serveren, som sendes til at starte med, og så tjekker man om det indtastede navn matcher.
//Hvis det matcher, så får du en fejlbesked i dialogen, som siger navnet allerede er taget.
//Det betyder selvfølgelig at den første besked der kommer ind til serveren, ikke er en connect besked, men en...
//NewConnection besked? Som gør, at alle spillernavne sendes til klienten. Dvs, socket halløj o.l. skal ud
            }
        }
    }

    public void createAndRegisterSelf() {
        if (me == null) {
            String[] spawnCoordinates = state.spawnPointFor(myName).split(" ");
            int spawnX = Integer.parseInt(spawnCoordinates[0]);
            int spawnY = Integer.parseInt(spawnCoordinates[1]);
            // Opretter egen spiller
            createPlayer(myName, spawnX, spawnY, "up", 0);
        }
    }

    public void createPlayer(String name, int xpos, int ypos, String direction, int points) {
        if (playerMap.get(name) == null) {
            if (state.playerAt(xpos, ypos) == null) {
                Player newPlayer = new Player(name, xpos, ypos, direction);
                playerMap.put(name, newPlayer);

                renderPlayer(xpos, ypos, direction);

                newPlayer.setPoint(points);
                scoreList.setText(getScoreList());

                if (name.equalsIgnoreCase(myName)) {
                    me = newPlayer;
                    try {
                        sendRegisterCommand();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void removePlayer(String name) {
        Player removedPlayer = playerMap.remove(name);
        int x = removedPlayer.getXpos(), y = removedPlayer.getYpos();
        resetFloor(x, y);
        removedPlayer = null;
        scoreList.setText(getScoreList());
    }

    private void sendPewpewCommand() throws IOException {
        String direction = me.getDirection();
        int delta_X = 0, delta_Y = 0;
        int x_coord = me.getXpos(), y_coord = me.getYpos();
        if (direction.equalsIgnoreCase("up")) {
            delta_Y = -1;
        } else if (direction.equalsIgnoreCase("down")) {
            delta_Y = 1;
        } else if (direction.equalsIgnoreCase("left")) {
            delta_X = -1;
        } else if (direction.equalsIgnoreCase("right")) {
            delta_X = 1;
        }
        if (!state.isWall(x_coord + delta_X, y_coord + delta_Y) && !state.isWall(x_coord + (2 * delta_X), y_coord + (2 * delta_Y))) {
            isFrozen = true;
            outToServer.writeBytes("PEWPEW " + myName + " " +
                    delta_X + " " + delta_Y + " " + me.getDirection() + "\n");
        }
    }

    public void sendConnectCommand() throws IOException {
        outToServer.writeBytes("CONNECT\n");
    }

    public void sendDisconnectCommand() throws IOException {
        outToServer.writeBytes("DISCONNECT " + myName + "\n");
    }

    public void sendMoveCommand(String input) throws IOException {
        if (!isFrozen) {
            outToServer.writeBytes("MOVE " + me.getName() + " " + input + "\n");
        }
    }

    // sendPlayerState:
    private void sendRegisterCommand() throws IOException {
        if (me != null) {
            outToServer.writeBytes("REGISTER " + me.getState() + "\n");
        }
    }

    //    GuiThread is a simple message-reader thread.
    private class GuiThread extends Thread {
        private GuiThread() {
        }

        //Runs when the GUI has finished compiling
        @Override
        public void run() {

            String inboundMessage;
            try {
                while (!clientSocket.isClosed() && (inboundMessage = inFromServer.readLine()) != null) {
                    System.out.println("Inbound message: " + inboundMessage);
                    String[] tokens = inboundMessage.split(" ");
                    String command = tokens[0];

                    if (command.equalsIgnoreCase("MOVE")) {
                        handleMoveCommand(tokens);
                    } else if (command.equalsIgnoreCase("REGISTER")) {
                        handleRegisterCommand(tokens);
                    } else if (command.equalsIgnoreCase("CONNECT")) {
                        handleConnectCommand(tokens);
                    } else if (command.equalsIgnoreCase("DISCONNECT")) {
                        handleDisconnectCommand(tokens);
                    } else if (command.equalsIgnoreCase("PEWPEW")) {
                        handlePewpewCommand(tokens);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void handlePewpewCommand(String[] tokens) {
            String name = tokens[1];
            int delta_X = Integer.parseInt(tokens[2]);
            int delta_Y = Integer.parseInt(tokens[3]);
            String direction = tokens[4];
            Platform.runLater(() -> pewPew(name, delta_X, delta_Y, direction));
        }

        private void handleMoveCommand(String[] tokens) {
            String name = tokens[1];
            int delta_x = Integer.parseInt(tokens[2]);
            int delta_y = Integer.parseInt(tokens[3]);
            String direction = tokens[4];
            Platform.runLater(() -> playerMoved(delta_x, delta_y, direction, playerMap.get(name)));
        }

        private void handleRegisterCommand(String[] tokens) {
            String name = tokens[1];
            if (!name.equalsIgnoreCase(myName)) {
                int x_coord = Integer.parseInt(tokens[2]);
                int y_coord = Integer.parseInt(tokens[3]);
                String direction = tokens[4];
                int points = Integer.parseInt(tokens[5]);
                Platform.runLater(() -> createPlayer(name, x_coord, y_coord, direction, points));
            }
        }

        private void handleConnectCommand(String[] tokens) throws IOException {
            int clientCount = Integer.parseInt(tokens[1]);
            connectedClients = clientCount;
            sendRegisterCommand();
        }

        private void handleDisconnectCommand(String[] tokens) {
            String name = tokens[1];
            if (playerMap.containsKey(name)) {
                Platform.runLater(() -> removePlayer(name));
            }
        }


    }

    private void loadImages() {
        image_wall = new Image(getClass().getResourceAsStream("Image/wall4.png"), size, size, false, false);
        image_floor = new Image(getClass().getResourceAsStream("Image/floor1.png"), size, size, false, false);

        hero_right = new Image(getClass().getResourceAsStream("Image/heroRight.png"), size, size, false, false);
        hero_left = new Image(getClass().getResourceAsStream("Image/heroLeft.png"), size, size, false, false);
        hero_up = new Image(getClass().getResourceAsStream("Image/heroUp.png"), size, size, false, false);
        hero_down = new Image(getClass().getResourceAsStream("Image/heroDown.png"), size, size, false, false);

        lazer_right = new Image(getClass().getResourceAsStream("Image/fireRight.png"), size, size, false, false);
        lazer_left = new Image(getClass().getResourceAsStream("Image/fireLeft.png"), size, size, false, false);
        lazer_up = new Image(getClass().getResourceAsStream("Image/fireUp.png"), size, size, false, false);
        lazer_down = new Image(getClass().getResourceAsStream("Image/fireDown.png"), size, size, false, false);

        lazer_horizontal = new Image(getClass().getResourceAsStream("Image/fireHorizontal.png"), size, size, false, false);
        lazer_vertical = new Image(getClass().getResourceAsStream("Image/fireVertical.png"), size, size, false, false);

        wall_hit_north = new Image(getClass().getResourceAsStream("Image/fireWallNorth.png"), size, size, false, false);
        wall_hit_south = new Image(getClass().getResourceAsStream("Image/fireWallSouth.png"), size, size, false, false);
        wall_hit_east = new Image(getClass().getResourceAsStream("Image/fireWallEast.png"), size, size, false, false);
        wall_hit_west = new Image(getClass().getResourceAsStream("Image/fireWallWest.png"), size, size, false, false);
    }
}


