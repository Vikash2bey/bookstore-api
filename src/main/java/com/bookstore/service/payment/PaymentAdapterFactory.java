package com.bookstore.service.payment;

import com.bookstore.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PaymentAdapterFactory {

    private final Map<String, PaymentAdapter> adapters;

    public PaymentAdapterFactory(List<PaymentAdapter> paymentAdapters) {
        Map<String, PaymentAdapter> adaptersByMethod = new HashMap<>();
        paymentAdapters.forEach(adapter ->
                adaptersByMethod.put(adapter.getPaymentMethod().toUpperCase(Locale.ROOT), adapter));
        this.adapters = Map.copyOf(adaptersByMethod);
    }

    public PaymentAdapter getAdapter(String paymentMethod) {
        String normalized = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        PaymentAdapter adapter = adapters.get(normalized);
        if (adapter == null) {
            throw new BadRequestException("Unsupported payment method: " + paymentMethod);
        }
        return adapter;
    }
}
