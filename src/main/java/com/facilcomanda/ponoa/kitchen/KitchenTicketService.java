package com.facilcomanda.ponoa.kitchen;

import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.kitchen.payload.KitchenTicketResponse;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class KitchenTicketService {

  private static final Set<KitchenTicketStatus> ACTIVE_STATUSES =
      Set.of(
          KitchenTicketStatus.PENDING,
          KitchenTicketStatus.IN_PREPARATION,
          KitchenTicketStatus.READY);

  private final KitchenTicketRepository kitchenTicketRepository;
  private final KitchenScreenPublisher kitchenScreenPublisher;

  KitchenTicketService(
      KitchenTicketRepository kitchenTicketRepository,
      KitchenScreenPublisher kitchenScreenPublisher) {
    this.kitchenTicketRepository = kitchenTicketRepository;
    this.kitchenScreenPublisher = kitchenScreenPublisher;
  }

  @Transactional
  public Long createTicketForOrder(Long branchId, Long orderId, String notes) {
    KitchenTicket ticket =
        new KitchenTicket(
            null, branchId, orderId, KitchenTicketStatus.PENDING, notes, null, null);

    KitchenTicket savedTicket = kitchenTicketRepository.save(ticket);
    KitchenTicketResponse response = toResponse(savedTicket);

    kitchenScreenPublisher.publishTicketUpdate(branchId, response);

    return savedTicket.id();
  }

  @Transactional
  public KitchenTicketResponse updateTicketStatus(
      Long branchId, Long ticketId, KitchenTicketStatus newStatus) {

    int updatedRows =
        kitchenTicketRepository.updateStatusAtomic(ticketId, branchId, newStatus.name());

    if (updatedRows == 0) {
      throw new ResourceNotFoundException(
          "Kitchen ticket " + ticketId + " not found in branch " + branchId);
    }

    KitchenTicket updatedTicket =
        kitchenTicketRepository
            .findByIdAndBranchId(ticketId, branchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Kitchen ticket " + ticketId + " not found in branch " + branchId));

    KitchenTicketResponse response = toResponse(updatedTicket);
    kitchenScreenPublisher.publishTicketUpdate(branchId, response);
    kitchenScreenPublisher.publishOrderStatusNotification(
        branchId, updatedTicket.orderId(), updatedTicket.status(), updatedTicket.notes());

    return response;
  }

  @Transactional
  public void updateTicketStatusForOrder(
      Long branchId, Long orderId, KitchenTicketStatus newStatus) {
    kitchenTicketRepository
        .findByOrderId(orderId)
        .ifPresent(
            ticket -> {
              if (ticket.status() != KitchenTicketStatus.CANCELLED) {
                updateTicketStatus(branchId, ticket.id(), newStatus);
              }
            });
  }

  @Transactional(readOnly = true)
  public List<KitchenTicketResponse> getActiveTicketsByBranch(Long branchId) {
    return kitchenTicketRepository.findAllByBranchIdAndStatusIn(branchId, ACTIVE_STATUSES).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public KitchenTicketResponse getTicketById(Long branchId, Long ticketId) {
    return kitchenTicketRepository
        .findByIdAndBranchId(ticketId, branchId)
        .map(this::toResponse)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Kitchen ticket " + ticketId + " not found in branch " + branchId));
  }

  @Transactional
  public void appendAdditionToTicket(
      Long branchId, Long orderId, String tableName, String additionNotes) {
    kitchenTicketRepository
        .findByOrderId(orderId)
        .ifPresentOrElse(
            ticket -> {
              String updatedNotes = ticket.notes();
              String additionTag =
                  (additionNotes != null && !additionNotes.isBlank())
                      ? "[ADICION]: " + additionNotes
                      : "[ADICION DE PLATOS]";
              updatedNotes =
                  (updatedNotes != null && !updatedNotes.isBlank())
                      ? updatedNotes + " | " + additionTag
                      : additionTag;

              kitchenTicketRepository.updateNotesAndStatus(
                  ticket.id(), branchId, updatedNotes, KitchenTicketStatus.PENDING.name());

              KitchenTicket updatedTicket =
                  new KitchenTicket(
                      ticket.id(),
                      branchId,
                      orderId,
                      KitchenTicketStatus.PENDING,
                      updatedNotes,
                      ticket.createdAt(),
                      null);

              kitchenScreenPublisher.publishTicketUpdate(branchId, toResponse(updatedTicket));
            },
            () -> {
              String notes =
                  "Mesa: " + (tableName != null ? tableName : "N/A") + " | [ADICION]: " + additionNotes;
              createTicketForOrder(branchId, orderId, notes);
            });
  }

  @Transactional
  public void cancelTicketsForOrder(Long branchId, Long orderId, String reason) {
    String cancelNote = "[CANCELADA]: " + (reason != null ? reason : "Orden cancelada");
    kitchenTicketRepository.cancelTicketsByOrder(branchId, orderId, cancelNote);
    kitchenTicketRepository
        .findByOrderId(orderId)
        .ifPresent(
            ticket -> {
              KitchenTicketResponse response = toResponse(ticket);
              kitchenScreenPublisher.publishTicketUpdate(branchId, response);
              kitchenScreenPublisher.publishOrderStatusNotification(
                  branchId, ticket.orderId(), KitchenTicketStatus.CANCELLED, cancelNote);
            });
  }

  private KitchenTicketResponse toResponse(KitchenTicket ticket) {
    return new KitchenTicketResponse(
        ticket.id(),
        ticket.branchId(),
        ticket.orderId(),
        ticket.status(),
        ticket.notes(),
        ticket.createdAt());
  }
}
