package top.nulldns.subdns.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.nulldns.subdns.dao.Member;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByProviderAndProviderId(String provider, String providerId);
}
