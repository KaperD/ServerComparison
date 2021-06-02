package ru.hse.statistics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Statistics {
    private final AtomicBoolean needMeasurement = new AtomicBoolean(true);
    private final AtomicLong sumTime = new AtomicLong(0);
    private final AtomicLong numberOfMeasurements = new AtomicLong(0);

    public void addMeasurement(long millis) {
        if (needMeasurement.get()) {
            sumTime.addAndGet(millis);
            numberOfMeasurements.incrementAndGet();
        }
    }

    public void stopMeasurements() {
        needMeasurement.set(false);
    }

    public long getAverageTimeInMillis() {
        System.out.println(numberOfMeasurements);
        if (numberOfMeasurements.get() == 0) {
            return 0;
        }
        return sumTime.get() / numberOfMeasurements.get();
    }
}
