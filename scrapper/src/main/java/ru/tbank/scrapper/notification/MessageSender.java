package ru.tbank.scrapper.notification;

import ru.tbank.common.dto.LinkUpdate;

public interface MessageSender {

    void send(long messageId, LinkUpdate update);
}
