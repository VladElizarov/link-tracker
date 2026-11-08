package ru.tbank.bot;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import ru.tbank.common.dto.LinkResponse;
import ru.tbank.common.dto.ListLinksResponse;

public final class TestData {

    private TestData() {
    }

    public static HttpClientErrorException httpError(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0], null);
    }

    public static ListLinksResponse links(LinkResponse... links) {
        return new ListLinksResponse(List.of(links), links.length);
    }

    public static LinkResponse link(long id, String url, String... tags) {
        return new LinkResponse(id, url, List.of(tags), List.of());
    }
}
