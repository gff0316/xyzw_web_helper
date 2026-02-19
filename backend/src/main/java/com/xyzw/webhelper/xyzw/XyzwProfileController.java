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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/xyzw")
public class XyzwProfileController {
    private static final Logger LOGGER = LoggerFactory.getLogger(XyzwProfileController.class);
    private final AuthService authService;
    private final XyzwProfileService profileService;
    private final XyzwUserBinMapper binMapper;
    private final XyzwUserTokenMapper tokenMapper;
    private final XyzwTaskLogService taskLogService;

    public XyzwProfileController(
        AuthService authService,
        XyzwProfileService profileService,
        XyzwUserBinMapper binMapper,
        XyzwUserTokenMapper tokenMapper,
        XyzwTaskLogService taskLogService
    ) {
        this.authService = authService;
        this.profileService = profileService;
        this.binMapper = binMapper;
        this.tokenMapper = tokenMapper;
        this.taskLogService = taskLogService;
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

    @GetMapping("/task-logs")
    public ResponseEntity<Map<String, Object>> listTaskLogs(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @RequestParam(value = "tokenId", required = false) Long tokenId,
        @RequestParam(value = "type", required = false) Integer type,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            LOGGER.warn("listTaskLogs unauthorized");
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        if (type != null
            && type != XyzwTaskLogService.TYPE_SCHEDULED_TASK
            && type != XyzwTaskLogService.TYPE_MANUAL_TASK
            && type != XyzwTaskLogService.TYPE_COMMAND) {
            return ResponseEntity.badRequest().body(build(false, "type 仅支持 0、1、2", null));
        }

        List<XyzwUserToken> userTokens = tokenMapper.findByUserId(user.getId());
        if (userTokens == null) {
            userTokens = new ArrayList<XyzwUserToken>();
        }
        Map<Long, TokenMeta> idToMeta = new HashMap<Long, TokenMeta>();
        Map<String, TokenMeta> keyToMeta = new HashMap<String, TokenMeta>();
        Set<String> allowedKeys = new LinkedHashSet<String>();
        List<Map<String, Object>> tokenOptions = new ArrayList<Map<String, Object>>();

        for (XyzwUserToken token : userTokens) {
            if (token == null || token.getId() == null) {
                continue;
            }
            String tokenKey = taskLogService.buildTokenKey(token.getToken());
            if (tokenKey == null || tokenKey.trim().isEmpty()) {
                continue;
            }
            TokenMeta meta = new TokenMeta();
            meta.tokenId = token.getId();
            meta.tokenName = normalizeTokenName(token);
            meta.tokenKey = tokenKey;
            meta.roleId = taskLogService.extractRoleId(token.getToken());

            idToMeta.put(meta.tokenId, meta);
            keyToMeta.put(meta.tokenKey, meta);
            allowedKeys.add(meta.tokenKey);

            Map<String, Object> option = new LinkedHashMap<String, Object>();
            option.put("tokenId", meta.tokenId);
            option.put("tokenName", meta.tokenName);
            option.put("roleId", meta.roleId);
            tokenOptions.add(option);
        }

        Set<String> filterKeys = allowedKeys;
        if (tokenId != null) {
            TokenMeta selected = idToMeta.get(tokenId);
            if (selected == null) {
                return ResponseEntity.badRequest().body(build(false, "token 不存在或无权限", null));
            }
            filterKeys = new LinkedHashSet<String>();
            filterKeys.add(selected.tokenKey);
        }

        List<XyzwTaskLogEntry> logs = taskLogService.listRecent(filterKeys, type, normalizeLimit(limit));
        List<Map<String, Object>> logItems = new ArrayList<Map<String, Object>>();
        for (XyzwTaskLogEntry entry : logs) {
            if (entry == null) {
                continue;
            }
            TokenMeta meta = keyToMeta.get(entry.getTokenKey());
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", entry.getId());
            item.put("type", entry.getType());
            item.put("level", entry.getLevel());
            item.put("phase", entry.getPhase());
            item.put("taskType", entry.getTaskType());
            item.put("taskTokenName", entry.getTaskTokenName());
            item.put("cmd", entry.getCmd());
            item.put("cmdName", entry.getCmdName());
            item.put("message", entry.getMessage());
            item.put("createdAt", entry.getCreatedAt() == null ? null : entry.getCreatedAt().toString());
            item.put("roleId", entry.getRoleId());
            item.put("tokenKey", entry.getTokenKey());
            if (meta != null) {
                item.put("tokenId", meta.tokenId);
                item.put("tokenName", meta.tokenName);
            } else {
                item.put("tokenName", entry.getTaskTokenName());
            }
            logItems.add(item);
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("logs", logItems);
        data.put("tokens", tokenOptions);
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

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit.intValue() <= 0) {
            return 120;
        }
        return Math.min(limit.intValue(), 300);
    }

    private String normalizeTokenName(XyzwUserToken token) {
        if (token == null) {
            return "未命名账号";
        }
        if (token.getName() != null && !token.getName().trim().isEmpty()) {
            return token.getName().trim();
        }
        if (token.getUuid() != null && !token.getUuid().trim().isEmpty()) {
            return token.getUuid().trim();
        }
        return "Token-" + token.getId();
    }

    private static final class TokenMeta {
        private Long tokenId;
        private String tokenName;
        private String tokenKey;
        private Long roleId;
    }
}
