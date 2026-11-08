package ru.tbank.scrapper.repository.jpa.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id")
    private ChatEntity chat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "link_id")
    private LinkEntity link;

    @ManyToMany
    @JoinTable(
        name = "subscription_tags",
        joinColumns = @JoinColumn(name = "subscription_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @OrderBy("name")
    private Set<TagEntity> tags = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "subscription_filters", joinColumns = @JoinColumn(name = "subscription_id"))
    @Column(name = "filter_value")
    @OrderBy
    private List<String> filters = new ArrayList<>();

    protected SubscriptionEntity() {
    }

    public SubscriptionEntity(ChatEntity chat, LinkEntity link) {
        this.chat = chat;
        this.link = link;
    }

    public Long getId() {
        return id;
    }

    public ChatEntity getChat() {
        return chat;
    }

    public LinkEntity getLink() {
        return link;
    }

    public Set<TagEntity> getTags() {
        return tags;
    }

    public List<String> getFilters() {
        return filters;
    }
}
