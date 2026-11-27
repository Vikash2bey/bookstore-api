package bookstore.service;

import com.bookstore.dto.OrderDto;
import com.bookstore.dto.payment.PaymentRequest;
import com.bookstore.entity.Order;
import com.bookstore.entity.OrderStatus;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.mapper.OrderMapper;
import com.bookstore.repository.OrderRepository;
import com.bookstore.service.impl.PaymentServiceImpl;
import com.bookstore.service.order.OrderReservation;
import com.bookstore.service.order.OrderStockRestorer;
import com.bookstore.service.payment.PaymentAdapter;
import com.bookstore.service.payment.PaymentAdapterFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private OrderRepository orders;
    @Mock private OrderMapper mapper;
    @Mock private PaymentAdapterFactory adapters;
    @Mock private PaymentAdapter adapter;
    @Mock private OrderStockRestorer stock;
    @Mock private OrderReservation reservation;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(orders, mapper, adapters, stock, reservation);
    }

    @Test
    void successfulPaymentChargesOrderAndMarksItPaid() {
        Order order = pendingOrder();
        OrderDto result = mock(OrderDto.class);
        when(orders.findByIdAndUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(order));
        when(adapters.getAdapter("CARD")).thenReturn(adapter);
        when(adapter.pay(new PaymentRequest(2L, new BigDecimal("12.00")))).thenReturn(true);
        when(orders.save(order)).thenReturn(order);
        when(mapper.toDto(order)).thenReturn(result);

        assertThat(service.payForOrder(1L, 2L, "CARD")).isSameAs(result);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(stock);
    }

    @Test
    void rejectedPaymentRestoresStockAndFailsOrderOnce() {
        Order order = pendingOrder();
        when(orders.findByIdAndUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(order));
        when(adapters.getAdapter("CARD")).thenReturn(adapter);
        when(adapter.pay(any())).thenReturn(false);

        assertThatThrownBy(() -> service.payForOrder(1L, 2L, "CARD"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("rejected");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(stock, times(1)).restore(order);
        verify(orders, times(1)).save(order);
    }

    @Test
    void paidOrderIsNotChargedAgain() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID);
        when(orders.findByIdAndUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(order));

        service.payForOrder(1L, 2L, "CARD");

        verifyNoInteractions(adapters, stock, reservation);
        verify(orders, never()).save(any());
    }

    @Test
    void expiredOrderIsNotCharged() {
        Order order = pendingOrder();
        when(orders.findByIdAndUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(order));
        when(reservation.expireIfDue(order)).thenReturn(true);

        assertThatThrownBy(() -> service.payForOrder(1L, 2L, "CARD"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("expired");
        verifyNoInteractions(adapters);
    }

    @Test
    void unsupportedPaymentMethodFailsOrderAndRestoresStock() {
        Order order = pendingOrder();
        when(orders.findByIdAndUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(order));
        when(adapters.getAdapter("WIRE")).thenThrow(new BadRequestException("Unsupported payment method"));

        assertThatThrownBy(() -> service.payForOrder(1L, 2L, "WIRE"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Unsupported");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(stock).restore(order);
        verify(orders).save(order);
    }

    @Test
    void missingOrderCannotBePaid() {
        when(orders.findByIdAndUserIdForUpdate(2L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payForOrder(1L, 2L, "CARD"))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(adapters, stock, reservation);
    }

    private Order pendingOrder() {
        Order order = new Order();
        order.setId(2L);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("12.00"));
        return order;
    }
}
