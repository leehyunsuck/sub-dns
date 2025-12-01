package top.nulldns.subdns.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider", "providerId"})
})
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider; // "GITHUB"

    @Column(nullable = false)
    private String providerId; // "12345678"

    @Column(nullable = false)
    private int maxRecords;

    @Builder
    public Member(String provider, String providerId) {
        this.provider = provider;
        this.providerId = providerId;
        this.maxRecords = 10;
    }
}