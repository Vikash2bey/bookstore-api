package com.bookstore.service;

import com.bookstore.dto.AddCartItemRequest;
import com.bookstore.dto.UpdateCartItemRequest;
import com.bookstore.entity.Book;
import com.bookstore.entity.Cart;
import com.bookstore.entity.CartItem;
import com.bookstore.entity.User;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.mapper.CartMapper;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock private CartRepository carts;
    @Mock private UserRepository users;
    @Mock private BookRepository books;
    @Mock private CartMapper mapper;
    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CartServiceImpl(carts, users, books, mapper);
    }

    @Test
    void addingExistingBookIncreasesQuantity() {
        User user = new User();
        user.setId(1L);
        Book book = new Book();
        book.setId(2L);
        book.setActive(true);
        Cart cart = new Cart();
        CartItem item = new CartItem();
        item.setBook(book);
        item.setQuantity(2);
        cart.getItems().add(item);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(books.findById(2L)).thenReturn(Optional.of(book));
        when(carts.findByUserId(1L)).thenReturn(Optional.of(cart));

        service.addItemCart(1L, new AddCartItemRequest(2L, 3));

        assertThat(item.getQuantity()).isEqualTo(5);
        verify(carts).save(cart);
        verify(mapper).toDto(cart);
    }

    @Test
    void inactiveBookCannotBeAdded() {
        User user = new User();
        Book book = new Book();
        book.setActive(false);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(books.findById(2L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.addItemCart(1L, new AddCartItemRequest(2L, 1)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(carts, mapper);
    }

    @Test
    void addingQuantityBeyondIntegerLimitIsRejected() {
        User user = new User();
        user.setId(1L);
        Book book = new Book();
        book.setId(2L);
        book.setActive(true);
        Cart cart = new Cart();
        CartItem item = new CartItem();
        item.setBook(book);
        item.setQuantity(Integer.MAX_VALUE);
        cart.getItems().add(item);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(books.findById(2L)).thenReturn(Optional.of(book));
        when(carts.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> service.addItemCart(1L, new AddCartItemRequest(2L, 1)))
                .isInstanceOf(BadRequestException.class);
        assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE);
        verify(carts, never()).save(any());
    }

    @Test
    void updatingMissingItemReturnsNotFound() {
        when(carts.findByUserId(1L)).thenReturn(Optional.of(new Cart()));

        assertThatThrownBy(() -> service.updateItemCart(1L, 2L, new UpdateCartItemRequest(1)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(carts, never()).save(any());
    }
}
