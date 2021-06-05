package ru.hse.server;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.hse.data.IntArray;
import ru.hse.statistics.Statistics;
import ru.hse.utils.IntArraysUtils;
import ru.hse.utils.ProtoUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AsynchronousServer extends Server {
    private ExecutorService workersThreadPool;
    private AsynchronousServerSocketChannel serverSocketChannel;

    public AsynchronousServer(Statistics statistics) {
        super(statistics);
    }

    @Override
    public void start(int port, int numberOfWorkers) throws ServerException {
        workersThreadPool = Executors.newFixedThreadPool(numberOfWorkers);
        try {
            serverSocketChannel = AsynchronousServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.accept(serverSocketChannel, new AcceptHandler());
        } catch (IOException ex) {
            throw new ServerException(ex);
        }
    }

    @Override
    public void shutdown() throws ServerException {
        try {
            workersThreadPool.shutdown();
            serverSocketChannel.close();
        } catch (IOException ex) {
            throw new ServerException(ex);
        }
    }

    private class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {
        private final CompletionHandler<Integer, ClientData> readHandler = new ReadHandler();

        @Override
        public void completed(AsynchronousSocketChannel asynchronousSocketChannel, AsynchronousServerSocketChannel serverSocketChannel) {
            serverSocketChannel.accept(serverSocketChannel, this);
            ClientData clientData = new ClientData(asynchronousSocketChannel);
            asynchronousSocketChannel.read(clientData.messageSizeBuffer, clientData, readHandler);
        }

        @Override
        public void failed(Throwable throwable, AsynchronousServerSocketChannel unused) {
            System.out.println("Я упал");
        }
    }

    private class ReadHandler implements  CompletionHandler<Integer, ClientData> {
        private final CompletionHandler<Integer, ClientData> outputHandler = new WriteHandler();

        @Override
        public void completed(Integer integer, ClientData clientData) {
            if (integer < 0) {
                clientData.close();
                return;
            }
            if (clientData.isReadingSize) {
                processSize(clientData);
            } else {
                processData(clientData);
            }
        }

        @Override
        public void failed(Throwable throwable, ClientData clientData) {
            clientData.close();
            System.out.println("Я упал");
        }

        private void processSize(ClientData clientData) {
            if (!clientData.messageSizeBuffer.hasRemaining()) {
                startReadingData(clientData);
            } else {
                readSize(clientData);
            }
        }

        private void startReadingData(ClientData clientData) {
            clientData.isReadingSize = false;
            int size = getDataSize(clientData);
            clientData.messageBuffer = ByteBuffer.allocate(size);
            clientData.channel.read(clientData.messageBuffer, clientData, this);
        }

        private int getDataSize(ClientData clientData) {
            clientData.messageSizeBuffer.flip();
            int size = clientData.messageSizeBuffer.getInt();
            clientData.messageSizeBuffer.clear();
            return size;
        }

        private void readSize(ClientData clientData) {
            clientData.channel.read(clientData.messageSizeBuffer, clientData, this);
        }

        private void processData(ClientData clientData) {
            if (!clientData.messageBuffer.hasRemaining()) {
                addTaskAndStartReadingSize(clientData);
            } else {
                readData(clientData);
            }
        }

        private void addTaskAndStartReadingSize(ClientData clientData) {
            clientData.isReadingSize = true;
            ByteBuffer buffer = clientData.messageBuffer;
            readSize(clientData);
            addTask(clientData, buffer);
        }

        private void addTask(ClientData clientData, ByteBuffer dataBuffer) {
            try {
                dataBuffer.flip();
                IntArray array = ProtoUtils.readArray(dataBuffer);
                final int id = array.getId();
                startMeasure(id);
                workersThreadPool.submit(() -> {
                    IntArraysUtils.sort(array.getData());
                    clientData.addOutput(ProtoUtils.serialize(array));
                    if (clientData.numberOfUnfinishedOutputs.incrementAndGet() == 1) {
                        clientData.channel.write(clientData.getNextOutput(), clientData, outputHandler);
                    }
                    endMeasure(id);
                });
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }

        private void readData(ClientData clientData) {
            clientData.channel.read(clientData.messageBuffer, clientData, this);
        }
    }

    private static class WriteHandler implements CompletionHandler<Integer, ClientData> {
        @Override
        public void completed(Integer integer, ClientData clientData) {
            if (integer < 0) {
                clientData.close();
                return;
            }
            if (clientData.getCurrentOutput().hasRemaining()) {
                clientData.channel.write(clientData.getCurrentOutput(), clientData, this);
            } else {
                if (clientData.numberOfUnfinishedOutputs.decrementAndGet() > 0) {
                    clientData.channel.write(clientData.getNextOutput(), clientData, this);
                }
            }
        }

        @Override
        public void failed(Throwable throwable, ClientData clientData) {
            clientData.close();
            System.out.println("Я упал");
        }
    }

    private static class ClientData {
        public final AtomicInteger numberOfUnfinishedOutputs = new AtomicInteger(0);
        public final ByteBuffer messageSizeBuffer = ByteBuffer.allocate(Integer.BYTES);
        public ByteBuffer messageBuffer;
        public boolean isReadingSize = true;
        public final AsynchronousSocketChannel channel;

        private final Queue<ByteBuffer> outputs = new ConcurrentLinkedQueue<>();
        private volatile ByteBuffer currentBuffer;

        private ClientData(AsynchronousSocketChannel channel) {
            this.channel = channel;
        }

        public ByteBuffer getNextOutput() {
            currentBuffer = outputs.remove();
            return currentBuffer;
        }

        public ByteBuffer getCurrentOutput() {
            return currentBuffer;
        }

        public void addOutput(ByteBuffer buffer) {
            outputs.add(buffer);
        }

        public void close() {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}

