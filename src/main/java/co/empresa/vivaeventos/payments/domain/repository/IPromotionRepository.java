package co.empresa.vivaeventos.payments.domain.repository;

import co.empresa.vivaeventos.payments.domain.model.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IPromotionRepository extends JpaRepository<Promotion, UUID> {
}
