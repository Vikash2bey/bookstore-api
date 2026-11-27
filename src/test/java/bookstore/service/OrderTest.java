package bookstore.service;

import com.bookstore.entity.Book;
import com.bookstore.entity.Order;
import com.bookstore.entity.OrderStatus;
import com.bookstore.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void addingItemsSnapshotsPricesAndCalculatesTotal() {
        Book first = new Book();
        first.setPrice(new BigDecimal("12.50"));
        Book second = new Book();
        second.setPrice(new BigDecimal("3.00"));
        User user = new User();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);
        Order order = new Order(user, "key", expiry);

        order.addItem(first, 2);
        order.addItem(second, 3);
        first.setPrice(new BigDecimal("20.00"));

        assertThat(order.getUser()).isSameAs(user);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getExpiresAt()).isEqualTo(expiry);
        assertThat(order.getIdempotencyKey()).isEqualTo("key");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("34.00");
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(order.getItems()).allSatisfy(item -> assertThat(item.getOrder()).isSameAs(order));
    }

    @Test
    void invalidQuantityDoesNotModifyOrder() {
        Order order = new Order(new User(), null, LocalDateTime.now().plusMinutes(15));

        assertThatThrownBy(() -> order.addItem(new Book(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getItems()).isEmpty();
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
