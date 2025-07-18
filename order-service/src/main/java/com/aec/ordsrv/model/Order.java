package com.aec.ordsrv.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant; // Importar Instant
import java.util.ArrayList;
import java.util.List;

// No necesitas importar OrderStatus y PaymentStatus de nuevo si ya están en el mismo paquete
// import com.aec.ordsrv.model.OrderStatus;
// import com.aec.ordsrv.model.PaymentStatus;

@Entity
@Table(name = "orders")
@Data
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderId; // Campo para el ID de orden visible al usuario, si aplica

    private String clienteUsername; // Nombre de usuario del cliente
    private String creatorUsername; // <-- ¡NUEVO CAMPO! Nombre de usuario del creador de la orden (colaborador/admin)
    private Long customerId; // ID del cliente, si lo usas
    private String customerEmail;
    private String customerFullName;

    private Instant creadoEn; // Fecha de creación de la orden, ahora Instant
    private Instant orderDate; // Fecha de la orden, ahora Instant
    private double total;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String paypalOrderId;
    private String manualTransferRefId;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    private String receiptFilename;
    private String adminApprovalUser;
    private Instant approvalDate; // <-- ¡CAMBIADO A INSTANT! Fecha de aprobación
    private String adminComment;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>(); // <-- ¡NOMBRE CONFIRMADO: 'items'!

    public Order() {
        this.creadoEn = Instant.now();
        this.total = 0.0;
        this.status = OrderStatus.PENDING; // Estado inicial por defecto
        this.paymentStatus = PaymentStatus.PENDING_PAYMENT; // Estado inicial de pago
    }

    // Asegúrate de que Lombok (@Data) genere los getters y setters correctos.
    // Si no estás usando Lombok o si hay algún problema con los getters/setters,
    // deberías generarlos manualmente o verificar la configuración de Lombok.

    // Método para agregar OrderItem (ya lo tienes, lo mantengo aquí por referencia)
    public void addOrderItem(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot add a null OrderItem.");
        }
        // Solo añade si no está ya en la lista para evitar duplicados lógicos
        if (!items.contains(item)) {
            items.add(item);
            item.setOrder(this); // Asegura la relación bidireccional
        }
    }

    // Método para remover OrderItem (ya lo tienes, lo mantengo aquí por referencia)
    public void removeOrderItem(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot remove a null OrderItem.");
        }
        if (items.remove(item)) { // remove() devuelve true si el elemento fue removido
            item.setOrder(null); // Rompe la relación bidireccional
        }
    }
}