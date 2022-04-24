package com.proxy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "server")
@Data
public class ServerConfig {
    private String type;
    private String serverIp;
    private int serverPort;
    private String backendIp;
    private int backendPort;
    private BackendThreadModel backendThreadModel;
    private int receiveBuffer;
    private int sendBuffer;
    private AllocatorType allocatorType;
    private int maxContentLength;
    private int workThreadNums ;
}
