package co.empresa.vivaeventos.payments.domain.repository;

import co.empresa.vivaeventos.payments.domain.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface IWebhookEventRepository extends JpaRepository<WebhookEvent, String> {

    @Transactional
    void deleteByCreatedAtBefore(Instant cutoffTime);
}