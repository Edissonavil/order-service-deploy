package com.aec.ordsrv.controller;

import com.aec.ordsrv.dto.CartCountDto;
import com.aec.ordsrv.dto.CartDto;
import com.aec.ordsrv.dto.CartItemDto;
import com.aec.ordsrv.dto.OrderDto; 
import com.aec.ordsrv.model.Order;
import com.aec.ordsrv.model.OrderItem;
import com.aec.ordsrv.model.OrderStatus;
import com.aec.ordsrv.repository.OrderRepository;
import com.aec.ordsrv.service.OrderService; 

import feign.Feign;
import feign.FeignException;
import jakarta.transaction.Transactional;

import com.aec.ordsrv.client.ProductClient;
import com.aec.ordsrv.dto.ProductDto;
import com.aec.ordsrv.exceptions.ProductNotFoundException; // Asegúrate de que esta excepción esté correctamente definida

import lombok.RequiredArgsConstructor;
import feign.FeignException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders/cart")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
public class CartController {
    private final OrderService orderService; // <-- ¡Inyectamos OrderService!
    private final ProductClient productClient;
        private static final Logger log = LoggerFactory.getLogger(OrderService.class);
            private final OrderRepository orderRepository;



    /** 1) GET /api/orders/cart → ver el contenido completo del carrito */

@GetMapping
@PreAuthorize("hasAuthority('ROL_CLIENTE')")
public ResponseEntity<CartDto> viewCart(@AuthenticationPrincipal Jwt jwtPrincipal) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    final String bearer;
    if (auth instanceof JwtAuthenticationToken) {
        bearer = "Bearer " + ((JwtAuthenticationToken) auth).getToken().getTokenValue();
    } else {
        throw new IllegalStateException("No hay JWT en el SecurityContext");
    }

    Order cart = orderService.getOrCreateCart(jwtPrincipal.getSubject());

    List<OrderItem> itemsToRemove = new java.util.ArrayList<>();

    List<CartItemDto> items = cart.getItems().stream().map(i -> {
        try {
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
        } catch (FeignException.NotFound ex) {
            log.warn("🗑 Producto ID {} eliminado: se eliminará del carrito de {}", i.getProductId(), jwtPrincipal.getSubject());
            itemsToRemove.add(i);
            return null;
        }
    }).filter(Objects::nonNull)
      .collect(Collectors.toList());

    if (!itemsToRemove.isEmpty()) {
        cart.getItems().removeAll(itemsToRemove);
        cart.setTotal(cart.getItems().stream().mapToDouble(OrderItem::getSubtotal).sum());
        orderRepository.save(cart);
        log.info("🛒 Se eliminaron {} productos obsoletos del carrito del usuario {}", itemsToRemove.size(), jwtPrincipal.getSubject());
    }

    CartDto dto = CartDto.builder()
                         .items(items)
                         .total(cart.getTotal())
                         .build();
    return ResponseEntity.ok(dto);
}



    /** 3) POST /api/orders/cart/{productId} → añade 1 unidad y retorna carrito */
    @Transactional
    public Order addItemToCart(String username, Long productId, int cantidad) {
        log.info("Añadiendo producto {} (cantidad {}) al carrito de {}", productId, cantidad, username);

        // 1) Extraer token del SecurityContext
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken)) {
            throw new IllegalStateException("No hay JWT en el SecurityContext");
        }
        String token = ((JwtAuthenticationToken) auth).getToken().getTokenValue();
        String bearer = "Bearer " + token;

        // 2) Llamar al microservicio de productos pasando el header
        ProductDto prod = productClient.getById(productId, bearer);
        if (prod == null || prod.getPrecioIndividual() == null) {
            log.error("Producto con ID {} no encontrado o sin precio.", productId);
            throw new IllegalArgumentException("El precio para el producto " + productId + " no está disponible.");
        }
        double precioUnitario = prod.getPrecioIndividual();

        // 3) Conseguir o crear carrito
        Order cart = orderRepository
            .findByClienteUsernameAndStatus(username, OrderStatus.CART)
            .orElseGet(() -> {
                log.info("Creando nuevo carrito para el usuario: {}", username);
                Order c = new Order();
                return orderRepository.save(c);
            });

        // 4) Agregar o actualizar item
        Optional<OrderItem> existing = cart.getItems().stream()
            .filter(i -> i.getProductId().equals(productId))
            .findFirst();

        if (existing.isPresent()) {
            var item = existing.get();
            item.setCantidad(item.getCantidad() + cantidad);
            item.setPrecioUnitario(precioUnitario);
            log.info("Actualizada cantidad de producto {} en carrito. Nueva cantidad: {}", productId, item.getCantidad());
        } else {
            OrderItem item = new OrderItem(productId, precioUnitario, cantidad);
            cart.addOrderItem(item);
            log.info("Añadido nuevo producto {} al carrito.", productId);
        }

        // 5) Recalcular totales
        cart.getItems().forEach(i -> i.setSubtotal(i.getPrecioUnitario() * i.getCantidad()));
        cart.setTotal(cart.getItems().stream().mapToDouble(OrderItem::getSubtotal).sum());

        return orderRepository.save(cart);
    }

    /** 2) GET /api/orders/cart/count */
    @GetMapping("/count")
        public int count(@AuthenticationPrincipal Jwt jwtPrincipal) {
        if (jwtPrincipal == null) {
            throw new IllegalStateException("No hay principal JWT en el SecurityContext.");
        }
        String username = jwtPrincipal.getSubject(); // Obtener el 'sub' (subject) del JWT
        // ... tu lógica para contar items del carrito para el usuario ...
        System.out.println("El username es: " + username);
        return 0; // Ejemplo
    }

    /** 3) POST /api/orders/cart/{productId} */
    @PostMapping("/{productId}")
    @PreAuthorize("hasAuthority('ROL_CLIENTE')")
    public ResponseEntity<CartDto> addItemToCart(
            @PathVariable Long productId,
            @AuthenticationPrincipal Jwt jwtPrincipal
    ) {
        String user = jwtPrincipal.getSubject();
        orderService.addItemToCart(user, productId, 1);
        return viewCart(jwtPrincipal);
    }

    /** 4) DELETE /api/orders/cart/{productId} */
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAuthority('ROL_CLIENTE')")
    public ResponseEntity<Void> removeItemFromCart(
            @PathVariable Long productId,
            @AuthenticationPrincipal Jwt jwtPrincipal
    ) {
        orderService.removeItemFromCart(jwtPrincipal.getSubject(), productId);
        return ResponseEntity.noContent().build();
    }
}

