package co.empresa.vivaeventos.payments.domain.model.Dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @Positive(message = "Refund amount must be positive")
    private BigDecimal amount;

    private String reason;

}
