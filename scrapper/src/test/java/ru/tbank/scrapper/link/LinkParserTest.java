package ru.tbank.scrapper.link;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkParserTest {

    private final LinkParser parser = new LinkParser();

    @Test
    void githubRepositoryLinkShouldBeParsed() {
        assertThat(parser.parse("https://github.com/user/repo"))
            .isEqualTo(new GitHubLink("user", "repo"));
    }

    @Test
    void githubLinkWithExtraPathShouldBeParsed() {
        assertThat(parser.parse("https://github.com/user/repo/issues/1"))
            .isEqualTo(new GitHubLink("user", "repo"));
    }

    @Test
    void stackOverflowQuestionLinkShouldBeParsed() {
        assertThat(parser.parse("https://stackoverflow.com/questions/11227809/why-is-it-faster"))
            .isEqualTo(new StackOverflowLink(11227809L));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "tbank://github.com/user/repo",
        "https://github.com/user",
        "https://github.com",
        "https://stackoverflow.com/questions/abc",
        "https://stackoverflow.com/users/123",
        "https://example.com/user/repo",
        "не ссылка",
        ""
    })
    void unsupportedLinksShouldReturnNull(String url) {
        assertThat(parser.parse(url)).isNull();
    }
}
