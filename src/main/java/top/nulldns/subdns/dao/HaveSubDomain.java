package top.nulldns.subdns.dao;

import jakarta.persistence.*;
import lombok.*;
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

    @PrePersist
    public void prePersist() {
        if (this.expiryDate == null) {
            this.expiryDate = LocalDate.now().plusMonths(6);
        }
    }

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

    public void renewDate() {
        if (!isRenewable()) {
            return;
        }
        this.expiryDate = this.expiryDate.plusMonths(6);
    }
}
