package com.xyzw.webhelper.xyzw;

import com.xyzw.webhelper.auth.AuthService;
import com.xyzw.webhelper.auth.dto.AuthResponse;
import com.xyzw.webhelper.xyzw.dto.XyzwBinResponse;
import com.xyzw.webhelper.xyzw.dto.XyzwTokenCreateRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwTokenRecordResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/xyzw")
public class XyzwProfileController {
    private static final Logger LOGGER = LoggerFactory.getLogger(XyzwProfileController.class);
    private final AuthService authService;
    private final XyzwProfileService profileService;
    private final XyzwUserBinMapper binMapper;

    public XyzwProfileController(
        AuthService authService,
        XyzwProfileService profileService,
        XyzwUserBinMapper binMapper
    ) {
        this.authService = authService;
        this.profileService = profileService;
        this.binMapper = binMapper;
    }

    @PostMapping(value = "/bins", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadBin(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @RequestPart("file") MultipartFile file,
        @RequestParam(value = "name", required = false) String name,
        @RequestParam(value = "remark", required = false) String remark
    ) throws IOException {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            LOGGER.warn("uploadBin unauthorized");
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        if (file == null || file.isEmpty()) {
            LOGGER.warn("uploadBin missing file for userId={}", user.getId());
            return ResponseEntity.badRequest().body(build(false, "bin file required", null));
        }
        if (name == null || name.trim().isEmpty()) {
            LOGGER.warn("uploadBin missing name userId={}", user.getId());
            return ResponseEntity.badRequest().body(build(false, "bin name required", null));
        }
        LOGGER.info("uploadBin start userId={} fileName={} size={}", user.getId(), file.getOriginalFilename(), file.getSize());
        XyzwUserBin saved = profileService.saveBin(
            user.getId(),
            name,
            remark,
            file.getOriginalFilename(),
            file.getBytes()
        );
        LOGGER.info("uploadBin success userId={} binId={}", user.getId(), saved.getId());
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("id", saved.getId());
        data.put("name", saved.getName());
        data.put("filePath", saved.getFilePath());
        data.put("remark", saved.getRemark());
        return ResponseEntity.ok(build(true, "success", data));
    }

    @GetMapping("/bins")
    public ResponseEntity<Map<String, Object>> listBins(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            LOGGER.warn("listBins unauthorized");
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        LOGGER.info("listBins userId={}", user.getId());
        List<XyzwBinResponse> bins = profileService.listBins(user.getId());
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("bins", bins);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/bins/{binId}/token")
    public ResponseEntity<Map<String, Object>> createToken(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @PathVariable("binId") Long binId,
        @RequestBody(required = false) XyzwTokenCreateRequest request
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            LOGGER.warn("createToken unauthorized binId={}", binId);
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        try {
            LOGGER.info("createToken start userId={} binId={}", user.getId(), binId);
            XyzwTokenRecordResponse response = profileService.createToken(user.getId(), binId, request);
            LOGGER.info("createToken success userId={} binId={} tokenId={}", user.getId(), binId, response.getId());
            return ResponseEntity.ok(build(true, "success", response));
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("createToken failed userId={} binId={} reason={}", user.getId(), binId, ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }
    }

    @PostMapping("/bins/{binId}/token/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @PathVariable("binId") Long binId
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            LOGGER.warn("refreshToken unauthorized binId={}", binId);
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        try {
            LOGGER.info("refreshToken start userId={} binId={}", user.getId(), binId);
            XyzwTokenRecordResponse response = profileService.refreshToken(user.getId(), binId);
            LOGGER.info("refreshToken success userId={} binId={} tokenId={}", user.getId(), binId, response.getId());
            return ResponseEntity.ok(build(true, "success", response));
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("refreshToken failed userId={} binId={} reason={}", user.getId(), binId, ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }
    }

    @GetMapping("/tokens/{tokenId}")
    public ResponseEntity<Map<String, Object>> getToken(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @PathVariable("tokenId") String tokenId
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            LOGGER.warn("getToken unauthorized tokenId={}", tokenId);
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        LOGGER.info("getToken userId={} tokenId={}", user.getId(), tokenId);
        XyzwTokenRecordResponse token = profileService.getToken(user.getId(), tokenId);
        if (token == null) {
            LOGGER.warn("getToken not found userId={} tokenId={}", user.getId(), tokenId);
            return ResponseEntity.status(404).body(build(false, "token not found", null));
        }
        XyzwUserBin bin = binMapper.findByIdAndUserId(token.getBinId(), user.getId());
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("token", token);
        if (bin != null) {
            data.put("bin", binInfo(bin));
        }
        return ResponseEntity.ok(build(true, "success", data));
    }

    @DeleteMapping("/bins/{binId}")
    public ResponseEntity<Map<String, Object>> deleteBin(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @PathVariable("binId") Long binId
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            LOGGER.warn("deleteBin unauthorized binId={}", binId);
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        try {
            LOGGER.info("deleteBin start userId={} binId={}", user.getId(), binId);
            profileService.deleteBin(user.getId(), binId);
            LOGGER.info("deleteBin success userId={} binId={}", user.getId(), binId);
            return ResponseEntity.ok(build(true, "success", null));
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("deleteBin failed userId={} binId={} reason={}", user.getId(), binId, ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }
    }

    @DeleteMapping("/tokens/{tokenId}")
    public ResponseEntity<Map<String, Object>> deleteToken(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @PathVariable("tokenId") Long tokenId
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            LOGGER.warn("deleteToken unauthorized tokenId={}", tokenId);
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        try {
            LOGGER.info("deleteToken start userId={} tokenId={}", user.getId(), tokenId);
            profileService.deleteToken(user.getId(), tokenId);
            LOGGER.info("deleteToken success userId={} tokenId={}", user.getId(), tokenId);
            return ResponseEntity.ok(build(true, "success", null));
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("deleteToken failed userId={} tokenId={} reason={}", user.getId(), tokenId, ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }
    }

    private AuthResponse resolveUser(String authHeader) {
        String token = extractToken(authHeader);
        return authService.getUserByToken(token);
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.trim().isEmpty()) {
            return null;
        }
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }

    private Map<String, Object> binInfo(XyzwUserBin bin) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("id", bin.getId());
        data.put("name", bin.getName());
        data.put("filePath", bin.getFilePath());
        data.put("remark", bin.getRemark());
        data.put("createdAt", bin.getCreatedAt());
        return data;
    }

    private Map<String, Object> build(boolean success, String message, Object data) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("success", success);
        payload.put("message", message);
        if (data != null) {
            payload.put("data", data);
        }
        return payload;
    }
}
