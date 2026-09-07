package top.nulldns.subdns.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import top.nulldns.subdns.config.finalconfig.Status;

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

    @Builder.Default
    private boolean banned = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    public void setDeletePending() {
        this.status = Status.DELETE_PENDING;
    }
}