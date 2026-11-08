package ru.tbank.scrapper.model;

import ru.tbank.common.dto.LinkUpdate;

public record OutboxMessage(long id, LinkUpdate update) {
}
