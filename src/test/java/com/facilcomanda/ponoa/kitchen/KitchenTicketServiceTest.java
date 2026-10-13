package com.facilcomanda.ponoa.kitchen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.kitchen.payload.KitchenTicketResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KitchenTicketServiceTest {

  @Mock private KitchenTicketRepository kitchenTicketRepository;
  @Mock private KitchenScreenPublisher kitchenScreenPublisher;

  @InjectMocks private KitchenTicketService kitchenTicketService;

  @Test
  @DisplayName("Should create ticket for order, save it, and publish to screen topic")
  void shouldCreateTicketAndPublish() {
    Long branchId = 1L;
    Long orderId = 100L;

    KitchenTicket savedTicket =
        new KitchenTicket(
            10L,
            branchId,
            orderId,
            KitchenTicketStatus.PENDING,
            "Sin cebolla",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(kitchenTicketRepository.save(any(KitchenTicket.class))).thenReturn(savedTicket);

    Long ticketId = kitchenTicketService.createTicketForOrder(branchId, orderId, "Sin cebolla");

    assertThat(ticketId).isEqualTo(10L);
    verify(kitchenTicketRepository).save(any(KitchenTicket.class));
    verify(kitchenScreenPublisher).publishTicketUpdate(eq(branchId), any(KitchenTicketResponse.class));
  }

  @Test
  @DisplayName("Should update ticket status and publish live update to screen topic")
  void shouldUpdateStatusAndPublish() {
    Long branchId = 1L;
    Long ticketId = 10L;

    when(kitchenTicketRepository.updateStatusAtomic(ticketId, branchId, "IN_PREPARATION"))
        .thenReturn(1);

    KitchenTicket updatedTicket =
        new KitchenTicket(
            ticketId,
            branchId,
            100L,
            KitchenTicketStatus.IN_PREPARATION,
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(kitchenTicketRepository.findByIdAndBranchId(ticketId, branchId))
        .thenReturn(Optional.of(updatedTicket));

    KitchenTicketResponse response =
        kitchenTicketService.updateTicketStatus(branchId, ticketId, KitchenTicketStatus.IN_PREPARATION);

    assertThat(response.status()).isEqualTo(KitchenTicketStatus.IN_PREPARATION);
    verify(kitchenScreenPublisher).publishTicketUpdate(eq(branchId), any(KitchenTicketResponse.class));
    verify(kitchenScreenPublisher)
        .publishOrderStatusNotification(branchId, 100L, KitchenTicketStatus.IN_PREPARATION, null);
  }

  @Test
  @DisplayName("Should throw ResourceNotFoundException when updating non-existent ticket")
  void shouldThrowWhenTicketNotFoundOnUpdate() {
    when(kitchenTicketRepository.updateStatusAtomic(99L, 1L, "READY")).thenReturn(0);

    assertThatThrownBy(
            () -> kitchenTicketService.updateTicketStatus(1L, 99L, KitchenTicketStatus.READY))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  @Test
  @DisplayName("Should return active tickets for branch")
  void shouldReturnActiveTickets() {
    KitchenTicket t1 =
        new KitchenTicket(1L, 1L, 101L, KitchenTicketStatus.PENDING, null, null, null);
    KitchenTicket t2 =
        new KitchenTicket(2L, 1L, 102L, KitchenTicketStatus.IN_PREPARATION, null, null, null);

    when(kitchenTicketRepository.findAllByBranchIdAndStatusIn(
            eq(1L),
            eq(
                Set.of(
                    KitchenTicketStatus.PENDING,
                    KitchenTicketStatus.IN_PREPARATION,
                    KitchenTicketStatus.READY))))
        .thenReturn(List.of(t1, t2));

    List<KitchenTicketResponse> tickets = kitchenTicketService.getActiveTicketsByBranch(1L);

    assertThat(tickets).hasSize(2);
    assertThat(tickets.get(0).status()).isEqualTo(KitchenTicketStatus.PENDING);
    assertThat(tickets.get(1).status()).isEqualTo(KitchenTicketStatus.IN_PREPARATION);
  }
}
