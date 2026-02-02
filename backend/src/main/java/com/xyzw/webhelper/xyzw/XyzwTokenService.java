package com.xyzw.webhelper.xyzw;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class XyzwTokenService {
    private static final String TOKEN_ENDPOINT = "https://xxz-xyzw.hortorgames.com/login/authuser";
    private final RestTemplate restTemplate = new RestTemplate();

    public byte[] fetchTokenBytes(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("bin文件为空");
        }

        String url = UriComponentsBuilder.fromHttpUrl(TOKEN_ENDPOINT)
            .queryParam("_seq", 1)
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        HttpEntity<byte[]> request = new HttpEntity<byte[]>(payload, headers);
        ResponseEntity<byte[]> response = restTemplate.postForEntity(url, request, byte[].class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalArgumentException("获取token失败，HTTP状态码: " + response.getStatusCodeValue());
        }

        return response.getBody();
    }
}
