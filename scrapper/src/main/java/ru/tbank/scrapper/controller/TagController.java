package ru.tbank.scrapper.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.tbank.scrapper.controller.dto.LinkTagRequest;
import ru.tbank.scrapper.controller.dto.TagRequest;
import ru.tbank.scrapper.service.TagService;

@RestController
public class TagController {

    private static final String CHAT_ID_HEADER = LinksController.CHAT_ID_HEADER;

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/tags")
    public List<String> getTags(@RequestHeader(CHAT_ID_HEADER) long chatId) {
        return tagService.getTags(chatId);
    }

    @PostMapping("/tags")
    public void createTag(@RequestHeader(CHAT_ID_HEADER) long chatId, @RequestBody @Valid TagRequest request) {
        tagService.create(chatId, request.name());
    }

    @PutMapping("/tags/{name}")
    public void renameTag(
        @RequestHeader(CHAT_ID_HEADER) long chatId,
        @PathVariable String name,
        @RequestBody @Valid TagRequest request
    ) {
        tagService.rename(chatId, name, request.name());
    }

    @DeleteMapping("/tags/{name}")
    public void deleteTag(@RequestHeader(CHAT_ID_HEADER) long chatId, @PathVariable String name) {
        tagService.delete(chatId, name);
    }

    @PostMapping("/links/tags")
    public void addTagToLink(@RequestHeader(CHAT_ID_HEADER) long chatId, @RequestBody @Valid LinkTagRequest request) {
        tagService.addTagToLink(chatId, request.link(), request.tag());
    }

    @DeleteMapping("/links/tags")
    public void removeTagFromLink(
        @RequestHeader(CHAT_ID_HEADER) long chatId,
        @RequestBody @Valid LinkTagRequest request
    ) {
        tagService.removeTagFromLink(chatId, request.link(), request.tag());
    }
}
