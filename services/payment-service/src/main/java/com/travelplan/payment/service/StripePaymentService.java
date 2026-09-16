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
import java.util.Currency;

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
                .setAmount(toSmallestCurrencyUnit(request.getAmount(), request.getCurrency()))
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
     * smallest unit that Stripe's API expects (e.g. 12.34 USD -> 1234 cents,
     * 1000 JPY -> 1000, 12.345 BHD -> 12345).
     *
     * <p>Uses {@link Currency#getDefaultFractionDigits()} (ISO 4217) rather
     * than assuming 2 decimals for every currency — a fixed x100 previously
     * silently produced a charge 100x too large for zero-decimal currencies
     * like JPY instead of erroring.</p>
     *
     * @throws PaymentProviderException if the currency code isn't a valid
     *     ISO 4217 code Stripe/the JVM recognizes
     */
    private static long toSmallestCurrencyUnit(BigDecimal amount, String currencyCode) {
        int fractionDigits;
        try {
            fractionDigits = Currency.getInstance(currencyCode.toUpperCase()).getDefaultFractionDigits();
        } catch (IllegalArgumentException ex) {
            throw new PaymentProviderException("Stripe", ex);
        }
        if (fractionDigits < 0) {
            // Pseudo-currencies (e.g. XXX) report -1 fraction digits; Stripe
            // doesn't support them, fail fast instead of guessing a scale.
            throw new PaymentProviderException("Stripe",
                    new IllegalArgumentException("Currency " + currencyCode + " has no defined minor unit"));
        }
        return amount.setScale(fractionDigits, RoundingMode.HALF_UP)
                .movePointRight(fractionDigits)
                .longValueExact();
    }
}
