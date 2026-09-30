package com.order.api.configuration;

import com.order.api.listener.CartExpiryListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests the subscription to Redis key expiry that cart expiry emails depend on. */
class CartExpiryConfigTest {

    private RedisConnectionFactory connectionFactory;
    private RedisConnection connection;
    private RedisServerCommands serverCommands;
    private CartExpiryListener listener;

    @BeforeEach
    void setUp() {
        connectionFactory = mock(RedisConnectionFactory.class);
        connection = mock(RedisConnection.class);
        serverCommands = mock(RedisServerCommands.class);
        listener = mock(CartExpiryListener.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.serverCommands()).thenReturn(serverCommands);
    }

    private void givenServerSetting(String value) {
        Properties properties = new Properties();
        if (value != null) {
            properties.setProperty("notify-keyspace-events", value);
        }
        when(serverCommands.getConfig("notify-keyspace-events")).thenReturn(properties);
    }

    @Test
    void shouldListenOnTheConnectionFactory() {
        givenServerSetting("Ex");

        RedisMessageListenerContainer container = new CartExpiryConfig("Ex")
                .cartExpiryListenerContainer(connectionFactory, listener);

        assertSame(connectionFactory, container.getConnectionFactory());
        verify(connection).close();
    }

    @Test
    void shouldEnableExpiryEventsOnAServerThatHasNone() {
        givenServerSetting("");

        new CartExpiryConfig("Ex").cartExpiryListenerContainer(connectionFactory, listener);

        verify(serverCommands).setConfig("notify-keyspace-events", "Ex");
    }

    @Test
    void shouldEnableExpiryEventsWhenTheServerReturnsNoConfig() {
        when(serverCommands.getConfig("notify-keyspace-events")).thenReturn(null);

        new CartExpiryConfig("Ex").cartExpiryListenerContainer(connectionFactory, listener);

        verify(serverCommands).setConfig("notify-keyspace-events", "Ex");
    }

    @Test
    void shouldKeepASettingTheServerAlreadyHas() {
        givenServerSetting("KEA");

        new CartExpiryConfig("Ex").cartExpiryListenerContainer(connectionFactory, listener);

        verify(serverCommands, never()).setConfig(anyString(), anyString());
    }

    @Test
    void shouldKeepASettingWithoutExpiryEventsButStillStart() {
        givenServerSetting("Kg");

        assertDoesNotThrow(() -> new CartExpiryConfig("Ex").cartExpiryListenerContainer(connectionFactory, listener));

        verify(serverCommands, never()).setConfig(anyString(), anyString());
    }

    @Test
    void shouldStartWhenRedisCannotBeConfigured() {
        when(connectionFactory.getConnection()).thenThrow(new DataAccessResourceFailureException("redis is down"));

        assertDoesNotThrow(() -> new CartExpiryConfig("Ex").cartExpiryListenerContainer(connectionFactory, listener));
    }

    @Test
    void shouldLeaveTheServerAloneWhenNoSettingIsConfigured() {
        new CartExpiryConfig("").cartExpiryListenerContainer(connectionFactory, listener);

        verifyNoInteractions(connectionFactory);
    }
}
