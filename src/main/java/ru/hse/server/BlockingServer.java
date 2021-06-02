package ru.hse.server;

import ru.hse.data.IntArray;
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

public class BlockingServer implements Server {
    private final ExecutorService serverSocketService = Executors.newSingleThreadExecutor();
    private final ExecutorService workersThreadPool;
    private final ConcurrentLinkedQueue<ClientData> clients = new ConcurrentLinkedQueue<>();
    private ServerSocket serverSocket;

    private volatile boolean isWorking;

    public BlockingServer(int numberOfWorkers) {
        workersThreadPool = Executors.newFixedThreadPool(numberOfWorkers);
    }

    @Override
    public void start(int port) throws ServerException {
        try {
            isWorking = true;
            serverSocket = new ServerSocket(port);
            serverSocketService.submit(() -> acceptClients(serverSocket));
        } catch (IOException ex) {
            throw new ServerException(ex);
        }
    }

    @Override
    public void shutdown() throws ServerException {
        isWorking = false;
        serverSocketService.shutdown();
        workersThreadPool.shutdown();
        try {
            serverSocket.close();
        } catch (IOException ex) {
            throw new ServerException(ex);
        }
        clients.forEach(ClientData::close);
    }

    private void acceptClients(ServerSocket serverSocket) {
        try (ServerSocket ignored = serverSocket) {
            while (isWorking) {
                Socket socket = serverSocket.accept();
                ClientData clientData = new ClientData(socket);
                clients.add(clientData);
                clientData.processClient();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
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
                        workersThreadPool.submit(() -> {
                            IntArraysUtils.sort(array.getData());
                            sendResponse(array);
                        });
                    }
                } catch (IOException ignored) {
                }
            });
        }

        public void sendResponse(IntArray array) {
            responseWriter.submit(() -> {
                try {
                    ProtoUtils.writeArray(outputStream, array);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        }

        public void close() {
            responseWriter.shutdown();
            requestReader.shutdown();
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
