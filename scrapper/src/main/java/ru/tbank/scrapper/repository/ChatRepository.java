package ru.tbank.scrapper.repository;

public interface ChatRepository {

    void save(long chatId);

    boolean exists(long chatId);

    void delete(long chatId);
}
