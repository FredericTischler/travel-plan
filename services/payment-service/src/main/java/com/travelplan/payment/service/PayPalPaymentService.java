package com.travelplan.payment.service;

import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.exceptions.ApiException;
import com.paypal.sdk.http.response.ApiResponse;
import io.apimatic.core.exceptions.AuthValidationException;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.CreateOrderInput;
import com.paypal.sdk.models.LinkDescription;
import com.paypal.sdk.models.Order;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.PurchaseUnitRequest;
import com.travelplan.payment.dto.CreatePayPalPaymentRequest;
import com.travelplan.payment.dto.PayPalPaymentResponse;
import com.travelplan.payment.entity.Payment;
import com.travelplan.payment.entity.PaymentProvider;
import com.travelplan.payment.exception.PaymentProviderException;
import com.travelplan.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.List;

/**
 * Creates PayPal Orders (docs/sujet.md §2 — PayPal support).
 *
 * <p><b>Scope actually delivered by this increment:</b> creating a PayPal
 * Order (intent {@code CAPTURE}) and persisting a {@code PENDING} payment row
 * with its id (standard PayPal Orders v2 "create order" step). The response
 * includes the order's PayPal-hosted approval URL — the client is expected to
 * redirect the payer there to approve the order themselves.</p>
 *
 * <p><b>NOT implemented (explicitly out of scope here):</b> the capture step
 * that must happen after the payer approves the order on PayPal's side
 * (normally: PayPal redirects back to a return URL, and the server then
 * calls PayPal's capture endpoint to actually move the funds), and there is
 * no webhook handling for PayPal's {@code CHECKOUT.ORDER.APPROVED}/
 * {@code PAYMENT.CAPTURE.COMPLETED} events either. Today, a PayPal-originated
 * payment only ever leaves {@code PENDING} through the existing manual
 * {@code PATCH /payments/{id}/status} endpoint — there is no automatic
 * reconciliation with PayPal's own view of the order's state. A real
 * production integration needs the capture call plus webhook verification
 * before this can be considered a complete payment flow.</p>
 */
@Service
public class PayPalPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaypalServerSdkClient paypalServerSdkClient;

    public PayPalPaymentService(PaymentRepository paymentRepository, PaypalServerSdkClient paypalServerSdkClient) {
        this.paymentRepository = paymentRepository;
        this.paypalServerSdkClient = paypalServerSdkClient;
    }

    /**
     * Create a PayPal Order for the given amount/currency and persist a new
     * {@code PENDING} payment with {@code provider=PAYPAL} and
     * {@code externalReference} set to the Order id.
     *
     * @throws PaymentProviderException if the call to PayPal's API fails
     */
    @Transactional
    public PayPalPaymentResponse createOrder(CreatePayPalPaymentRequest request) {
        AmountWithBreakdown amount = new AmountWithBreakdown.Builder(
                request.getCurrency(),
                request.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .build();
        PurchaseUnitRequest purchaseUnit = new PurchaseUnitRequest.Builder(amount).build();
        OrderRequest orderRequest = new OrderRequest.Builder(CheckoutPaymentIntent.CAPTURE, List.of(purchaseUnit))
                .build();
        CreateOrderInput input = new CreateOrderInput.Builder("application/json", orderRequest).build();

        Order order;
        try {
            ApiResponse<Order> response = paypalServerSdkClient.getOrdersController().createOrder(input);
            order = response.getResult();
        } catch (ApiException | IOException | AuthValidationException ex) {
            // AuthValidationException is thrown by the SDK's own client-side
            // credential check (io.apimatic ClientCredentialsAuth), BEFORE any
            // network call — invalid/placeholder PAYPAL_CLIENT_ID/SECRET land
            // here, not in ApiException. Without this branch it escapes as an
            // unhandled 500 instead of the intended 502 PaymentProviderException.
            throw new PaymentProviderException("PayPal", ex);
        }

        String approveUrl = order.getLinks() == null ? null : order.getLinks().stream()
                .filter(link -> "approve".equals(link.getRel()))
                .map(LinkDescription::getHref)
                .findFirst()
                .orElse(null);

        Payment payment = new Payment(request.getUserId(), request.getAmount(), request.getCurrency(),
                PaymentProvider.PAYPAL, order.getId());
        Payment saved = paymentRepository.save(payment);
        return PayPalPaymentResponse.from(saved, approveUrl);
    }
}
