package game2024;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {
    private static final List<ServerThread> threads = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        int port = 6789;

        try (ServerSocket welcomeSocket = new ServerSocket(port)) {
            System.out.println("Venter på klient...");
            System.out.println("Lytter på port " + port);
            serve(welcomeSocket);
        }
    }

    static void serve(ServerSocket welcomeSocket) throws IOException {
        while (true) {
            Socket connectionSocket = welcomeSocket.accept();
            System.out.println("Three-way handshake completed.");

            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
            String connectionInfo = inFromClient.readLine();
            if (connectionInfo == null) {
                connectionSocket.close();
                continue;
            }

            ServerThread serverThread = new ServerThread(connectionSocket, inFromClient);
            int playerCount;
            synchronized (Server.class) {
                threads.add(serverThread);
                playerCount = threads.size();
            }
            serverThread.start();

            broadcast(connectionInfo + " " + playerCount);
            System.out.println("Ny klient forbundet.");
        }
    }

    static int clientCount() {
        return threads.size();
    }

    // Holding the lock while enqueueing gives every client the same message order;
    // the actual socket writes happen on each client's own writer thread.
    public static synchronized void broadcast(String command) {
        for (ServerThread st : threads) {
            st.send(command);
        }
    }

    public static synchronized boolean removeClient(ServerThread thread) {
        return threads.remove(thread);
    }
}
