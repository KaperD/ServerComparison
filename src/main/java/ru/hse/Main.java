package ru.hse;

import ru.hse.client.Client;
import ru.hse.server.AsynchronousServer;
import ru.hse.server.BlockingServer;
import ru.hse.server.Server;
import ru.hse.statistics.Statistics;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    private static final int N = 300;

    public static void main(String[] args) throws Exception {
        Server server = new AsynchronousServer(5);
        server.start(8080);
        ExecutorService threadPool = Executors.newCachedThreadPool();
        Statistics statistics = new Statistics();
        List<Future<Void>> futures = threadPool.invokeAll(
                IntStream.range(0, N).mapToObj(
                        id -> Client.getBuilder().
                        id(id * 2).
                        host("localhost").
                        port(8080).
                        arraySize(1000).
                        delta(200).
                        cycles(4).
                        statistics(statistics).
                        build()).collect(Collectors.toList())
        );
        for (Future<Void> future : futures) {
            future.get();
        }
        threadPool.shutdown();
        server.shutdown();
        System.out.println(statistics.getAverageTimeInMillis());
    }

}
