package ru.hse.server;

public interface Server {
    void start(int port) throws ServerException;
    void shutdown() throws ServerException;
}
