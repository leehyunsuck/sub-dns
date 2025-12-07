package top.nulldns.subdns.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import top.nulldns.subdns.dao.HaveSubDomain;

import java.util.List;
import java.util.Optional;

public interface HaveSubDomainRepository extends JpaRepository<HaveSubDomain, Long> {
    List<HaveSubDomain> findByMemberId(Long memberId);

    @Query("SELECT h FROM HaveSubDomain h WHERE h.member.id = :memberId GROUP BY h.fullDomain")
    List<HaveSubDomain> findDistinctByMemberId(Long memberId);

    List<HaveSubDomain> findAllByMemberIdAndFullDomain(Long memberId, String fullDomain);
    Optional<HaveSubDomain> findByMemberIdAndFullDomainAndRecordType(Long memberId, String fullDomain, String recordType);

    @Query("select count(distinct h.fullDomain) from HaveSubDomain h where h.member.id = :memberId")
    int countDistinctFullDomainByMemberId(Long memberId);

    boolean existsHaveSubDomainByMemberIdAndFullDomain(Long memberId, String fullDomain);

    boolean existsByFullDomain(String fullDomain);

}
