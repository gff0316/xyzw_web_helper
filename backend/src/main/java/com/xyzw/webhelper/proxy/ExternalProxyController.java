package com.xyzw.webhelper.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

@RestController
public class ExternalProxyController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalProxyController.class);

    private static final String WECHAT_QR_BASE = "https://open.weixin.qq.com/connect/app/qrconnect";
    private static final String WECHAT_SCAN_BASE = "https://long.open.weixin.qq.com/connect/l/qrconnect";
    private static final String HORTOR_LOGIN_BASE = "https://comb-platform.hortorgames.com/comb-login-server/api/v1/login";

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/api/weixin/connect/app/qrconnect")
    public ResponseEntity<byte[]> proxyWeixinQr(HttpServletRequest request) {
        long start = System.currentTimeMillis();
        return proxyGet(WECHAT_QR_BASE, request);
    }

    @GetMapping("/api/weixin/connect/l/qrconnect")
    public ResponseEntity<byte[]> proxyWeixinScan(HttpServletRequest request) {
        long start = System.currentTimeMillis();
        return proxyGet(WECHAT_SCAN_BASE, request);
    }

    @PostMapping("/api/hortor/comb-login-server/api/v1/login")
    public ResponseEntity<byte[]> proxyHortorLogin(
        HttpServletRequest request,
        @RequestBody(required = false) byte[] body
    ) {
        long start = System.currentTimeMillis();
        String url = buildUrlWithQuery(HORTOR_LOGIN_BASE, request);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setAccept(MediaType.parseMediaTypes("*/*"));
        headers.set(HttpHeaders.USER_AGENT,
            "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 " +
                "MicroMessenger/8.0.36(0x1800243e) NetType/WIFI Language/zh_CN");
        HttpEntity<byte[]> entity = new HttpEntity<byte[]>(body == null ? new byte[0] : body, headers);
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);
            LOGGER.info("proxyHortorLogin status={} costMs={} url={}", response.getStatusCodeValue(), System.currentTimeMillis() - start, url);
            return buildResponse(response);
        } catch (Exception ex) {
            LOGGER.warn("proxyHortorLogin failed costMs={} url={}", System.currentTimeMillis() - start, url, ex);
            return ResponseEntity.status(502).body(errorBody("proxy login failed"));
        }
    }

    @GetMapping("/api/weixin/callback")
    public ResponseEntity<byte[]> weixinCallback(HttpServletRequest request) {
        String code = request.getParameter("code");
        String state = request.getParameter("state");
        LOGGER.info("weixinCallback code={} state={}", code, state);
        String html =
            "<!DOCTYPE html><html><head><meta charset='utf-8'/>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1'/>" +
            "<title>微信登录</title></head><body style='font-family: sans-serif;'>" +
            "<div style='padding:24px;'><h3>微信登录已完成</h3>" +
            "<p>你可以关闭此页面并返回应用。</p></div></body></html>";
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> proxyGet(String baseUrl, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        String url = buildUrlWithQuery(baseUrl, request);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(MediaType.parseMediaTypes("*/*"));
        headers.set(HttpHeaders.USER_AGENT,
            "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 " +
                "MicroMessenger/8.0.36(0x1800243e) NetType/WIFI Language/zh_CN");
        headers.set("Referer", "https://open.weixin.qq.com/");
        HttpEntity<Void> entity = new HttpEntity<Void>(headers);
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            LOGGER.info("proxyGet status={} costMs={} url={}", response.getStatusCodeValue(), System.currentTimeMillis() - start, url);
            return buildResponse(response);
        } catch (Exception ex) {
            LOGGER.warn("proxyGet failed costMs={} url={}", System.currentTimeMillis() - start, url, ex);
            return ResponseEntity.status(502).body(errorBody("proxy get failed"));
        }
    }

    private String buildUrlWithQuery(String baseUrl, HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            return baseUrl;
        }
        return baseUrl + "?" + query;
    }

    private ResponseEntity<byte[]> buildResponse(ResponseEntity<byte[]> response) {
        HttpHeaders headers = new HttpHeaders();
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        return new ResponseEntity<byte[]>(response.getBody(), headers, response.getStatusCode());
    }

    private byte[] errorBody(String message) {
        return message.getBytes(StandardCharsets.UTF_8);
    }
}
