package ru.hse.statistics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Statistics {
    private final AtomicBoolean needMeasurement = new AtomicBoolean(true);
    private final AtomicLong sumTimeClients = new AtomicLong(0);
    private final AtomicLong numberOfMeasurementsClients = new AtomicLong(0);
    private final AtomicLong sumTimeServer = new AtomicLong(0);
    private final AtomicLong numberOfMeasurementsServer = new AtomicLong(0);

    public void addMeasurementClient(long millis) {
        if (needMeasurement.get()) {
            sumTimeClients.addAndGet(millis);
            numberOfMeasurementsClients.incrementAndGet();
        }
    }

    public void addMeasurementServer(long millis) {
        if (needMeasurement.get()) {
            sumTimeServer.addAndGet(millis);
            numberOfMeasurementsServer.incrementAndGet();
        }
    }

    public void reset() {
        sumTimeClients.set(0);
        sumTimeServer.set(0);
        numberOfMeasurementsClients.set(0);
        numberOfMeasurementsServer.set(0);
        needMeasurement.set(true);
    }

    public void stopMeasurements() {
        needMeasurement.set(false);
    }

    public long getAverageTimeInMillisClients() {
        if (numberOfMeasurementsClients.get() == 0) {
            return 0;
        }
        return (sumTimeClients.get() + numberOfMeasurementsClients.get() - 1) / numberOfMeasurementsClients.get();
    }

    public long getAverageTimeInMillisServer() {
        if (numberOfMeasurementsServer.get() == 0) {
            return 0;
        }
        return (sumTimeServer.get() + numberOfMeasurementsServer.get() - 1) / numberOfMeasurementsServer.get();
    }

    public long getNumberOfMeasurementsClients() {
        return numberOfMeasurementsClients.get();
    }

    public long getNumberOfMeasurementsServer() {
        return numberOfMeasurementsServer.get();
    }
}
