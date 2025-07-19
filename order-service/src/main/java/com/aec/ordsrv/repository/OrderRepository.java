// En com.aec.ordsrv.repository.OrderRepository.java
package com.aec.ordsrv.repository;

import com.aec.ordsrv.dto.ProductSalesDto;
import com.aec.ordsrv.model.Order;
import com.aec.ordsrv.model.OrderStatus;
import com.aec.ordsrv.model.PaymentStatus;

import feign.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByClienteUsernameAndStatus(String clienteUsername, OrderStatus status);
    Optional<Order> findByPaypalOrderId(String paypalOrderId);
    Optional<Order> findByManualTransferRefId(String manualTransferRefId);
    Page<Order> findByPaymentStatus(PaymentStatus status, Pageable pageable);
    Optional<Order> findFirstByClienteUsernameAndStatusOrderByOrderDateDesc(
            String clienteUsername, OrderStatus status);
    Optional<Order> findFirstByClienteUsernameAndPaymentStatusOrderByApprovalDateDesc(
            String clienteUsername, PaymentStatus paymentStatus);

    List<Order> findByStatusAndOrderDateBetween(OrderStatus status, Instant from, Instant to);

    List<Order> findByCreatorUsernameAndStatusAndCreadoEnBetween(String creatorUsername, OrderStatus status, Instant from, Instant to);
    List<Order> findByStatusAndCreadoEnBetween(OrderStatus status, Instant from, Instant to);

 @Query(value = """
  SELECT 
    oi.product_id AS productId,
    p.nombre AS nombre,
    o.payment_method AS paymentMethod,
    SUM(oi.precio_unitario * oi.cantidad) AS totalVenta,
    SUM(oi.cantidad) AS totalCantidad,
    COUNT(o.id) AS totalOrdenes
  FROM order_items oi
  JOIN orders o ON o.id = oi.order_id
  JOIN products p ON p.id = oi.product_id
  WHERE p.uploader_username = :uploader
    AND o.status = 'COMPLETED'
    AND o.creado_en BETWEEN :from AND :to
  GROUP BY oi.product_id, p.nombre, o.payment_method
""", nativeQuery = true)
List<Object[]> findByUploaderAndPeriodNative(
  @Param("uploader") String uploader,
  @Param("from") Instant from,
  @Param("to") Instant to
);

}