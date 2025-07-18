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

   @Query("""
    SELECT new com.aec.ordsrv.dto.ProductSalesDto(
      oi.productId,
      p.nombre,
      o.paymentMethod,
      SUM(oi.precioUnitario * oi.cantidad),
      SUM(oi.cantidad),
      COUNT(o.id)
    )
    FROM OrderItem oi
    JOIN oi.order o
    JOIN Product p ON p.id = oi.productId
    WHERE p.uploaderUsername = :uploader
      AND o.status = 'COMPLETED'
      AND o.creadoEn BETWEEN :from AND :to
    GROUP BY oi.productId, p.nombre, o.paymentMethod
  """)
  List<ProductSalesDto> findByUploaderAndPeriod(
    @Param("uploader") String uploader,
    @Param("from")     Instant from,
    @Param("to")       Instant to
  );

}