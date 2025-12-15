package top.nulldns.subdns.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

// ---------- 리팩토링 필요 -> subDoamin ,zoneName, fullDomain 을 다 name으로 만들어버렸음 (현재는 혼동 주의)
public class PDNSDto {
    @Data
    public static class AddRecordRequest {
        private String subDomain,
                       zone,
                       type,
                       content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResult {
        private String  name,       // full Domain
                        type,
                        content;
    }

    @Data
    @Builder
    public static class Rrset {
        private String  name,   // fullDomain
                        type;

        @Builder.Default
        private Integer ttl = 3600;

        @JsonProperty("changetype")
        private String changeType;

        private List<Record> records;
    }

    @Data
    @Builder
    public static class Record {
        private String content;

        @Builder.Default
        private boolean disabled = false;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZoneName {
        private String name;    // zone domain
    }

    @Data
    @Builder
    public static class CanAddSubDomainZones {
        private String subDomain;
        private List<ZoneAddCapability> zones;
    }

    @Data
    @Builder
    public static class ZoneAddCapability {
        private String name;    // zone domain
        private boolean canAdd;
    }
}
