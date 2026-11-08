package ru.tbank.scrapper.repository;

import java.util.List;

public interface TagRepository {

    List<String> findNamesByChatId(long chatId);

    boolean exists(long chatId, String name);

    void create(long chatId, String name);

    boolean rename(long chatId, String oldName, String newName);

    boolean delete(long chatId, String name);

    boolean addToSubscription(long chatId, String url, String name);

    boolean removeFromSubscription(long chatId, String url, String name);
}
