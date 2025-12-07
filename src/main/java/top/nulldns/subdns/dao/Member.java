package top.nulldns.subdns.dao;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "members",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_provider_provider_id",
                    columnNames = {"provider", "provider_id"}
            )
        })
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String provider; // "GITHUB"

    @Column(name = "provider_id", nullable = false)
    private String providerId; // "12345678"

    @Builder.Default
    private int maxRecords = 10;
}