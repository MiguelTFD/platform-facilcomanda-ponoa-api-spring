package com.facilcomanda.ponoa.global.websocket;

import com.facilcomanda.ponoa.global.security.BranchAccessValidator;
import com.facilcomanda.ponoa.global.security.KeycloakRoleConverter;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  private static final Pattern BRANCH_TOPIC_PATTERN =
      Pattern.compile("^/topic/branches/(\\d+)(/.*)?$");

  private final JwtDecoder jwtDecoder;
  private final BranchAccessValidator branchAccessValidator;
  private final KeycloakRoleConverter keycloakRoleConverter = new KeycloakRoleConverter();

  public StompAuthChannelInterceptor(
      JwtDecoder jwtDecoder, BranchAccessValidator branchAccessValidator) {
    this.jwtDecoder = jwtDecoder;
    this.branchAccessValidator = branchAccessValidator;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null) {
      return message;
    }

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      authenticateStompClient(accessor);
    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      authorizeSubscription(accessor);
    }

    return message;
  }

  private void authenticateStompClient(StompHeaderAccessor accessor) {
    String authHeader = accessor.getFirstNativeHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      // Optional fallback: allow 'passcode' native header for web browsers / STOMP clients
      authHeader = accessor.getFirstNativeHeader("passcode");
      if (authHeader == null) {
        throw new AccessDeniedException("Missing Authorization header in STOMP CONNECT frame");
      }
    }

    String tokenValue =
        authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

    Jwt jwt = jwtDecoder.decode(tokenValue);
    Collection<GrantedAuthority> authorities = keycloakRoleConverter.convert(jwt);
    JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, authorities);

    accessor.setUser(authentication);
  }

  private void authorizeSubscription(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null) {
      return;
    }

    Matcher matcher = BRANCH_TOPIC_PATTERN.matcher(destination);
    if (matcher.matches()) {
      Long branchId = Long.parseLong(matcher.group(1));

      if (!(accessor.getUser() instanceof JwtAuthenticationToken jwtAuth)) {
        throw new AccessDeniedException(
            "Unauthenticated STOMP subscription attempt to: " + destination);
      }

      branchAccessValidator.resolveAndValidateBranchId(jwtAuth.getToken(), branchId);
    }
  }
}
