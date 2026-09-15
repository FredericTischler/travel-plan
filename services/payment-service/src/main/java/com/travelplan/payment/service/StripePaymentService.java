package com.travelplan.payment.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.travelplan.payment.dto.CreateStripePaymentRequest;
import com.travelplan.payment.dto.StripePaymentResponse;
import com.travelplan.payment.entity.Payment;
import com.travelplan.payment.entity.PaymentProvider;
import com.travelplan.payment.exception.PaymentProviderException;
import com.travelplan.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Creates Stripe PaymentIntents (docs/sujet.md §2 — Stripe support).
 *
 * <p><b>Scope actually delivered by this increment:</b> creating the
 * PaymentIntent and persisting a {@code PENDING} payment row with its id
 * (standard Stripe "PaymentIntent creation" step of the flow). The client is
 * expected to confirm the PaymentIntent itself (Stripe.js/Elements) using the
 * returned {@code client_secret} — that confirmation step happens entirely on
 * Stripe's side and on the client, not in this service.</p>
 *
 * <p><b>NOT implemented (explicitly out of scope here):</b> the
 * {@code payment_intent.succeeded} (and {@code .payment_failed}) webhook that
 * would let this service learn asynchronously that a PaymentIntent actually
 * completed and transition the corresponding {@code Payment} row to
 * {@code COMPLETED}/{@code FAILED} automatically. Today, a Stripe-originated
 * payment only ever leaves {@code PENDING} through the existing manual
 * {@code PATCH /payments/{id}/status} endpoint — there is no automatic
 * reconciliation with Stripe's own view of the PaymentIntent's state. A real
 * production integration needs that webhook (with signature verification via
 * Stripe's webhook signing secret) before this can be considered a complete
 * payment flow.</p>
 */
@Service
public class StripePaymentService {

    private final PaymentRepository paymentRepository;

    public StripePaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Create a Stripe PaymentIntent for the given amount/currency and persist
     * a new {@code PENDING} payment with {@code provider=STRIPE} and
     * {@code externalReference} set to the PaymentIntent id.
     *
     * @throws PaymentProviderException if the call to Stripe's API fails
     */
    @Transactional
    public StripePaymentResponse createPaymentIntent(CreateStripePaymentRequest request) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(toSmallestCurrencyUnit(request.getAmount()))
                .setCurrency(request.getCurrency().toLowerCase())
                .build();

        PaymentIntent paymentIntent;
        try {
            paymentIntent = PaymentIntent.create(params);
        } catch (StripeException ex) {
            throw new PaymentProviderException("Stripe", ex);
        }

        Payment payment = new Payment(request.getUserId(), request.getAmount(), request.getCurrency(),
                PaymentProvider.STRIPE, paymentIntent.getId());
        Payment saved = paymentRepository.save(payment);
        return StripePaymentResponse.from(saved, paymentIntent.getClientSecret());
    }

    /**
     * Converts a decimal amount to the integer count of the currency's
     * smallest unit that Stripe's API expects (e.g. 12.34 USD -> 1234 cents).
     *
     * <p><b>Known limitation:</b> this assumes a 2-decimal-place currency for
     * every code, matching the existing {@code CreateManualPaymentRequest}
     * validation ({@code amount} is a plain {@link BigDecimal}, no
     * currency-aware scale). Zero-decimal currencies (e.g. JPY) or
     * 3-decimal currencies (e.g. BHD) are not specifically handled — the same
     * simplification already accepted in V1__init.sql's design notes for the
     * manual payment path. A full ISO 4217 minor-unit table is out of scope
     * for this increment.</p>
     */
    private static long toSmallestCurrencyUnit(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }
}
