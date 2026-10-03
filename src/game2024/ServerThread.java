package game2024;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ServerThread extends Thread {
    private static final String POISON = "\u0000";

    private final Socket connSocket;
    private final DataOutputStream outToClient;
    private final BufferedReader inFromClient;
    private final BlockingQueue<String> outbox = new LinkedBlockingQueue<>();
    private final Thread writer;

    public ServerThread(Socket connSocket, BufferedReader inFromClient) throws IOException {
        this.connSocket = connSocket;
        this.inFromClient = inFromClient;
        this.outToClient = new DataOutputStream(new BufferedOutputStream(connSocket.getOutputStream()));
        this.writer = new Thread(this::drainOutbox, "writer-" + connSocket.getRemoteSocketAddress());
        this.writer.setDaemon(true);
    }

    @Override
    public void run() {
        writer.start();
        try {
            String sentence;
            while ((sentence = inFromClient.readLine()) != null) {
                Server.broadcast(sentence);
            }
        } catch (IOException e) {
            System.out.println("Forbindelse tabt: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    public void send(String command) {
        outbox.offer(command);
    }

    private void drainOutbox() {
        try {
            String command;
            while (!(command = outbox.take()).equals(POISON)) {
                outToClient.writeBytes(command + "\n");
                if (outbox.isEmpty()) {
                    outToClient.flush();
                }
            }
        } catch (IOException | InterruptedException e) {
            disconnect();
        }
    }

    private void disconnect() {
        if (Server.removeClient(this)) {
            outbox.offer(POISON);
            try {
                connSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
