package com.aec.ordsrv.controller;

import com.aec.ordsrv.dto.CartDto;
import com.aec.ordsrv.dto.CollaboratorMonthlyStatsDto;
import com.aec.ordsrv.dto.CreateOrderRequest;
import com.aec.ordsrv.dto.CreatePaypalOrderRequest;
import com.aec.ordsrv.dto.OrderDto;
import com.aec.ordsrv.dto.ProductSalesDto;
import com.aec.ordsrv.model.PaymentStatus;
import com.aec.ordsrv.service.OrderService;
import com.aec.ordsrv.service.ZipService;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.http.exceptions.HttpException;
import com.paypal.orders.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.aec.ordsrv.dto.ProductSalesDto; // Importar el nuevo DTO
import com.aec.ordsrv.dto.CollaboratorMonthlyStatsDto; // Importar el nuevo DTO
import java.io.IOException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
public class OrderController {

        private final PayPalHttpClient paypalClient;
        private final OrderService orderService;
        private final ZipService zipService;
        private static final Logger log = LoggerFactory.getLogger(OrderController.class);

        /** 1) Crear orden en PayPal (solo CLIENTE) **/
        @PostMapping
        @PreAuthorize("hasAuthority('ROL_CLIENTE')")
        public ResponseEntity<Map<String, String>> createPaypalOrder(
                        @Valid @RequestBody CreatePaypalOrderRequest req,
                        Authentication authentication) throws IOException {

                String username = authentication.getName();
                log.info("=== CREANDO ORDEN PAYPAL === user={} items={}", username,
                                req.getItems() != null ? req.getItems().size() : 0);

                List<Item> ppItems = req.getItems().stream().map(i -> new Item()
                                .name(i.getName())
                                .sku(String.valueOf(i.getProductId()))
                                .quantity(String.valueOf(i.getQuantity()))
                                .unitAmount(new Money()
                                                .currencyCode(req.getCurrency())
                                                .value(String.format(Locale.US, "%.2f", i.getUnitPrice()))))
                                .toList();

                double itemTotal = req.getItems().stream()
                                .mapToDouble(i -> i.getUnitPrice() * i.getQuantity())
                                .sum();

                AmountBreakdown breakdown = new AmountBreakdown()
                                .itemTotal(new Money()
                                                .currencyCode(req.getCurrency())
                                                .value(String.format(Locale.US, "%.2f", itemTotal)));

                AmountWithBreakdown amountWithBreakdown = new AmountWithBreakdown()
                                .currencyCode(req.getCurrency())
                                .value(String.format(Locale.US, "%.2f", req.getTotalAmount()))
                                .amountBreakdown(breakdown);

                PurchaseUnitRequest unit = new PurchaseUnitRequest()
                                .amountWithBreakdown(amountWithBreakdown)
                                .items(ppItems)
                                .customId(ppItems.get(0).sku());

                OrdersCreateRequest pcRequest = new OrdersCreateRequest()
                                .prefer("return=representation")
                                .requestBody(new OrderRequest()
                                                .checkoutPaymentIntent("CAPTURE")
                                                .purchaseUnits(List.of(unit)));

                com.paypal.orders.Order order = paypalClient.execute(pcRequest).result();
                log.info("Orden creada en PayPal: {}", order.id());
                return ResponseEntity.ok(Map.of("orderId", order.id()));
        }

        /** 2) Capturar pago y registrar la orden en BD **/
        @PostMapping("/{orderId}/capture")
        public ResponseEntity<OrderDto> capture(
                        @PathVariable String orderId,
                        Authentication authentication) {

                String customerUsername = authentication.getName();

                try {
                        // 1) obtenemos la orden de PayPal antes de capturar (por logging o
                        // validaciones...)
                        OrdersGetRequest getReq = new OrdersGetRequest(orderId);
                        paypalClient.execute(getReq);

                        // 2) capturamos en PayPal
                        HttpResponse<com.paypal.orders.Order> capResp = paypalClient
                                        .execute(new OrdersCaptureRequest(orderId));
                        log.info("Orden {} capturada en PayPal con status={}", orderId, capResp.result().status());

                        // 3) delegamos en el service, pasando username y orderId
                        OrderDto dto = orderService.completeOrderFromCart(customerUsername, orderId);
                        return ResponseEntity.ok(dto);

                } catch (HttpException e) {
                        log.error("PayPal API error al capturar {}: {}", orderId, e.getMessage(), e);
                        return ResponseEntity.status(e.statusCode()).build();
                } catch (Exception e) {
                        log.error("Error inesperado al capturar {}", orderId, e);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Error al procesar captura de la orden");
                }
        }

        /** Descarga del pedido (activa y vacía el carrito) **/
@GetMapping("/{orderId}/download")
@PreAuthorize("hasAuthority('ROL_CLIENTE')")
public void downloadOrderFiles(
        @PathVariable String orderId,
        HttpServletResponse response,
        Authentication authentication) throws IOException {

    log.info("==> DESCARGA ZIP para orden {}", orderId);
        String username = authentication.getName(); // Obtiene el nombre de usuario del objeto Authentication
    OrderDto dto = orderService.findOrderDtoById(orderId, username); // <-- Pasa AHORA los dos argumentos
    
    Long productId = dto.getItems().get(0).getProductId();

    response.setContentType("application/zip");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"pedido-" + orderId + "-productos.zip\"");
    zipService.streamProductZip(productId, response.getOutputStream());
    orderService.clearCart(authentication.getName());
}

        @GetMapping
        @PreAuthorize("hasAuthority('ROL_CLIENTE')")
        public ResponseEntity<CartDto> viewCart(@AuthenticationPrincipal Jwt jwtPrincipal) {
                // aquí ya no manejamos productClient ni FeignExceptions: lo hace el service
                CartDto dto = orderService.viewCart(jwtPrincipal.getSubject());
                return ResponseEntity.ok(dto);
        }

        /** Manual payment endpoints **/
        @PostMapping("/manual")
        public ResponseEntity<OrderDto> createManualPaymentOrder(
                        @RequestBody @Valid CreateOrderRequest request,
                        Authentication authentication) {

                String customerUsername = authentication.getName();
                OrderDto order = orderService.createManualOrder(request, customerUsername);
                return ResponseEntity.status(HttpStatus.CREATED).body(order);
        }

        @PutMapping("/admin/orders/{orderId}/review-payment")
        public ResponseEntity<OrderDto> reviewPayment(
                        @PathVariable Long orderId,
                        @RequestParam boolean approve,
                        @RequestBody(required = false) Map<String, String> body,
                        Authentication authentication) {

                String adminUsername = authentication.getName();
                String comment = (body != null) ? body.get("comment") : null;
                OrderDto updated = approve
                                ? orderService.approveManualPayment(orderId, comment, adminUsername)
                                : orderService.rejectManualPayment(orderId, comment, adminUsername);
                return ResponseEntity.ok(updated);
        }

        @PostMapping(path = "/{orderId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<OrderDto> uploadReceiptForOrder(
                        @PathVariable Long orderId,
                        @RequestPart("file") MultipartFile file,
                        Authentication authentication) {

                String uploader = authentication.getName();
                OrderDto updated = orderService.uploadReceipt(orderId, file, uploader);
                return ResponseEntity.ok(updated);
        }

        @GetMapping("/pending-receipts")
        public Page<OrderDto> getOrdersPendingReceiptReview(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {

                Pageable pg = PageRequest.of(page, size);
                return orderService.getOrdersByPaymentStatus(PaymentStatus.UPLOADED_RECEIPT, pg);
        }

        @PatchMapping("/admin/orders/{orderId}/approve-payment")
        public ResponseEntity<OrderDto> approveManualPayment(
                        @PathVariable Long orderId,
                        @RequestParam(required = false) String adminComment,
                        Authentication authentication) {

                String adminUsername = authentication.getName();
                OrderDto dto = orderService.approveManualPayment(orderId, adminComment, adminUsername);
                return ResponseEntity.ok(dto);
        }

        @PatchMapping("/admin/orders/{orderId}/reject-payment")
        public ResponseEntity<OrderDto> rejectManualPayment(
                        @PathVariable Long orderId,
                        @RequestParam(required = false) String adminComment,
                        Authentication authentication) {

                String adminUsername = authentication.getName();
                OrderDto dto = orderService.rejectManualPayment(orderId, adminComment, adminUsername);
                return ResponseEntity.ok(dto);
        }

        @GetMapping("/my-orders/latest")
        public ResponseEntity<OrderDto> getLatestOrder(Authentication auth) {
                String username = auth.getName();
                OrderDto dto = orderService.findLatestOrderForUser(username);
                return ResponseEntity.ok(dto);
        }

        @GetMapping("/{orderIdentifier}") // Cambia el nombre para ser más genérico
@PreAuthorize("hasAuthority('ROL_CLIENTE')") // Asume que solo los clientes pueden ver sus propias órdenes
public ResponseEntity<OrderDto> getOrderByIdentifier(
        @PathVariable String orderIdentifier, // Puede ser el ID de PayPal o el ID de DB
        Authentication authentication) {
    
    log.info("==> Solicitud para obtener orden por identificador: {}", orderIdentifier);
    String username = authentication.getName(); // Para verificar si la orden pertenece al usuario
    
    OrderDto dto = orderService.findOrderDtoById(orderIdentifier, username); 
    
    return ResponseEntity.ok(dto);
}

}