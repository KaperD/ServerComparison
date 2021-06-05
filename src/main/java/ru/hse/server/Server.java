package ru.hse.server;

import ru.hse.statistics.Statistics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Server {
    private final Statistics statistics;
    private final Map<Integer, Long> measurements = new ConcurrentHashMap<>();

    protected Server(Statistics statistics) {
        this.statistics = statistics;
    }

    protected void startMeasure(int id) {
        measurements.put(id, System.currentTimeMillis());
    }

    protected void endMeasure(int id) {
        long startTime = measurements.get(id);
        statistics.addMeasurementServer(System.currentTimeMillis() - startTime);
    }

    public abstract void start(int port, int numberOfWorkers) throws ServerException;
    public abstract void shutdown() throws ServerException;
}
