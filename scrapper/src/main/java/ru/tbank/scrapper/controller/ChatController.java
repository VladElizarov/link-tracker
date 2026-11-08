package ru.tbank.scrapper.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tbank.scrapper.service.ChatService;

@RestController
@RequestMapping("/tg-chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/{id}")
    public void registerChat(@PathVariable long id) {
        chatService.register(id);
    }

    @DeleteMapping("/{id}")
    public void deleteChat(@PathVariable long id) {
        chatService.delete(id);
    }
}
