package com.facilcomanda.ponoa.report;

import com.facilcomanda.ponoa.report.payload.ProductSalesRankingResponse;
import com.facilcomanda.ponoa.report.payload.SalesSummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

  private final JdbcTemplate jdbcTemplate;

  public ReportService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public SalesSummaryResponse getSalesSummary(Long branchId, OffsetDateTime from, OffsetDateTime to) {
    String invoiceSql =
        "SELECT COUNT(id) as invoice_count, COALESCE(SUM(order_total), 0) as total_sales "
            + "FROM invoices "
            + "WHERE (? IS NULL OR branch_id = ?) "
            + "  AND paid_at >= ? AND paid_at <= ?";

    Map<String, Object> invoiceTotals =
        jdbcTemplate.queryForMap(invoiceSql, branchId, branchId, from, to);

    Long invoiceCount = ((Number) invoiceTotals.get("invoice_count")).longValue();
    BigDecimal totalSales = (BigDecimal) invoiceTotals.get("total_sales");
    if (totalSales == null) {
      totalSales = BigDecimal.ZERO;
    }

    BigDecimal netAmount =
        totalSales.compareTo(BigDecimal.ZERO) > 0
            ? totalSales.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
    BigDecimal taxAmount = totalSales.subtract(netAmount);

    String paymentSql =
        "SELECT ip.method, COALESCE(SUM(ip.amount), 0) as total "
            + "FROM invoice_payments ip "
            + "JOIN invoices i ON ip.invoice_id = i.id "
            + "WHERE (? IS NULL OR i.branch_id = ?) "
            + "  AND i.paid_at >= ? AND i.paid_at <= ? "
            + "GROUP BY ip.method";

    Map<String, BigDecimal> salesByPayment = new HashMap<>();
    jdbcTemplate.query(
        paymentSql,
        ps -> {
          ps.setObject(1, branchId);
          ps.setObject(2, branchId);
          ps.setObject(3, from);
          ps.setObject(4, to);
        },
        rs -> {
          salesByPayment.put(rs.getString("method"), rs.getBigDecimal("total"));
        });

    String typeSql =
        "SELECT invoice_type, COALESCE(SUM(order_total), 0) as total "
            + "FROM invoices "
            + "WHERE (? IS NULL OR branch_id = ?) "
            + "  AND paid_at >= ? AND paid_at <= ? "
            + "GROUP BY invoice_type";

    Map<String, BigDecimal> salesByType = new HashMap<>();
    jdbcTemplate.query(
        typeSql,
        ps -> {
          ps.setObject(1, branchId);
          ps.setObject(2, branchId);
          ps.setObject(3, from);
          ps.setObject(4, to);
        },
        rs -> {
          salesByType.put(rs.getString("invoice_type"), rs.getBigDecimal("total"));
        });

    return new SalesSummaryResponse(
        branchId, from, to, totalSales, netAmount, taxAmount, invoiceCount, salesByPayment, salesByType);
  }

  @Transactional(readOnly = true)
  public ProductSalesRankingResponse getProductSalesRanking(
      Long branchId, OffsetDateTime from, OffsetDateTime to, int limit) {

    int maxLimit = Math.min(Math.max(limit, 1), 100);

    String sql =
        "SELECT oi.sku_id, sp.sku_code, p.name as product_name, sp.presentation_name, "
            + "SUM(oi.quantity) as quantity_sold, "
            + "COALESCE(SUM(oi.quantity * oi.unit_price_at_purchase), 0) as total_revenue "
            + "FROM order_items oi "
            + "JOIN customer_orders co ON oi.order_id = co.id "
            + "JOIN sku_presentations sp ON oi.sku_id = sp.id "
            + "JOIN products p ON sp.product_id = p.id "
            + "WHERE (? IS NULL OR co.branch_id = ?) "
            + "  AND co.status = 'PAID' "
            + "  AND (co.created_at >= ? AND co.created_at <= ?) "
            + "GROUP BY oi.sku_id, sp.sku_code, p.name, sp.presentation_name "
            + "ORDER BY quantity_sold DESC, total_revenue DESC "
            + "LIMIT ?";

    List<ProductSalesRankingResponse.ProductSaleItem> items =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                new ProductSalesRankingResponse.ProductSaleItem(
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("product_name"),
                    rs.getString("presentation_name"),
                    rs.getLong("quantity_sold"),
                    rs.getBigDecimal("total_revenue")),
            branchId,
            branchId,
            from,
            to,
            maxLimit);

    return new ProductSalesRankingResponse(branchId, from, to, items);
  }
}
