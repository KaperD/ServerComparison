package ru.hse.server;

import ru.hse.data.IntArray;
import ru.hse.statistics.Statistics;
import ru.hse.utils.IntArraysUtils;
import ru.hse.utils.ProtoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockingServer extends Server {
    private final ExecutorService clientsAcceptor = Executors.newSingleThreadExecutor();
    private final ConcurrentLinkedQueue<ClientData> clients = new ConcurrentLinkedQueue<>();
    private ExecutorService workersThreadPool;
    private ServerSocket serverSocket;

    private volatile boolean isWorking;

    public BlockingServer(Statistics statistics) {
        super(statistics);
    }

    @Override
    public void start(int port, int numberOfWorkers) throws ServerException {
        workersThreadPool = Executors.newFixedThreadPool(numberOfWorkers);
        try {
            isWorking = true;
            serverSocket = new ServerSocket(port);
            clientsAcceptor.submit(() -> acceptClients(serverSocket));
        } catch (IOException ex) {
            throw new ServerException(ex);
        }
    }

    @Override
    public void shutdown() throws ServerException {
        isWorking = false;
        clientsAcceptor.shutdown();
        workersThreadPool.shutdown();
        clients.forEach(ClientData::close);
        try {
            serverSocket.close();
        } catch (IOException ex) {
            throw new ServerException(ex);
        }
    }

    private void acceptClients(ServerSocket serverSocket) {
        try (ServerSocket ignored = serverSocket) {
            while (isWorking) {
                Socket socket = serverSocket.accept();
                ClientData clientData = new ClientData(socket);
                clients.add(clientData);
                clientData.processClient();
            }
        } catch (IOException ignored) {
        }
    }

    private class ClientData {
        private final Socket socket;
        private final ExecutorService responseWriter = Executors.newSingleThreadExecutor();
        private final ExecutorService requestReader = Executors.newSingleThreadExecutor();

        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ClientData(Socket socket) throws IOException {
            this.socket = socket;
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        }

        public void processClient() {
            requestReader.submit(() -> {
                try (Socket ignored = socket) {
                    while (isWorking && socket.isConnected()) {
                        IntArray array = ProtoUtils.readArray(inputStream);
                        final int id = array.getId();
                        startMeasure(id);
                        workersThreadPool.submit(() -> {
                            IntArraysUtils.sort(array.getData());
                            sendResponse(array);
                            endMeasure(id);
                        });
                    }
                } catch (IOException ignored) {
                } finally {
                    close();
                }
            });
        }

        public void sendResponse(IntArray array) {
            responseWriter.submit(() -> {
                try {
                    ProtoUtils.writeArray(outputStream, array);
                } catch (IOException ignored) {
                }
            });
        }

        public void close() {
            if (!responseWriter.isShutdown()) {
                responseWriter.shutdown();
            }
            if (!requestReader.isShutdown()) {
                requestReader.shutdown();
            }
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
