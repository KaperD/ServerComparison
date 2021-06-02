package ru.hse.server;

public interface Server {
    void start(int numberOfThreads) throws ServerException;
    void shutdown() throws ServerException;
}
