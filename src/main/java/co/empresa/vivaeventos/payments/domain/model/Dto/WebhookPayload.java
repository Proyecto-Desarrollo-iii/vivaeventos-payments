package co.empresa.vivaeventos.payments.domain.model.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayload {

    private String eventId;
    private String eventType;
    private String stripeId;
    private String paymentIntentId;
    private String chargeId;
    private String status;
    private String failureReason;
    private Long amount;
    private String currency;

}
