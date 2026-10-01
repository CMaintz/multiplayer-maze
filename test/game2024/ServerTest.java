package game2024;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ServerTest {
    private ServerSocket serverSocket;
    private Thread acceptor;
    private final List<Client> clients = new ArrayList<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @BeforeEach
    void startServer() throws IOException {
        awaitTrue(() -> Server.clientCount() == 0 && serverThreadsAlive() == 0, "previous test cleaned up");
        serverSocket = new ServerSocket(0);
        acceptor = new Thread(() -> {
            try {
                Server.serve(serverSocket);
            } catch (IOException expectedOnClose) {
            }
        }, "test-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        for (Client c : clients) {
            c.close();
        }
        serverSocket.close();
        acceptor.join(5_000);
        pool.shutdownNow();
        awaitTrue(() -> Server.clientCount() == 0, "all clients removed");
        awaitTrue(() -> serverThreadsAlive() == 0, "no ServerThread or writer thread left running");
    }

    @Test
    void connectBroadcastsPlayerCountToEveryone() throws Exception {
        Client a = connect();
        Client b = connect();

        assertEquals(List.of("CONNECT 1", "CONNECT 2"), a.read(2));
        assertEquals(List.of("CONNECT 2"), b.read(1));
        assertEquals(2, Server.clientCount());
    }

    @Test
    void everyClientSeesTheSameMessageOrder() throws Exception {
        int clientCount = 3, perClient = 300;
        List<Client> joined = connectAndDrain(clientCount);

        List<Future<List<String>>> received = new ArrayList<>();
        for (Client c : joined) {
            received.add(pool.submit(() -> c.read(clientCount * perClient)));
        }
        for (int i = 0; i < clientCount; i++) {
            Client sender = joined.get(i);
            String prefix = "MOVE p" + i + " ";
            pool.submit(() -> {
                for (int j = 0; j < perClient; j++) {
                    sender.send(prefix + j);
                }
            });
        }

        List<String> first = received.get(0).get(30, TimeUnit.SECONDS);
        assertEquals(clientCount * perClient, first.size());
        for (int i = 1; i < clientCount; i++) {
            assertEquals(first, received.get(i).get(30, TimeUnit.SECONDS), "client " + i + " saw a different order");
        }
        for (int i = 0; i < clientCount; i++) {
            String prefix = "MOVE p" + i + " ";
            List<String> fromSender = first.stream().filter(m -> m.startsWith(prefix)).toList();
            for (int j = 0; j < perClient; j++) {
                assertEquals(prefix + j, fromSender.get(j));
            }
        }
    }

    @Test
    void disconnectRemovesClientAndStopsItsThreads() throws Exception {
        List<Client> joined = connectAndDrain(2);
        awaitTrue(() -> serverThreadsAlive() == 4, "reader + writer thread per client");

        joined.get(0).close();
        awaitTrue(() -> Server.clientCount() == 1, "closed client removed");
        awaitTrue(() -> serverThreadsAlive() == 2, "closed client's threads stopped");

        Client remaining = joined.get(1);
        remaining.send("DISCONNECT p1");
        assertEquals(List.of("DISCONNECT p1"), remaining.read(1));
    }

    @Test
    void clientThatClosesBeforeHandshakeIsIgnored() throws Exception {
        Socket early = new Socket("localhost", serverSocket.getLocalPort());
        early.close();

        Client c = connect();
        assertEquals(List.of("CONNECT 1"), c.read(1));
        assertEquals(1, Server.clientCount());
    }

    @Test
    void slowClientDoesNotBlockOthers() throws Exception {
        connect(4 * 1024); // never reads, so its socket buffer fills up
        Client fast = connect();
        assertEquals(List.of("CONNECT 2"), fast.read(1));

        int messages = 2_000;
        String payload = "x".repeat(4 * 1024);
        Future<List<String>> fastReceived = pool.submit(() -> fast.read(messages));

        long start = System.nanoTime();
        for (int i = 0; i < messages; i++) {
            Server.broadcast(i + " " + payload);
        }
        long broadcastMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        List<String> got = fastReceived.get(30, TimeUnit.SECONDS);
        assertEquals(messages, got.size());
        assertEquals((messages - 1) + " " + payload, got.get(messages - 1));
        assertTrue(broadcastMillis < 10_000, "broadcast blocked for " + broadcastMillis + " ms");
        assertEquals(2, Server.clientCount(), "slow client is still connected, just behind");
    }

    private Client connect() throws IOException {
        return connect(-1);
    }

    private Client connect(int receiveBufferSize) throws IOException {
        Socket socket = new Socket();
        if (receiveBufferSize > 0) {
            socket.setReceiveBufferSize(receiveBufferSize);
        }
        socket.connect(new InetSocketAddress("localhost", serverSocket.getLocalPort()));
        Client c = new Client(socket);
        clients.add(c);
        c.send("CONNECT");
        return c;
    }

    private List<Client> connectAndDrain(int count) throws IOException {
        List<Client> joined = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Client c = connect();
            joined.add(c);
            for (Client each : joined) {
                assertEquals(List.of("CONNECT " + i), each.read(1));
            }
        }
        return joined;
    }

    private static long serverThreadsAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && (t instanceof ServerThread || t.getName().startsWith("writer-")))
                .count();
    }

    private static void awaitTrue(BooleanSupplier condition, String what) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for: " + what);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(e);
            }
        }
    }

    private static final class Client implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;

        Client(Socket socket) throws IOException {
            this.socket = socket;
            socket.setSoTimeout(30_000);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        synchronized void send(String line) {
            out.print(line + "\n");
            out.flush();
        }

        List<String> read(int count) throws IOException {
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }
                lines.add(line);
            }
            return lines;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
