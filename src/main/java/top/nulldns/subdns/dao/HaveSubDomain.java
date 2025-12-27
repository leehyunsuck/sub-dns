package top.nulldns.subdns.dao;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "record_type", nullable = false)
    private String recordType;

    private String content;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @PrePersist
    public void prePersist() {
        if (expiryDate == null) {
            expiryDate = LocalDate.now().plusMonths(6);
        }
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
