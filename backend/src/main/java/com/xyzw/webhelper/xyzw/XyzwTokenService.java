package com.xyzw.webhelper.xyzw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class XyzwTokenService {
    private static final Logger logger = LoggerFactory.getLogger(XyzwTokenService.class);
    private static final String TOKEN_ENDPOINT = "https://xxz-xyzw.hortorgames.com/login/authuser";
    private final RestTemplate restTemplate = new RestTemplate();

    public byte[] fetchTokenBytes(byte[] payload) {
        if (payload == null || payload.length == 0) {
            logger.warn("Token request payload is empty");
            throw new IllegalArgumentException("bin\u6587\u4ef6\u4e3a\u7a7a");
        }

        String url = UriComponentsBuilder.fromHttpUrl(TOKEN_ENDPOINT)
            .queryParam("_seq", 1)
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        logger.debug("Requesting token from {} with payload size {}", url, payload.length);

        HttpEntity<byte[]> request = new HttpEntity<byte[]>(payload, headers);
        ResponseEntity<byte[]> response = restTemplate.postForEntity(url, request, byte[].class);

        logger.info("Token endpoint response status: {}", response.getStatusCodeValue());

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalArgumentException("闁兼儳鍢茶ぐ鍣抩ken濠㈡儼绮剧憴锕傛晬鐎涙壍TP闁绘鍩栭埀顑胯兌閻? " + response.getStatusCodeValue());
        }

        logger.debug("Token response size: {}", response.getBody().length);
        return response.getBody();
    }
}