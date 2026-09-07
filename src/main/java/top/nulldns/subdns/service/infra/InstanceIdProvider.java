package top.nulldns.subdns.service.infra;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.UUID;

@Slf4j
@Component
@Getter
public class InstanceIdProvider {
    private final String instanceId;

    public InstanceIdProvider() {
        this.instanceId = getLocalIpAddress();
    }

    private String getLocalIpAddress() {
        String publicIp = null;

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                // 루프백, 비활성, 가상 인터페이스 무시
                if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        if (address.isSiteLocalAddress()) {
                            // 사설 IP(Private IP) 발견 시 즉시 반환 (최우선)
                            return address.getHostAddress();
                        } else {
                            // 공인 IP(Public IP) 발견 시 임시 보관
                            if (publicIp == null) {
                                publicIp = address.getHostAddress();
                            }
                        }
                    }
                }
            }
            
            // 사설 IP가 없다면 임시 보관해둔 공인 IP 반환
            if (publicIp != null) {
                return publicIp;
            }
            
            // 둘 다 없다면 OS 기본 설정 시도
            return InetAddress.getLocalHost().getHostAddress();
            
        } catch (Exception e) {
            log.error("IP 주소를 가져오는데 실패했습니다. 임시로 UUID를 사용합니다.", e);
            return UUID.randomUUID().toString();
        }
    }
}
