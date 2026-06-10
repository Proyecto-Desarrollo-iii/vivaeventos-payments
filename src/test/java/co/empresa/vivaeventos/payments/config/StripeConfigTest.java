package co.empresa.vivaeventos.payments.config;

import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StripeConfig.class)
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@TestPropertySource(properties = {
    "stripe.api.key=sk_test_test",
    "stripe.webhook.secret=whsec_test"
})
class StripeConfigTest {

    @Test
    void validateWebhookSignature_withInvalidSignature_returnsFalse() {
        StripeConfig config = new StripeConfig();
        java.lang.reflect.Field apiField;
        java.lang.reflect.Field webhookField;
        try {
            apiField = StripeConfig.class.getDeclaredField("stripeApiKey");
            apiField.setAccessible(true);
            apiField.set(config, "sk_test_test");

            webhookField = StripeConfig.class.getDeclaredField("stripeWebhookSecret");
            webhookField.setAccessible(true);
            webhookField.set(config, "whsec_test");

            config.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThat(config.validateWebhookSignature("payload", "invalid_sig")).isFalse();
    }

    @Test
    void getWebhookSecret_returnsSecret() {
        StripeConfig config = new StripeConfig();
        java.lang.reflect.Field field;
        try {
            field = StripeConfig.class.getDeclaredField("stripeWebhookSecret");
            field.setAccessible(true);
            field.set(config, "whsec_test_value");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThat(config.getWebhookSecret()).isEqualTo("whsec_test_value");
    }
}
