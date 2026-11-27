package bookstore.service;

import com.bookstore.entity.Order;
import com.bookstore.entity.OrderStatus;
import com.bookstore.service.order.OrderReservation;
import com.bookstore.service.order.OrderStockRestorer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderReservationTest {

    private final OrderStockRestorer stock = mock(OrderStockRestorer.class);
    private final OrderReservation reservation = new OrderReservation("PT15M", stock);

    @Test
    void overduePendingOrderExpiresAndRestoresStock() {
        Order order = order(OrderStatus.PENDING_PAYMENT, LocalDateTime.now().minusSeconds(1));

        assertThat(reservation.expireIfDue(order)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(stock).restore(order);
    }

    @Test
    void unexpiredOrPaidOrdersDoNotRestoreStock() {
        Order pending = order(OrderStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(1));
        Order paid = order(OrderStatus.PAID, LocalDateTime.now().minusMinutes(1));

        assertThat(reservation.expireIfDue(pending)).isFalse();
        assertThat(reservation.expireIfDue(paid)).isFalse();
        verifyNoInteractions(stock);
    }

    @Test
    void legacyOrderUsesCreationTimeAndConfiguredDuration() {
        Order order = order(OrderStatus.PENDING_PAYMENT, null);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(16));

        assertThat(reservation.expireIfDue(order)).isTrue();
        verify(stock).restore(order);
    }

    @Test
    void reservationDurationMustBePositive() {
        assertThatThrownBy(() -> new OrderReservation("PT0S", stock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Order order(OrderStatus status, LocalDateTime expiry) {
        Order order = new Order();
        order.setStatus(status);
        order.setExpiresAt(expiry);
        return order;
    }
}
