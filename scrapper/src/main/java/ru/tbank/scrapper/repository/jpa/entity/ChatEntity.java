package ru.tbank.scrapper.repository.jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chats")
public class ChatEntity {

    @Id
    private Long id;

    protected ChatEntity() {
    }

    public ChatEntity(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
