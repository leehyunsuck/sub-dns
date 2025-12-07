package top.nulldns.subdns.dao;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "have_sub_domain")
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

    public void updateContent(String recordType, String content) {
        this.recordType = recordType;
        this.content = content;
    }

    public boolean isRenewable(LocalDate now) {
        LocalDate oneMonthBeforeExpiry = this.expiryDate.minusMonths(1);
        return now.isAfter(oneMonthBeforeExpiry);
    }

    public void renewDate(LocalDate now) {
        if (!isRenewable(now)) {
            throw new IllegalStateException("갱신 가능한 기간이 아닙니다 (만료 1달 전부터 가능).");
        }

        if (now.isAfter(this.expiryDate)) {
            this.expiryDate = now.plusMonths(6);
        } else {
            this.expiryDate = this.expiryDate.plusMonths(6);
        }
    }
}
