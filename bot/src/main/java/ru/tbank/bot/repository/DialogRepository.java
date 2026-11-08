package ru.tbank.bot.repository;

import ru.tbank.bot.dialog.TrackDialog;

public interface DialogRepository {

    void save(long chatId, TrackDialog dialog);

    TrackDialog find(long chatId);

    TrackDialog remove(long chatId);
}
