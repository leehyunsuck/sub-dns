package top.nulldns.subdns.dto;

import top.nulldns.subdns.config.finalconfig.Status;

public record AdminMemberDto(
    Long id,
    String provider,
    String providerId,
    int maxRecords,
    boolean banned,
    Status status
) {
}
