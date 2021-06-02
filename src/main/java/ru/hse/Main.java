package ru.hse;

import ru.hse.client.Client;
import ru.hse.server.AsynchronousServer;
import ru.hse.server.BlockingServer;
import ru.hse.server.Server;
import ru.hse.server.ServerException;
import ru.hse.statistics.Statistics;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    private static final int NUMBER_OF_SERVER_WORKERS = 5;
    private static final int PORT = 1234;
    private ServerType serverType;
    private int numberOfElementsInArray;
    private int numberOfClients;
    private int requestsTimeDelta;
    private int numberOfRequestsPerClient;
    private final Scanner scanner = new Scanner(System.in);
    private Parameter changingParameter;
    private int lowerBound;
    private int upperBound;
    private int step;

    private enum ServerType {
        BLOCKING {
            @Override
            public Server getInstance() {
                return new BlockingServer(NUMBER_OF_SERVER_WORKERS);
            }

            @Override
            public String toString() {
                return "Blocking";
            }
        },
        ASYNCHRONOUS {
            @Override
            public Server getInstance() {
                return new AsynchronousServer(NUMBER_OF_SERVER_WORKERS);
            }

            @Override
            public String toString() {
                return "Asynchronous";
            }
        };

        public abstract Server getInstance();
    }

    private enum Parameter {
        ARRAY_SIZE {
            @Override
            public String toString() {
                return "ArraySize";
            }
        },
        NUMBER_OF_CLIENTS {
            @Override
            public String toString() {
                return "NumberOfClients";
            }
        },
        TIME_BETWEEN_REQUESTS {
            @Override
            public String toString() {
                return "TimeBetweenRequests";
            }
        },
    }

    public Main() {
        askServerType();
        askNumberOfRequestsPerClient();
        askChangingParameter();
        askBounds();
        askRestParameters();
    }

    public String run() throws ServerException, ExecutionException, InterruptedException {
        StringBuilder builder = new StringBuilder();
        builder.append(serverType).append(System.lineSeparator());
        builder.append("NumberOfRequestsPerClient ").
                append(numberOfRequestsPerClient).
                append(System.lineSeparator());
        if (!changingParameter.equals(Parameter.ARRAY_SIZE)) {
            builder.append(Parameter.ARRAY_SIZE).
                    append(" ").
                    append(numberOfElementsInArray).
                    append(System.lineSeparator());
        }
        if (!changingParameter.equals(Parameter.NUMBER_OF_CLIENTS)) {
            builder.append(Parameter.NUMBER_OF_CLIENTS).
                    append(" ").
                    append(numberOfClients).
                    append(System.lineSeparator());
        }
        if (!changingParameter.equals(Parameter.TIME_BETWEEN_REQUESTS)) {
            builder.append(Parameter.TIME_BETWEEN_REQUESTS).
                    append(" ").
                    append(requestsTimeDelta).
                    append(System.lineSeparator());
        }
        builder.append(changingParameter).append(System.lineSeparator());

        while (lowerBound <= upperBound) {
            if (changingParameter.equals(Parameter.ARRAY_SIZE)) {
                numberOfElementsInArray = lowerBound;
            }
            if (changingParameter.equals(Parameter.NUMBER_OF_CLIENTS)) {
                numberOfClients = lowerBound;
            }
            if (changingParameter.equals(Parameter.TIME_BETWEEN_REQUESTS)) {
                requestsTimeDelta = lowerBound;
            }
            long time = test();
            builder.append(lowerBound).append(" ").append(time).append(System.lineSeparator());
            lowerBound += step;
        }

        return builder.toString();
    }

    private long test() throws ServerException, InterruptedException, ExecutionException {
        Server server = serverType.getInstance();
        server.start(PORT);
        ExecutorService threadPool = Executors.newCachedThreadPool();
        Statistics statistics = new Statistics();
        List<Future<Void>> futures = threadPool.invokeAll(
                IntStream.range(0, numberOfClients).mapToObj(
                        id -> Client.getBuilder().
                                id(id * numberOfRequestsPerClient).
                                host("localhost").
                                port(PORT).
                                arraySize(numberOfElementsInArray).
                                delta(requestsTimeDelta).
                                cycles(numberOfRequestsPerClient).
                                statistics(statistics).
                                build()).collect(Collectors.toList())
        );
        for (Future<Void> future : futures) {
            future.get();
        }
        threadPool.shutdown();
        server.shutdown();
        return statistics.getAverageTimeInMillis();
    }

    public void askServerType() {
        while (true) {
            System.out.println("Chose server type:");
            System.out.println("1. Blocking");
            System.out.println("2. Asynchronous");
            printPrefix();
            int type = scanner.nextInt();
            if (type < 1 || type > 2) {
                System.out.println("Wrong type, try again");
                continue;
            }
            if (type == 1) {
                serverType = ServerType.BLOCKING;
            }
            if (type == 2) {
                serverType = ServerType.ASYNCHRONOUS;
            }
            return;
        }
    }

    public void askNumberOfRequestsPerClient() {
        System.out.println("Write number of requests per client:");
        printPrefix();
        numberOfRequestsPerClient = scanner.nextInt();
    }

    public void askChangingParameter() {
        while (true) {
            System.out.println("Chose changing parameter:");
            System.out.println("1. Array size");
            System.out.println("2. Number of clients");
            System.out.println("3. Time between requests");
            printPrefix();
            int type = scanner.nextInt();
            if (type < 1 || type > 3) {
                System.out.println("Wrong parameter, try again");
                continue;
            }
            if (type == 1) {
                changingParameter = Parameter.ARRAY_SIZE;
            }
            if (type == 2) {
                changingParameter = Parameter.NUMBER_OF_CLIENTS;
            }
            if (type == 3) {
                changingParameter = Parameter.TIME_BETWEEN_REQUESTS;
            }
            return;
        }
    }

    public void askBounds() {
        askLowerBound();
        askUpperBound();
        askStep();
    }

    public void askRestParameters() {
        if (!changingParameter.equals(Parameter.ARRAY_SIZE)) {
            askArraySize();
        }
        if (!changingParameter.equals(Parameter.NUMBER_OF_CLIENTS)) {
            askNumberOfClients();
        }
        if (!changingParameter.equals(Parameter.TIME_BETWEEN_REQUESTS)) {
            askTimeBetweenRequests();
        }
    }

    private void askLowerBound() {
        while (true) {
            System.out.println("Write lower bound:");
            printPrefix();
            lowerBound = scanner.nextInt();
            if (lowerBound >= 0) {
                return;
            }
            System.out.println("Bound must be non negative");
        }
    }

    private void askUpperBound() {
        while (true) {
            System.out.println("Write upper bound:");
            printPrefix();
            upperBound = scanner.nextInt();
            if (upperBound >= 0 && upperBound >= lowerBound) {
                return;
            }
            if (upperBound < 0) {
                System.out.println("Bound must be non negative");
            }
            if (upperBound < lowerBound) {
                System.out.println("Upper bound must be not less than lower bound");
            }
        }
    }

    private void askStep() {
        while (true) {
            System.out.println("Write step:");
            printPrefix();
            step = scanner.nextInt();
            if (step > 0) {
                return;
            }
            System.out.println("Step must be positive");
        }
    }

    private void askArraySize() {
        while (true) {
            System.out.println("Write array size:");
            printPrefix();
            numberOfElementsInArray = scanner.nextInt();
            if (numberOfElementsInArray > 0) {
                return;
            }
            System.out.println("Array size must be positive");
        }
    }

    private void askNumberOfClients() {
        while (true) {
            System.out.println("Write number of clients:");
            printPrefix();
            numberOfClients = scanner.nextInt();
            if (numberOfClients > 0) {
                return;
            }
            System.out.println("Number of clients must be positive");
        }
    }

    private void askTimeBetweenRequests() {
        while (true) {
            System.out.println("Write time between requests:");
            printPrefix();
            requestsTimeDelta = scanner.nextInt();
            if (requestsTimeDelta >= 0) {
                return;
            }
            System.out.println("Time between requests must be non negative");
        }
    }

    private void printPrefix() {
        System.out.print(">> ");
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();

        System.out.println(main.run());
    }

}
