package ru.tbank.scrapper.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.tbank.common.dto.LinkResponse;
import ru.tbank.common.dto.ListLinksResponse;
import ru.tbank.scrapper.config.CacheConfig;
import ru.tbank.scrapper.exception.ChatNotFoundException;
import ru.tbank.scrapper.service.LinkService;

class CachingLinkServiceTest {

    private static final ListLinksResponse RESPONSE = new ListLinksResponse(
        List.of(new LinkResponse(1L, "https://github.com/user/repo", List.of(), List.of())), 1);

    private final LinkService linkService = mock(LinkService.class);
    private final LinkListCache cache = mock(LinkListCache.class);
    private final CachingLinkService service = new CachingLinkService(linkService, cache);

    @Test
    void cacheHitShouldNotCallService() {
        when(cache.get(1L)).thenReturn(RESPONSE);

        assertThat(service.getAll(1L)).isEqualTo(RESPONSE);
        verify(linkService, never()).getAll(anyLong());
    }

    @Test
    void cacheMissShouldCallServiceAndPutResult() {
        when(linkService.getAll(1L)).thenReturn(RESPONSE);

        assertThat(service.getAll(1L)).isEqualTo(RESPONSE);
        verify(cache).put(1L, RESPONSE);
    }

    @Test
    void errorFromServiceShouldNotBeCached() {
        when(linkService.getAll(1L)).thenThrow(new ChatNotFoundException(1L));

        assertThatThrownBy(() -> service.getAll(1L)).isInstanceOf(ChatNotFoundException.class);
        verify(cache, never()).put(anyLong(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unavailableValkeyShouldWorkAsEmptyCache() {
        StatefulRedisConnection<String, String> connection = mock(StatefulRedisConnection.class);
        RedisCommands<String, String> commands = mock(RedisCommands.class);
        when(connection.sync()).thenReturn(commands);
        RedisCommandTimeoutException timeout = new RedisCommandTimeoutException("Command timed out after 500 ms");
        when(commands.get(anyString())).thenThrow(timeout);
        when(commands.set(anyString(), anyString(), any(SetArgs.class))).thenThrow(timeout);
        when(commands.del(anyString())).thenThrow(timeout);
        ValkeyLinkListCache valkeyCache = new ValkeyLinkListCache(
            connection, new ObjectMapper(), new CacheConfig(CacheConfig.Mode.VALKEY, Duration.ofMinutes(10)));
        when(linkService.getAll(1L)).thenReturn(RESPONSE);

        assertThat(new CachingLinkService(linkService, valkeyCache).getAll(1L)).isEqualTo(RESPONSE);
        assertThatCode(() -> valkeyCache.evict(1L)).doesNotThrowAnyException();
    }

    @Test
    void localCopyShouldForgetValueAfterTtl() throws InterruptedException {
        ExpiringCacheAccessor local = new ExpiringCacheAccessor(Duration.ofMillis(50));
        local.put("1", "json");
        assertThat(local.get("1")).isEqualTo("json");

        Thread.sleep(100);

        assertThat(local.get("1")).isNull();
    }
}
