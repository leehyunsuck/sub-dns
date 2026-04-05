package top.nulldns.subdns.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.nulldns.subdns.config.finalconfig.Status;
import top.nulldns.subdns.dao.Member;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByProviderAndProviderId(String provider, String providerId);

    List<Member> findByStatus(Status status);
}
