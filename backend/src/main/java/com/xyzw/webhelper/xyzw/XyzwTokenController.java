package com.xyzw.webhelper.xyzw;

import com.xyzw.webhelper.xyzw.dto.XyzwTokenRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwTokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/xyzw")
public class XyzwTokenController {
    private final XyzwTokenService tokenService;

    public XyzwTokenController(XyzwTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> fetchToken(@RequestBody XyzwTokenRequest request) {
        XyzwTokenResponse tokenResponse = tokenService.resolveToken(request);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("success", true);
        payload.put("data", tokenResponse);
        payload.put("message", "success");
        return ResponseEntity.ok(payload);
    }
}
