package top.nulldns.subdns.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.nulldns.subdns.dao.HaveSubDomain;

import java.time.LocalDate;
import java.util.List;

public interface HaveSubDomainRepository extends JpaRepository<HaveSubDomain, Long> {
    List<HaveSubDomain> findByMemberId(Long memberId);

    @Query("SELECT h FROM HaveSubDomain h WHERE h.member.id = :memberId GROUP BY h.fullDomain")
    List<HaveSubDomain> findDistinctByMemberId(Long memberId);

    List<HaveSubDomain> findByExpiryDateBefore(LocalDate date);

    List<HaveSubDomain> findByMemberIdAndFullDomain(Long memberId, String fullDomain);

    @Query("select count(distinct h.fullDomain) from HaveSubDomain h where h.member.id = :memberId")
    int countDistinctFullDomainByMemberId(Long memberId);

    boolean existsByFullDomain(String fullDomain);

    boolean existsByFullDomainAndMemberId(String fullDomain, Long memberId);

    void deleteByMemberIdAndFullDomainAndRecordType(Long memberId, String fullDomain, String recordType);
    void deleteAllByMemberId(Long memberId);

    @Modifying
    @Query("""
    DELETE FROM HaveSubDomain h
    WHERE h.fullDomain = :domain
       OR h.fullDomain LIKE CONCAT('%.', :domain)
    """)
    int deleteByDomain(@Param("domain") String domain);

    HaveSubDomain findByMemberIdAndFullDomainAndRecordType(Long memberId, String fullDomain, String recordType);
}
