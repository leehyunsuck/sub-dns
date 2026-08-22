package top.nulldns.subdns.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import top.nulldns.subdns.config.finalconfig.Status;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "have_sub_domain",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_member_full_domain_record_type",
                    columnNames = {"full_domain", "record_type"}
            )
        }
)
public class HaveSubDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", referencedColumnName = "id", nullable = false)
    private Member member;

    @Column(name = "full_domain", nullable = false)
    private String fullDomain;

    @Column(name = "record_type", nullable = false, length = 15)
    private String recordType;

    private String content;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain_status", length = 15)
    private Status domainStatus;

    public Long getMemberId() {
        return this.member.getId();
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void changeToActive() {
        this.domainStatus = Status.ACTIVE;
    }

    public void changeToUpdatePending() {
        this.domainStatus = Status.UPDATE_PENDING;
    }

    public void changeToDeletePending() {
        this.domainStatus = Status.DELETE_PENDING;
    }

    public boolean isRenewable() {
        LocalDate oneMonthBeforeExpiry = this.expiryDate.minusMonths(1);
        
        return LocalDate.now().isAfter(oneMonthBeforeExpiry);
    }

    public void renewDate(LocalDate maximumExpiryDate) {
        if (!isRenewable()) {
            return;
        }
        LocalDate plus6Month = this.expiryDate.plusMonths(6);
        this.expiryDate = plus6Month.isBefore(maximumExpiryDate) ? plus6Month : maximumExpiryDate;
    }
}
