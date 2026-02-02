package com.xyzw.webhelper.xyzw;

import com.xyzw.webhelper.xyzw.dto.XyzwTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/xyzw")
public class XyzwTokenController {
    private static final Logger logger = LoggerFactory.getLogger(XyzwTokenController.class);

    private final XyzwTokenService tokenService;

    public XyzwTokenController(XyzwTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping(value = "/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> fetchToken(
        @RequestPart("file") MultipartFile file,
        @RequestParam(value = "name", required = false) String name,
        @RequestParam(value = "server", required = false) String server,
        @RequestParam(value = "wsUrl", required = false) String wsUrl
    ) throws IOException {
        long fileSize = file == null ? 0L : file.getSize();
        logger.info("Incoming token request: name={}, server={}, wsUrl={}, fileSize={}", name, server, wsUrl, fileSize);

        byte[] tokenBytes = tokenService.fetchTokenBytes(file.getBytes());
        String encodedToken = Base64.getEncoder().encodeToString(tokenBytes);
        XyzwTokenResponse tokenResponse = new XyzwTokenResponse(name, encodedToken, server, wsUrl);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("success", true);
        payload.put("data", tokenResponse);
        payload.put("message", "success");

        logger.info("Token request success: name={}, server={}, wsUrl={}, tokenBytes={}", name, server, wsUrl, tokenBytes.length);
        return ResponseEntity.ok(payload);
    }
}
