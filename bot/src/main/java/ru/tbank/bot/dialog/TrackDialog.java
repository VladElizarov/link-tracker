package ru.tbank.bot.dialog;

import java.util.ArrayList;
import java.util.List;

public class TrackDialog {

    private DialogState state = DialogState.WAITING_LINK;
    private String link;
    private List<String> tags = new ArrayList<>();

    public DialogState getState() {
        return state;
    }

    public void setState(DialogState state) {
        this.state = state;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
