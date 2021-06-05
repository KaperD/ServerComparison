package ru.hse.server;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.hse.data.IntArray;
import ru.hse.statistics.Statistics;
import ru.hse.utils.IntArraysUtils;
import ru.hse.utils.ProtoUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NonBlockingServer extends Server {
    private ExecutorService workersThreadPool;
    private final ExecutorService serverSocketChanelService = Executors.newSingleThreadExecutor();
    private volatile boolean isWorking;

    private Selector readSelector;
    private final ExecutorService requestReader = Executors.newSingleThreadExecutor();
    private final Queue<ClientData> readQueue = new ConcurrentLinkedQueue<>();

    private Selector writeSelector;
    private final ExecutorService responseWriter = Executors.newSingleThreadExecutor();
    private final Queue<ClientData> writeQueue = new ConcurrentLinkedQueue<>();

    private ServerSocketChannel serverSocketChannel;

    public NonBlockingServer(Statistics statistics) {
        super(statistics);
    }

    @Override
    public void start(int port, int numberOfWorkers) throws ServerException {
        isWorking = true;
        workersThreadPool = Executors.newFixedThreadPool(numberOfWorkers);
        try {
            writeSelector = Selector.open();
            readSelector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChanelService.submit(() -> acceptClients(serverSocketChannel));
            requestReader.submit(() -> {
                try {
                    readClientsRequests();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            responseWriter.submit(() -> {
                try {
                    writeClientsResponse();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException ex) {
            throw new ServerException(ex);
        }
    }

    @Override
    public void shutdown() throws ServerException {
        isWorking = false;
        workersThreadPool.shutdown();
        serverSocketChanelService.shutdown();
        requestReader.shutdown();
        responseWriter.shutdown();
        try {
            serverSocketChannel.close();
            readSelector.close();
            writeSelector.close();
        } catch (IOException ex) {
            throw new ServerException(ex);
        }
    }

    private void acceptClients(ServerSocketChannel serverSocket) {
        try (ServerSocketChannel ignored = serverSocket) {
            while (isWorking) {
                SocketChannel socketChannel = serverSocket.accept();
                socketChannel.configureBlocking(false);
                ClientData clientData = new ClientData(socketChannel);
                readQueue.add(clientData);
                readSelector.wakeup();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void readClientsRequests() throws IOException {
        while (isWorking) {
            int n = readSelector.select();
            if (n > 0) {
                Set<SelectionKey> readySet = readSelector.selectedKeys();
                Iterator<SelectionKey> iterator = readySet.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    ClientData clientData = (ClientData) key.attachment();
                    SocketChannel channel = clientData.channel;
                    int len;
                    if (clientData.isReadingSize) {
                        len = channel.read(clientData.messageSizeBuffer);
                        if (!clientData.messageSizeBuffer.hasRemaining()) {
                            clientData.isReadingSize = false;
                            clientData.messageSizeBuffer.flip();
                            int size = clientData.messageSizeBuffer.getInt();
                            clientData.messageBuffer = ByteBuffer.allocate(size);
                            clientData.messageSizeBuffer.clear();
                        }
                    } else {
                        len = channel.read(clientData.messageBuffer);
                        if (!clientData.messageBuffer.hasRemaining()) {
                            clientData.messageBuffer.flip();
                            workersThreadPool.submit(new Task(clientData.messageBuffer, clientData));
                            clientData.isReadingSize = true;
                            clientData.messageBuffer = null;
                        }
                    }
                    if (len < 0) {
                        key.cancel();
                    }
                    iterator.remove();
                }
            }
            while (!readQueue.isEmpty()) {
                ClientData clientData = readQueue.remove();
                clientData.channel.register(readSelector, SelectionKey.OP_READ, clientData);
            }
        }
    }

    private void writeClientsResponse() throws IOException {
        while (isWorking) {
            int n = writeSelector.select();
            while (!writeQueue.isEmpty()) {
                ClientData clientData = writeQueue.remove();
                try {
                    clientData.channel.register(writeSelector, SelectionKey.OP_WRITE, clientData);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    throw new RuntimeException(ex);
                }
            }
            if (n > 0) {
                Set<SelectionKey> readySet = writeSelector.selectedKeys();
                Iterator<SelectionKey> iterator = readySet.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    ClientData clientData = (ClientData) key.attachment();
                    SocketChannel channel = clientData.channel;

                    ByteBuffer buffer = clientData.getCurrentOutput();
                    if (channel.write(buffer) < 0) {
                        throw new RuntimeException("<0");
                    }
                    if (!buffer.hasRemaining()) {
                        clientData.currentBuffer = null;
                        if (clientData.numberOfUnfinishedOutputs.decrementAndGet() == 0) {
                            key.cancel();
                        }
                    }
                    iterator.remove();
                }
            }
        }
    }

    private class Task implements Runnable {
        private final IntArray array;
        private final ClientData clientData;

        public Task(ByteBuffer buffer, ClientData clientData) throws InvalidProtocolBufferException {
            this.array = ProtoUtils.readArray(buffer);
            this.clientData = clientData;
            startMeasure(array.getId());
        }

        @Override
        public void run() {
            IntArraysUtils.sort(array.getData());
            clientData.addOutput(ProtoUtils.serialize(array));
            if (clientData.numberOfUnfinishedOutputs.incrementAndGet() == 1) {
                writeQueue.add(clientData);
                writeSelector.wakeup();
            }
            endMeasure(array.getId());
        }
    }

    private static class ClientData {
        public final AtomicInteger numberOfUnfinishedOutputs = new AtomicInteger(0);
        private final Queue<ByteBuffer> outputs = new ConcurrentLinkedQueue<>();
        public final ByteBuffer messageSizeBuffer = ByteBuffer.allocate(Integer.BYTES);
        public ByteBuffer messageBuffer;
        public boolean isReadingSize = true;
        public final SocketChannel channel;
        private volatile ByteBuffer currentBuffer;


        private ClientData(SocketChannel channel) {
            this.channel = channel;
        }

        public ByteBuffer getNextOutput() {
            currentBuffer = outputs.remove();
            return currentBuffer;
        }

        public ByteBuffer getCurrentOutput() {
            if (currentBuffer != null) {
                return currentBuffer;
            }
            return getNextOutput();
        }

        public void addOutput(ByteBuffer buffer) {
            outputs.add(buffer);
        }

        public void close() {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
