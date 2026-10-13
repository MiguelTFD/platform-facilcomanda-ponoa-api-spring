package com.facilcomanda.ponoa.sales;

import com.facilcomanda.ponoa.global.exception.ResourceNotFoundException;
import com.facilcomanda.ponoa.inventory.StockService;
import com.facilcomanda.ponoa.sales.event.OrderPlacedEvent;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService {

  private final OrderRepository orderRepository;
  private final SkuPricingRepository skuPricingRepository;
  private final StockService stockService;
  private final ApplicationEventPublisher eventPublisher;

  OrderService(
      OrderRepository orderRepository,
      SkuPricingRepository skuPricingRepository,
      StockService stockService,
      ApplicationEventPublisher eventPublisher) {
    this.orderRepository = orderRepository;
    this.skuPricingRepository = skuPricingRepository;
    this.stockService = stockService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Long placeOrder(
      UUID userId, String idempotencyKey, String userEmail, PlaceOrderRequest request) {
    Set<Long> requestedSkuIds =
        request.items().stream()
            .map(PlaceOrderRequest.OrderItemPayload::skuId)
            .collect(Collectors.toSet());

    Map<Long, BigDecimal> officialPrices =
        skuPricingRepository.findPricesBySkuIds(requestedSkuIds).stream()
            .collect(Collectors.toMap(SkuPriceView::skuId, SkuPriceView::currentPrice));

    BigDecimal totalOrderAmount = BigDecimal.ZERO;

    Set<OrderItem> orderItems =
        request.items().stream()
            .map(
                payload -> {
                  BigDecimal unitPrice = officialPrices.get(payload.skuId());

                  if (unitPrice == null) {
                    throw new ResourceNotFoundException(
                        "El SKU " + payload.skuId() + " is unavailable or does not exist.");
                  }

                  stockService.deductStock(payload.skuId(), payload.quantity());

                  return new OrderItem(payload.skuId(), payload.quantity(), unitPrice);
                })
            .collect(Collectors.toSet());

    for (OrderItem item : orderItems) {
      BigDecimal lineTotal =
          item.unitPriceAtPurchase().multiply(BigDecimal.valueOf(item.quantity()));
      totalOrderAmount = totalOrderAmount.add(lineTotal);
    }

    CustomerOrder newOrder =
        new CustomerOrder(null, userId, idempotencyKey, totalOrderAmount, "PENDING", orderItems);

    Long savedOrderId = orderRepository.save(newOrder).id();

    eventPublisher.publishEvent(new OrderPlacedEvent(savedOrderId, userEmail, totalOrderAmount));

    return savedOrderId;
  }
}
