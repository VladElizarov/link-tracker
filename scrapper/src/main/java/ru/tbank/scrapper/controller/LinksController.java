package ru.tbank.scrapper.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tbank.common.dto.AddLinkRequest;
import ru.tbank.common.dto.LinkResponse;
import ru.tbank.common.dto.ListLinksResponse;
import ru.tbank.common.dto.RemoveLinkRequest;
import ru.tbank.scrapper.cache.CachingLinkService;
import ru.tbank.scrapper.service.LinkService;

@RestController
@RequestMapping("/links")
public class LinksController {

    public static final String CHAT_ID_HEADER = "Tg-Chat-Id";

    private final LinkService linkService;
    private final CachingLinkService cachingLinkService;

    public LinksController(LinkService linkService, CachingLinkService cachingLinkService) {
        this.linkService = linkService;
        this.cachingLinkService = cachingLinkService;
    }

    @GetMapping
    public ListLinksResponse getLinks(@RequestHeader(CHAT_ID_HEADER) long chatId) {
        return cachingLinkService.getAll(chatId);
    }

    @PostMapping
    public LinkResponse addLink(
        @RequestHeader(CHAT_ID_HEADER) long chatId,
        @RequestBody @Valid AddLinkRequest request
    ) {
        return linkService.add(chatId, request.link(), request.tags(), request.filters());
    }

    @DeleteMapping
    public LinkResponse removeLink(
        @RequestHeader(CHAT_ID_HEADER) long chatId,
        @RequestBody @Valid RemoveLinkRequest request
    ) {
        return linkService.remove(chatId, request.link());
    }
}
