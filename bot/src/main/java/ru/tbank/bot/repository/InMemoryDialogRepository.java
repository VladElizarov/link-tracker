package ru.tbank.bot.repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import ru.tbank.bot.dialog.TrackDialog;

@Repository
public class InMemoryDialogRepository implements DialogRepository {

    private final Map<Long, TrackDialog> dialogs = new ConcurrentHashMap<>();

    @Override
    public void save(long chatId, TrackDialog dialog) {
        dialogs.put(chatId, dialog);
    }

    @Override
    public TrackDialog find(long chatId) {
        return dialogs.get(chatId);
    }

    @Override
    public TrackDialog remove(long chatId) {
        return dialogs.remove(chatId);
    }
}
