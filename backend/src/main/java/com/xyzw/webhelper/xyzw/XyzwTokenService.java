package com.xyzw.webhelper.xyzw;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyzw.webhelper.xyzw.dto.XyzwTokenRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwTokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class XyzwTokenService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public XyzwTokenResponse resolveToken(XyzwTokenRequest request) {
        String token = request.getToken();

        if (!StringUtils.hasText(token) && StringUtils.hasText(request.getTokenUrl())) {
            token = fetchTokenFromUrl(request.getTokenUrl());
        }

        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("token字段为空");
        }

        return new XyzwTokenResponse(
            request.getName(),
            token,
            request.getServer(),
            request.getWsUrl()
        );
    }

    private String fetchTokenFromUrl(String tokenUrl) {
        ResponseEntity<String> response = restTemplate.getForEntity(tokenUrl, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalArgumentException("获取token失败，HTTP状态码: " + response.getStatusCodeValue());
        }

        try {
            Map<?, ?> payload = objectMapper.readValue(response.getBody(), Map.class);
            Object token = payload.get("token");
            if (token == null) {
                throw new IllegalArgumentException("响应中未找到token字段");
            }
            return token.toString();
        } catch (Exception error) {
            throw new IllegalArgumentException("解析token响应失败: " + error.getMessage(), error);
        }
    }
}
