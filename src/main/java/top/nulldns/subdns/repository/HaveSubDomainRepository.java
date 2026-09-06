package top.nulldns.subdns.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import top.nulldns.subdns.config.finalconfig.Status;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dao.Member;

import java.time.LocalDate;
import java.util.List;

public interface HaveSubDomainRepository extends JpaRepository<HaveSubDomain, Long> {
    List<HaveSubDomain> findByMember(Member member);

    @Query("SELECT h FROM HaveSubDomain h WHERE h.member.id = :memberId GROUP BY h.fullDomain")
    List<HaveSubDomain> findDistinctByMemberId(Long memberId);

    @Query("""
    SELECT h FROM HaveSubDomain h
    WHERE h.expiryDate < :now
    ORDER BY h.id ASC
    """)
    List<HaveSubDomain> findExpired(@Param("now") LocalDate now, Pageable pageable);

    List<HaveSubDomain> findByDomainStatusAndExpiryDateGreaterThanEqual(Status status, LocalDate date);

    List<HaveSubDomain> findByMemberAndFullDomain(Member member, String fullDomain);

    List<HaveSubDomain> findByFullDomain(String fullDomain);

    @Query("""
        SELECT h FROM HaveSubDomain h
                WHERE h.fullDomain = :zone
                        OR h.fullDomain LIKE CONCAT('%.', :zone)
        """)
    List<HaveSubDomain> findByZoneIncludingFullDomain(String zone);

    @Query("select count(distinct h.fullDomain) from HaveSubDomain h where h.member.id = :memberId")
    int countDistinctFullDomainByMemberId(Long memberId);

    boolean existsByFullDomain(String fullDomain);

    boolean existsByFullDomainAndMember(String fullDomain, Member member);

    HaveSubDomain findByMemberAndFullDomainAndRecordType(Member member, String fullDomain, String recordType);

    List<HaveSubDomain> findByFullDomainContaining(String fullDomain);

    Page<HaveSubDomain> findAll(Pageable pageable);

    Page<HaveSubDomain> findByFullDomainContaining(String fullDomain, Pageable pageable);

    @Query("SELECT h FROM HaveSubDomain h WHERE h.member.id = :memberId")
    List<HaveSubDomain> findByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT COUNT(DISTINCT h.fullDomain) FROM HaveSubDomain h")
    long countTotalDomains();

    @Query("SELECT h.recordType, COUNT(h) FROM HaveSubDomain h GROUP BY h.recordType")
    List<Object[]> countByRecordType();
}
