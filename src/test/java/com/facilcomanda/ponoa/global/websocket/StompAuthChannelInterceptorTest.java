package com.facilcomanda.ponoa.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

  @Mock private JwtDecoder jwtDecoder;
  @Mock private BranchAccessValidator branchAccessValidator;
  @Mock private MessageChannel messageChannel;

  @InjectMocks private StompAuthChannelInterceptor interceptor;

  @Test
  @DisplayName("Should authenticate user in CONNECT frame when Bearer token is provided")
  void shouldAuthenticateOnConnect() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setLeaveMutable(true);
    accessor.addNativeHeader("Authorization", "Bearer valid-token");
    Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Jwt jwt =
        new Jwt(
            "valid-token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "none"),
            Map.of("sub", "user-123"));

    when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

    Message<?> result = interceptor.preSend(message, messageChannel);

    assertThat(result).isNotNull();
    StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
    assertThat(resultAccessor.getUser()).isInstanceOf(JwtAuthenticationToken.class);
  }

  @Test
  @DisplayName("Should throw AccessDeniedException on CONNECT when no auth header is present")
  void shouldThrowWhenNoAuthOnConnect() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Missing Authorization header");
  }

  @Test
  @DisplayName("Should authorize subscription to branch topic when user has access")
  void shouldAuthorizeSubscription() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setDestination("/topic/branches/5/kitchen");

    Jwt jwt =
        new Jwt(
            "valid-token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "none"),
            Map.of("sub", "user-123", "branch_id", 5L));

    accessor.setUser(new JwtAuthenticationToken(jwt));
    Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, messageChannel);

    assertThat(result).isNotNull();
    verify(branchAccessValidator).resolveAndValidateBranchId(jwt, 5L);
  }

  @Test
  @DisplayName("Should reject subscription when user is unauthorized for branch")
  void shouldRejectUnauthorizedSubscription() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setDestination("/topic/branches/5/kitchen");

    Jwt jwt =
        new Jwt(
            "valid-token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "none"),
            Map.of("sub", "user-123", "branch_id", 2L));

    accessor.setUser(new JwtAuthenticationToken(jwt));
    Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    doThrow(new AccessDeniedException("Unauthorized branch"))
        .when(branchAccessValidator)
        .resolveAndValidateBranchId(jwt, 5L);

    assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Unauthorized branch");
  }
}
