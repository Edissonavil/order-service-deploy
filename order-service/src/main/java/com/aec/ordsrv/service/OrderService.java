package com.aec.ordsrv.service;

import com.aec.ordsrv.client.UserClient;
import com.aec.ordsrv.client.ProductClient;
import com.aec.ordsrv.dto.CartDto;
import com.aec.ordsrv.dto.CartItemDto;
import com.aec.ordsrv.dto.CollaboratorMonthlyStatsDto;
import com.aec.ordsrv.dto.CreateOrderRequest;
import com.aec.ordsrv.dto.FileInfoDto;
import com.aec.ordsrv.dto.OrderDto;
import com.aec.ordsrv.dto.ProductDto;
import com.aec.ordsrv.dto.ProductSalesDto;
import com.aec.ordsrv.dto.UserResponseDto;
import com.aec.ordsrv.model.*;
import com.aec.ordsrv.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import feign.FeignException;

import jakarta.persistence.EntityNotFoundException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream; // Asegurarse de importar Stream


@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final WebClient.Builder webClientBuilder;
    private final EmailService emailService;
    private final UserClient userClient;
    private final WebClient userWebClient;

    @Value("${file-service}")
    private String fileServiceUrl;

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private String safePayMethod(Order o) {
        return o.getPaymentMethod() != null
             ? o.getPaymentMethod().name()
             : "UNKNOWN";
    }

    @Transactional
    public Order getOrCreateCart(String username) {
        return orderRepository.findByClienteUsernameAndStatus(username, OrderStatus.CART)
                .orElseGet(() -> {
                    log.info("Creando nuevo carrito para el usuario: {}", username);
                    Order cart = new Order();
                    cart.setClienteUsername(username);
                    cart.setCreatorUsername(username);
                    cart.setStatus(OrderStatus.CART);
                    cart.setOrderDate(Instant.now());
                    cart.setCreadoEn(Instant.now());
                    return orderRepository.save(cart);
                });
    }

    @Transactional(readOnly = true)
    public CartDto viewCart(String username) {
        // 1) Obtiene o crea carrito
        Order cart = getOrCreateCart(username);
        String bearer = extractBearer();

        // 2) Limpia los ítems cuyos productos ya no existen
        cart.getItems().removeIf(item -> {
            try {
                productClient.getById(item.getProductId(), bearer);
                return false;
            } catch (FeignException.NotFound e) {
                log.warn("Producto {} no existe, eliminándolo del carrito de {}", item.getProductId(), username);
                return true;
            }
        });

        // 3) Recalcula total y persiste
        cart.setTotal(cart.getItems().stream()
                        .mapToDouble(OrderItem::getSubtotal)
                        .sum());
        orderRepository.save(cart);

        // 4) Mapea a DTO
        List<CartItemDto> items = cart.getItems().stream()
            .map(i -> {
                ProductDto p = productClient.getById(i.getProductId(), bearer);
                return CartItemDto.builder()
                    .id(i.getId())
                    .productId(i.getProductId())
                    .unitPrice(i.getPrecioUnitario())
                    .quantity(i.getCantidad())
                    .subtotal(i.getSubtotal())
                    .name(p.getNombre())
                    .especialidades(p.getEspecialidades())
                    .pais(p.getPais())
                    .fotografiaProd(p.getFotografiaProd())
                    .build();
            })
            .collect(Collectors.toList());

        return CartDto.builder()
                .items(items)
                .total(cart.getTotal())
                .build();
    }

    // extrae el bearer del SecurityContext
    private String extractBearer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return "Bearer " + jwt.getToken().getTokenValue();
        }
        throw new IllegalStateException("No hay JWT en el SecurityContext");
    }

    @Transactional
    public OrderDto completeOrderFromCart(String customerUsername, String paypalOrderId) {
        OrderDto dto = completeOrderFromCartInternal(customerUsername, paypalOrderId);
        clearCart(customerUsername);
        return dto;
    }

    private UserResponseDto fetchUser(String username) {
        String jwt = jwtToken();

        return userWebClient.get()
                .uri("/api/users/by-username/{u}", username)
                .headers(h -> {
                    if (jwt != null)
                        h.setBearerAuth(jwt);
                })
                .retrieve()
                .bodyToMono(UserResponseDto.class)
                .block();
    }

@Transactional
private OrderDto completeOrderFromCartInternal(String customerUsername, String paypalOrderId) {

    Order cart = orderRepository
            .findByClienteUsernameAndStatus(customerUsername, OrderStatus.CART)
            .orElseThrow(() ->
                    new EntityNotFoundException("No hay carrito activo para " + customerUsername));

    // ➜ Datos del usuario
    UserResponseDto user = fetchUser(customerUsername);
    cart.setCustomerEmail(user.getEmail());
    cart.setCustomerFullName(user.getFullName());

    /* ----------  ESTADO FINAL DE LA ORDEN  ---------- */
    cart.setStatus(OrderStatus.COMPLETED);
    cart.setPaymentMethod(PaymentMethod.PAYPAL);
    cart.setPaymentStatus(PaymentStatus.PAID);
    cart.setPaypalOrderId(paypalOrderId);
    cart.setOrderDate(Instant.now());
    cart.setCreadoEn(Instant.now());

    /* Guardamos y forzamos flush para garantizar que
       el PaymentStatus quede efectivamente como PAID
       antes de mapear al DTO.                       */
    Order saved = orderRepository.saveAndFlush(cart);

    // Notificación por correo
    try {
        emailService.sendPaymentApprovedEmail(
                saved.getCustomerEmail(),
                saved.getCustomerFullName(),
                saved.getId());
    } catch (Exception e) {
        log.error("Error al enviar email de pago aprobado para orden {}: {}", saved.getId(), e.getMessage());
    }

    return toDto(saved);
}

    @Transactional
    public void clearCart(String customerUsername) {
        orderRepository.findByClienteUsernameAndStatus(customerUsername, OrderStatus.CART)
                .ifPresent(cart -> {
                    cart.getItems().clear();
                    cart.setTotal(0);
                    orderRepository.save(cart);
                });
    }

    @Transactional
    public OrderDto createManualOrder(CreateOrderRequest request, String customerUsername) {
        UserResponseDto user = fetchUser(customerUsername);

        Order order = new Order();
        order.setClienteUsername(customerUsername);
        order.setCreatorUsername(customerUsername);
        order.setCustomerEmail(user.getEmail());
        order.setCustomerFullName(user.getFullName());
        order.setTotal(request.getTotalAmount());
        order.setPaymentMethod(PaymentMethod.MANUAL_TRANSFER);
        order.setPaymentStatus(PaymentStatus.PENDING_PAYMENT);
        order.setManualTransferRefId(UUID.randomUUID().toString());
        order.setOrderDate(Instant.now());
        order.setCreadoEn(Instant.now());
        order.setStatus(OrderStatus.PENDING);

        List<OrderItem> items = request.getItems().stream().map(dto -> {
            OrderItem it = new OrderItem();
            it.setProductId(dto.getProductId());
            it.setCantidad(dto.getQuantity());
            it.setPrecioUnitario(dto.getUnitPrice());
            it.setSubtotal(dto.getUnitPrice() * dto.getQuantity());
            it.setOrder(order);
            return it;
        }).collect(Collectors.toList());
        order.setItems(items);

        Order saved = orderRepository.save(order);
        clearCart(customerUsername);
        return toDto(saved);
    }

@Transactional(readOnly = true)

public OrderDto findOrderDtoById(String orderIdentifier, String username) {

Optional<Order> orderOptional;



try {

// Intenta parsear como Long (ID de la base de datos)

Long id = Long.parseLong(orderIdentifier);

orderOptional = orderRepository.findById(id);

} catch (NumberFormatException e) {

// Si no es un Long, intenta buscar por PayPalOrderId o ManualTransferRefId

orderOptional = orderRepository.findByPaypalOrderId(orderIdentifier)

.or(() -> orderRepository.findByManualTransferRefId(orderIdentifier));

}



Order order = orderOptional.orElseThrow(() ->

new EntityNotFoundException("Orden no encontrada con identificador: " + orderIdentifier));



// Seguridad: Asegúrate de que el usuario actual es el propietario de la orden

if (!order.getClienteUsername().equals(username)) {

throw new AccessDeniedException("No tienes permiso para ver esta orden.");

}


return toDto(order);

}



      @Transactional
    public OrderDto uploadReceipt(Long orderId, MultipartFile file, String uploaderUsername) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada"));

        if (order.getPaymentMethod() != PaymentMethod.MANUAL_TRANSFER ||
                !(order.getPaymentStatus() == PaymentStatus.PENDING_PAYMENT ||
                        order.getPaymentStatus() == PaymentStatus.PAYMENT_REJECTED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede subir comprobante en el estado actual");
        }

        FileInfoDto info = uploadFileToService(file, uploaderUsername, orderId);
        order.setReceiptFilename(info.getFilename());
        order.setPaymentStatus(PaymentStatus.UPLOADED_RECEIPT);
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);

        try {
            emailService.sendReceiptUploadedNotification(
                    order.getId(),
                    order.getClienteUsername(),
                    order.getCustomerEmail(),
                    order.getTotal()
            );
        } catch (Exception e) {
            log.error("Error al enviar email de notificación de comprobante para orden {}: {}", order.getId(), e.getMessage());
        }

        return toDto(order);
    }


    @Transactional
public OrderDto approveManualPayment(Long orderId, String adminComment, String adminUsername) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada"));

    if (order.getPaymentStatus() != PaymentStatus.UPLOADED_RECEIPT) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La orden no está pendiente de revisión de comprobante");
    }

    order.setPaymentStatus(PaymentStatus.PAID);
    order.setStatus(OrderStatus.COMPLETED);
    order.setAdminApprovalUser(adminUsername);
    order.setApprovalDate(Instant.now());
    order.setAdminComment(adminComment);

    Order updated = orderRepository.saveAndFlush(order);
    clearCart(updated.getClienteUsername());


    try {
        log.info("Enviando email de aprobación manual a {} (nombre: {}, ordenId: {})",
                updated.getCustomerEmail(), updated.getCustomerFullName(), updated.getId());

        emailService.sendPaymentApprovedEmail(
                updated.getCustomerEmail(),
                updated.getCustomerFullName(),
                updated.getId()
        );
    } catch (Exception e) {
        log.error("Error al enviar email de aprobación manual para orden {}: {}", orderId, e.getMessage());
    }

    return toDto(updated);
}


    @Transactional
    public OrderDto rejectManualPayment(Long orderId, String adminComment, String adminUsername) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada"));

        if (order.getPaymentStatus() != PaymentStatus.UPLOADED_RECEIPT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La orden no está pendiente de revisión de comprobante");
        }

        order.setPaymentStatus(PaymentStatus.PAYMENT_REJECTED);
        order.setStatus(OrderStatus.PENDING);
        order.setAdminApprovalUser(adminUsername);
        order.setApprovalDate(Instant.now());
        order.setAdminComment(adminComment);
        order.setReceiptFilename(null);

        Order updated = orderRepository.save(order);
        try {
            emailService.sendPaymentRejectedEmail(
                    updated.getCustomerEmail(),
                    updated.getCustomerFullName(),
                    updated.getId(),
                    adminComment);
        } catch (Exception e) {
            log.error("Error al enviar email de rechazo manual para orden {}: {}", orderId, e.getMessage());
        }
        return toDto(updated);
    }

    @Transactional(readOnly = true)
    public OrderDto findLatestOrderForUser(String username) {
        return orderRepository
                .findFirstByClienteUsernameAndStatusOrderByOrderDateDesc(username, OrderStatus.COMPLETED)
                .or(() -> orderRepository.findFirstByClienteUsernameAndPaymentStatusOrderByApprovalDateDesc(
                        username, PaymentStatus.PAID))
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No se encontró ninguna orden completada"));
    }

    @Transactional
    public OrderDto addItemToCart(String username, Long productId, int cantidad) {
        Order cart = getOrCreateCart(username);
        String bearer = extractBearer();
        ProductDto prod = productClient.getById(productId, bearer);
        double precioUnitario = prod.getPrecioIndividual();
        Optional<OrderItem> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();
        if (existing.isPresent()) {
            var it = existing.get();
            it.setCantidad(it.getCantidad() + cantidad);
            it.setPrecioUnitario(precioUnitario);
        } else {
            cart.addOrderItem(new OrderItem(productId, precioUnitario, cantidad));
        }
        cart.getItems().forEach(i -> i.setSubtotal(i.getPrecioUnitario() * i.getCantidad()));
        cart.setTotal(cart.getItems().stream().mapToDouble(OrderItem::getSubtotal).sum());
        return toDto(orderRepository.save(cart));
    }

    @Transactional
    public void removeItemFromCart(String username, Long productId) {
        Order cart = getOrCreateCart(username);
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        cart.setTotal(cart.getItems().stream().mapToDouble(OrderItem::getSubtotal).sum());
        orderRepository.save(cart);
    }

    public Page<OrderDto> getOrdersByPaymentStatus(PaymentStatus status, Pageable pageable) {
        return orderRepository.findByPaymentStatus(status, pageable).map(this::toDto);
    }

    private String jwtToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        return null;
    }

    private FileInfoDto uploadFileToService(MultipartFile file, String uploader, Long orderId) {
        String jwt = extractJwtToken();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        body.add("uploader", uploader);

        return webClientBuilder.build()
                .post()
                .uri(fileServiceUrl + "/api/files/receipts/{orderId}", orderId)
                .headers(h -> {
                    if (jwt != null) {
                        h.setBearerAuth(jwt);
                    }
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(FileInfoDto.class)
                .block();
    }

    private String extractJwtToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        return null;
    }

    public List<OrderDto> findCompletedBetween(Instant from, Instant to) {
        return orderRepository
                .findByStatusAndOrderDateBetween(OrderStatus.COMPLETED, from, to)
                .stream()
                .map(this::toDto)
                .toList();
    }

// OrderService.java
private OrderDto toDto(Order o) {

    var items = o.getItems().stream()
        .map(i -> OrderDto.ItemDto.builder()
            .id(i.getId())
            .productId(i.getProductId())
            .cantidad(i.getCantidad())
            .precioUnitario(i.getPrecioUnitario())
            .subtotal(i.getSubtotal())
            .build())
        .toList();

    // ------------------------ NUEVO ------------------------
    String externalId = o.getPaypalOrderId() != null
            ? o.getPaypalOrderId()
            : o.getManualTransferRefId();          // puede quedar null para carritos
    // -------------------------------------------------------

    String status    = o.getStatus()        != null ? o.getStatus().name()        : null;
    String payMethod = o.getPaymentMethod() != null ? o.getPaymentMethod().name() : null;
    String payStatus = o.getPaymentStatus() != null ? o.getPaymentStatus().name() : null;

    String downloadUrl = null;
if (PaymentStatus.PAID.equals(o.getPaymentStatus()) && externalId != null) {
    downloadUrl = ServletUriComponentsBuilder
                      .fromCurrentContextPath()
                      .path("/api/orders/")
                      .path(externalId)
                      .path("/download")
                      .toUriString();

}

    return OrderDto.builder()
            .id(o.getId())
            .orderId(externalId)        
            .items(items)
            .total(o.getTotal())
            .creadoEn(o.getCreadoEn())
            .status(status)
            .paymentMethod(payMethod)
            .paymentStatus(payStatus)
            .downloadUrl(downloadUrl)
            .customerUsername(o.getClienteUsername())
            .customerEmail(o.getCustomerEmail())
            .customerFullName(o.getCustomerFullName())
            .receiptFilename(o.getReceiptFilename())
            .adminApprovalUser(o.getAdminApprovalUser())
            .approvalDate(o.getApprovalDate())
            .adminComment(o.getAdminComment())
            .build();
}




}


