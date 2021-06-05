package ru.hse.client;

import ru.hse.data.IntArray;
import ru.hse.statistics.Statistics;
import ru.hse.utils.ProtoUtils;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class Client implements Callable<Void> {
    private final Map<Integer, Long> measurements = new ConcurrentHashMap<>();

    private int id;
    private final String host;
    private final int port;
    private final int arraySize;
    private final int delta;
    private final int cycles;
    private final Statistics statistics;
    private Socket socket;

    public static Builder getBuilder() {
        return new Builder();
    }

    private Client(int id, String host, int port, int arraySize, int delta, int cycles, Statistics statistics) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.arraySize = arraySize;
        this.delta = delta;
        this.cycles = cycles;
        this.statistics = statistics;
    }

    @Override
    public Void call() throws IOException {
        socket = new Socket(host, port);
        Thread requestsThread = new Thread(() -> {
            try {
                for (int k = 0; k < cycles; k++) {
                    int[] data = generateArray();
                    IntArray array = new IntArray(id, data);
                    long startMillis = System.currentTimeMillis();
                    measurements.put(id, startMillis);
                    id++;
                    ProtoUtils.writeArray(socket.getOutputStream(), array);
                    long endMillis = System.currentTimeMillis();
                    Thread.sleep(Math.max(delta - (endMillis - startMillis), 0));
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        requestsThread.start();
        try {
            for (int k = 0; k < cycles; k++) {
                IntArray sortedArray = ProtoUtils.readArray(socket.getInputStream());
                long start = measurements.get(sortedArray.getId());
                long time = System.currentTimeMillis() - start;
                statistics.addMeasurementClient(time);
//                System.out.println("Task " + sortedArray.getId() + " is ready");
//                checkData(sortedArray.getData(), sortedArray.getId());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        statistics.stopMeasurements();
        socket.close();
        return null;
    }

    private final Random r = new Random();
    private int[] generateArray() {
        return IntStream.generate(r::nextInt).limit(arraySize).toArray();
    }

    private void checkData(int[] sortedData, int id) {
        boolean isOk = true;
        for (int k = 0; k < sortedData.length - 1; k++) {
            if (sortedData[k] > sortedData[k + 1]) {
                isOk = false;
                break;
            }
        }

        if (isOk) {
            System.out.println("Client " + id + " is OK");
        } else {
            System.out.println("Client " + id + " fails");
        }
    }

    public static class Builder {
        private int id;
        private String host;
        private int port;
        private int arraySize;
        private int delta;
        private int cycles;
        private Statistics statistics;

        private Builder() {

        }

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder arraySize(int arraySize) {
            this.arraySize = arraySize;
            return this;
        }

        public Builder delta(int delta) {
            this.delta = delta;
            return this;
        }

        public Builder cycles(int cycles) {
            this.cycles = cycles;
            return this;
        }

        public Builder statistics(Statistics statistics) {
            this.statistics = statistics;
            return this;
        }

        public Client build() {
            return new Client(id, host, port, arraySize, delta, cycles, statistics);
        }
    }
}
