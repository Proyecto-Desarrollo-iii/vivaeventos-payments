package co.empresa.vivaeventos.payments.config;

import com.stripe.Stripe;
import com.stripe.net.Webhook;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class StripeConfig {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.webhook.secret}")
    private String stripeWebhookSecret;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public String getWebhookSecret() {
        return stripeWebhookSecret;
    }

    public boolean validateWebhookSignature(String payload, String signature) {
        try {
            Webhook.constructEvent(payload, signature, stripeWebhookSecret);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
