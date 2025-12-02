package top.nulldns.subdns.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultMessageDTO<T> {
    private boolean pass;
    private String message;
    private T data;
}
