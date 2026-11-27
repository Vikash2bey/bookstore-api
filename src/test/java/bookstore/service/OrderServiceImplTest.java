package bookstore.service;

import com.bookstore.dto.OrderDto;
import com.bookstore.entity.*;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.InsufficientStockException;
import com.bookstore.mapper.OrderMapper;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.service.impl.OrderServiceImpl;
import com.bookstore.service.order.OrderReservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private UserRepository users;
    @Mock private CartRepository carts;
    @Mock private BookRepository books;
    @Mock private OrderRepository orders;
    @Mock private OrderMapper mapper;
    @Mock private OrderReservation reservation;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(users, carts, books, orders, mapper, reservation);
    }

    @Test
    void existingIdempotencyKeyReturnsExistingOrderWithoutReservingStock() {
        Order existing = new Order();
        OrderDto dto = mock(OrderDto.class);
        when(orders.findByUserIdAndIdempotencyKey(1L, "key")).thenReturn(Optional.of(existing));
        when(mapper.toDto(existing)).thenReturn(dto);

        assertThat(service.createOrder(1L, "key")).isSameAs(dto);
        verifyNoInteractions(books, carts, users, reservation);
    }

    @Test
    void emptyCartCannotBeSubmitted() {
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
        when(carts.findByUserId(1L)).thenReturn(Optional.of(new Cart()));

        assertThatThrownBy(() -> service.createOrder(1L, null)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(books, reservation);
    }

    @Test
    void insufficientStockDoesNotCreateOrder() {
        Book book = new Book();
        book.setId(3L);
        book.setStock(1);
        book.setTitle("Book");
        CartItem item = new CartItem();
        item.setBook(book);
        item.setQuantity(2);
        Cart cart = new Cart();
        cart.getItems().add(item);
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
        when(carts.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(books.findAllByIdForUpdate(List.of(3L))).thenReturn(List.of(book));
        when(reservation.newExpiry()).thenReturn(java.time.LocalDateTime.now().plusMinutes(15));

        assertThatThrownBy(() -> service.createOrder(1L, null)).isInstanceOf(InsufficientStockException.class);
        assertThat(book.getStock()).isEqualTo(1);
        verify(orders, never()).save(any());
    }
}
