package com.bookstore.service;

import com.bookstore.exception.BadRequestException;
import com.bookstore.service.payment.PaymentAdapter;
import com.bookstore.service.payment.PaymentAdapterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentAdapterFactoryTest {

    @Test
    void selectsAdapterIgnoringCaseAndWhitespace() {
        PaymentAdapter adapter = mock(PaymentAdapter.class);
        when(adapter.getPaymentMethod()).thenReturn("CARD");
        PaymentAdapterFactory factory = new PaymentAdapterFactory(List.of(adapter));

        assertThat(factory.getAdapter(" card ")).isSameAs(adapter);
    }

    @Test
    void unsupportedMethodIsRejected() {
        PaymentAdapterFactory factory = new PaymentAdapterFactory(List.of());

        assertThatThrownBy(() -> factory.getAdapter("WIRE"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Unsupported");
    }
}
