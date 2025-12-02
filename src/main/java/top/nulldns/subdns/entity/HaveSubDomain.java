package top.nulldns.subdns.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "have_sub_domain")
public class HaveSubDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", referencedColumnName = "id", nullable = false)
    private Member member;

    @Column(nullable = false, unique = true)
    private String fullDomain;

    @Column(nullable = false)
    private String recordType;

    private String content;

    private LocalDateTime expiryDate;

    @Builder
    public HaveSubDomain(Member member, String fullDomain, String recordType, String content) {
        this.member = member;
        this.fullDomain = fullDomain;
        this.recordType = recordType;
        this.content = content;
        this.expiryDate = LocalDateTime.now().plusMonths(6); // 기본 6개월 설정
    }

    public void updateContent(String recordType, String content) {
        this.recordType = recordType;
        this.content = content;
    }

    public boolean isRenewable() {
        LocalDateTime oneMonthBeforeExpiry = this.expiryDate.minusMonths(1);
        return LocalDateTime.now().isAfter(oneMonthBeforeExpiry);
    }

    public void renewDate() {
        if (isRenewable()) {
            this.expiryDate = LocalDateTime.now().plusMonths(6);
        } else {
            throw new IllegalStateException("갱신 가능한 기간이 아닙니다.");
        }
    }
}
