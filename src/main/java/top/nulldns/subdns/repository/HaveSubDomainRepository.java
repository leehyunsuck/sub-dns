package top.nulldns.subdns.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.nulldns.subdns.entity.HaveSubDomain;

import java.util.List;
import java.util.Optional;

public interface HaveSubDomainRepository extends JpaRepository<HaveSubDomain, Long> {
    List<HaveSubDomain> findByMemberId(Long memberId);
}
