package co.empresa.vivaeventos.payments;

import co.empresa.vivaeventos.payments.config.EventsClient;
import co.empresa.vivaeventos.payments.config.JwtUtil;
import co.empresa.vivaeventos.payments.config.NotificationsClient;
import co.empresa.vivaeventos.payments.config.OrdersClient;
import co.empresa.vivaeventos.payments.config.StripeConfig;
import co.empresa.vivaeventos.payments.config.TicketsClient;
import co.empresa.vivaeventos.payments.domain.repository.IPaymentRepository;
import co.empresa.vivaeventos.payments.domain.repository.IPromotionRepository;
import co.empresa.vivaeventos.payments.domain.repository.IWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
class VivaeventosPaymentsApplicationTest {

    @Autowired
    private VivaeventosPaymentsApplication application;

    @MockBean
    private IPaymentRepository paymentRepository;

    @MockBean
    private IWebhookEventRepository webhookEventRepository;

    @MockBean
    private IPromotionRepository promotionRepository;

    @MockBean
    private StripeConfig stripeConfig;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private EventsClient eventsClient;

    @MockBean
    private NotificationsClient notificationsClient;

    @MockBean
    private OrdersClient ordersClient;

    @MockBean
    private TicketsClient ticketsClient;

    //Hola

    @Test
    void contextLoads() {
        assertThat(application).isNotNull();
    }
}
