package com.xyzw.webhelper.xyzw;

import com.xyzw.webhelper.xyzw.dto.XyzwBinResponse;
import com.xyzw.webhelper.xyzw.dto.XyzwTokenCreateRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwTokenRecordResponse;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class XyzwProfileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(XyzwProfileService.class);
    private final XyzwUserBinMapper binMapper;
    private final XyzwUserTokenMapper tokenMapper;
    private final XyzwTokenService tokenService;
    private final Path storageRoot = Paths.get("backend", "storage", "bins");

    public XyzwProfileService(
        XyzwUserBinMapper binMapper,
        XyzwUserTokenMapper tokenMapper,
        XyzwTokenService tokenService
    ) {
        this.binMapper = binMapper;
        this.tokenMapper = tokenMapper;
        this.tokenService = tokenService;
    }

    public XyzwUserBin saveBin(Long userId, String name, String remark, String fileName, byte[] binData) {
        if (userId == null) {
            throw new IllegalArgumentException("user required");
        }
        if (binData == null || binData.length == 0) {
            throw new IllegalArgumentException("bin file required");
        }
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("bin name required");
        }
        LOGGER.info("saveBin start userId={} fileName={} bytes={}", userId, fileName, binData.length);
        String safeFileName = fileName == null ? "bin.dat" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String uniqueName = System.currentTimeMillis() + "_" + safeFileName;
        Path userDir = storageRoot.resolve(String.valueOf(userId));
        Path storedFile = userDir.resolve(uniqueName);
        try {
            Files.createDirectories(userDir);
            Files.write(storedFile, binData);
        } catch (IOException ex) {
            LOGGER.error("saveBin failed userId={} path={}", userId, storedFile, ex);
            throw new IllegalStateException("save bin file failed", ex);
        }

        XyzwUserBin bin = new XyzwUserBin();
        bin.setUserId(userId);
        bin.setName(trimmedName);
        bin.setFilePath(storedFile.toString());
        bin.setRemark(remark == null ? "" : remark.trim());
        LocalDateTime now = LocalDateTime.now();
        bin.setCreatedAt(now);
        bin.setUpdatedAt(now);
        binMapper.insert(bin);
        LOGGER.info("saveBin success userId={} binId={} filePath={}", userId, bin.getId(), bin.getFilePath());
        return bin;
    }

    public List<XyzwBinResponse> listBins(Long userId) {
        List<XyzwUserBin> bins = binMapper.findByUserId(userId);
        List<XyzwUserToken> tokens = tokenMapper.findByUserId(userId);
        LOGGER.info("listBins userId={} bins={} tokens={}", userId, bins.size(), tokens.size());
        Map<Long, XyzwTokenRecordResponse> tokenMap = new HashMap<Long, XyzwTokenRecordResponse>();
        for (XyzwUserToken token : tokens) {
            if (!tokenMap.containsKey(token.getBinId())) {
                tokenMap.put(token.getBinId(), toTokenResponse(token));
            }
        }

        List<XyzwBinResponse> result = new ArrayList<XyzwBinResponse>();
        for (XyzwUserBin bin : bins) {
            XyzwBinResponse response = new XyzwBinResponse();
            response.setId(bin.getId());
            response.setName(bin.getName());
            response.setFilePath(bin.getFilePath());
            response.setRemark(bin.getRemark());
            response.setCreatedAt(bin.getCreatedAt());
            List<XyzwTokenRecordResponse> tokenList = new ArrayList<XyzwTokenRecordResponse>();
            XyzwTokenRecordResponse token = tokenMap.get(bin.getId());
            if (token != null) {
                token.setName(bin.getName());
                tokenList.add(token);
            }
            response.setTokens(tokenList);
            result.add(response);
        }
        return result;
    }

    public XyzwTokenRecordResponse createToken(Long userId, Long binId, XyzwTokenCreateRequest request) {
        XyzwUserBin bin = binMapper.findByIdAndUserId(binId, userId);
        if (bin == null) {
            throw new IllegalArgumentException("bin not found");
        }
        LOGGER.info("createToken start userId={} binId={}", userId, binId);
        XyzwTokenRecordResponse response = refreshTokenForBin(bin);
        LOGGER.info("createToken success userId={} binId={} tokenId={}", userId, binId, response.getId());
        return response;
    }

    public XyzwTokenRecordResponse refreshToken(Long userId, Long binId) {
        XyzwUserBin bin = binMapper.findByIdAndUserId(binId, userId);
        if (bin == null) {
            throw new IllegalArgumentException("bin not found");
        }
        LOGGER.info("refreshToken start userId={} binId={}", userId, binId);
        XyzwTokenRecordResponse response = refreshTokenForBin(bin);
        LOGGER.info("refreshToken success userId={} binId={} tokenId={}", userId, binId, response.getId());
        return response;
    }

    public int refreshAllTokens() {
        List<XyzwUserBin> bins = binMapper.findAll();
        if (bins == null || bins.isEmpty()) {
            LOGGER.info("refreshAllTokens no bins");
            return 0;
        }
        int refreshed = 0;
        for (XyzwUserBin bin : bins) {
            if (bin == null) {
                continue;
            }
            try {
                refreshTokenForBin(bin);
                refreshed += 1;
            } catch (Exception ex) {
                LOGGER.warn("refreshAllTokens failed binId={} reason={}", bin.getId(), ex.getMessage());
            }
        }
        LOGGER.info("refreshAllTokens done count={}", refreshed);
        return refreshed;
    }

    public XyzwTokenRecordResponse getToken(Long userId, String uuid) {
        XyzwUserToken token = tokenMapper.findByUuidAndUserId(uuid, userId);
        if (token == null) {
            return null;
        }
        LOGGER.info("getToken userId={} tokenUuid={} tokenId={}", userId, uuid, token.getId());
        return toTokenResponse(token);
    }

    public void deleteToken(Long userId, Long tokenId) {
        if (userId == null || tokenId == null) {
            throw new IllegalArgumentException("token required");
        }
        XyzwUserToken token = tokenMapper.findByIdAndUserId(tokenId, userId);
        if (token == null) {
            throw new IllegalArgumentException("token not found");
        }
        LOGGER.info("deleteToken userId={} tokenId={} binId={}", userId, tokenId, token.getBinId());
        tokenMapper.deleteByIdAndUserId(tokenId, userId);
    }

    public void deleteBin(Long userId, Long binId) {
        if (userId == null || binId == null) {
            throw new IllegalArgumentException("bin required");
        }
        XyzwUserBin bin = binMapper.findByIdAndUserId(binId, userId);
        if (bin == null) {
            throw new IllegalArgumentException("bin not found");
        }
        LOGGER.info("deleteBin start userId={} binId={} filePath={}", userId, binId, bin.getFilePath());
        tokenMapper.deleteByBinIdAndUserId(binId, userId);
        binMapper.deleteByIdAndUserId(binId, userId);
        if (bin.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(bin.getFilePath()));
            } catch (IOException ex) {
                LOGGER.error("deleteBin file failed userId={} binId={} filePath={}", userId, binId, bin.getFilePath(), ex);
                throw new IllegalStateException("delete bin file failed", ex);
            }
        }
        LOGGER.info("deleteBin success userId={} binId={}", userId, binId);
    }

    private XyzwTokenRecordResponse toTokenResponse(XyzwUserToken token) {
        XyzwTokenRecordResponse response = new XyzwTokenRecordResponse();
        response.setId(token.getId());
        response.setUuid(token.getUuid());
        response.setName(token.getName());
        response.setBinId(token.getBinId());
        response.setToken(token.getToken());
        response.setServer(token.getServer());
        response.setWsUrl(token.getWsUrl());
        response.setCreatedAt(token.getCreatedAt());
        return response;
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private XyzwTokenRecordResponse refreshTokenForBin(XyzwUserBin bin) {
        byte[] tokenBytes;
        try {
            tokenBytes = Files.readAllBytes(Paths.get(bin.getFilePath()));
        } catch (IOException ex) {
            LOGGER.error("refreshToken read bin failed userId={} binId={} filePath={}", bin.getUserId(), bin.getId(), bin.getFilePath(), ex);
            throw new IllegalStateException("read bin file failed", ex);
        }
        tokenBytes = tokenService.fetchTokenBytes(tokenBytes);
        String encodedToken = Base64.getEncoder().encodeToString(tokenBytes);

        List<XyzwUserToken> existingTokens = tokenMapper.findByBinId(bin.getId());
        XyzwUserToken token;
        LocalDateTime now = LocalDateTime.now();
        if (existingTokens != null && !existingTokens.isEmpty()) {
            token = existingTokens.get(0);
            token.setUserId(bin.getUserId());
            token.setBinId(bin.getId());
            token.setName(trimOrNull(bin.getName()));
            token.setToken(encodedToken);
            token.setServer(null);
            token.setWsUrl(null);
            token.setUpdatedAt(now);
            tokenMapper.updateToken(token);
            if (existingTokens.size() > 1) {
                for (int i = 1; i < existingTokens.size(); i++) {
                    XyzwUserToken extra = existingTokens.get(i);
                    if (extra != null && extra.getId() != null) {
                        tokenMapper.deleteByIdAndUserId(extra.getId(), bin.getUserId());
                    }
                }
            }
        } else {
            token = new XyzwUserToken();
            token.setUserId(bin.getUserId());
            token.setBinId(bin.getId());
            token.setUuid(UUID.randomUUID().toString());
            token.setName(trimOrNull(bin.getName()));
            token.setToken(encodedToken);
            token.setServer(null);
            token.setWsUrl(null);
            token.setCreatedAt(now);
            token.setUpdatedAt(now);
            tokenMapper.insert(token);
        }
        return toTokenResponse(token);
    }
}
