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
        bin.setName(name == null ? "" : name.trim());
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
        Map<Long, List<XyzwTokenRecordResponse>> tokenMap = new HashMap<Long, List<XyzwTokenRecordResponse>>();
        for (XyzwUserToken token : tokens) {
            XyzwTokenRecordResponse response = toTokenResponse(token);
            List<XyzwTokenRecordResponse> list = tokenMap.computeIfAbsent(token.getBinId(), key -> new ArrayList<XyzwTokenRecordResponse>());
            list.add(response);
        }

        List<XyzwBinResponse> result = new ArrayList<XyzwBinResponse>();
        for (XyzwUserBin bin : bins) {
            XyzwBinResponse response = new XyzwBinResponse();
            response.setId(bin.getId());
            response.setName(bin.getName());
            response.setFilePath(bin.getFilePath());
            response.setRemark(bin.getRemark());
            response.setCreatedAt(bin.getCreatedAt());
            response.setTokens(tokenMap.getOrDefault(bin.getId(), new ArrayList<XyzwTokenRecordResponse>()));
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
        byte[] tokenBytes;
        try {
            tokenBytes = Files.readAllBytes(Paths.get(bin.getFilePath()));
        } catch (IOException ex) {
            LOGGER.error("createToken read bin failed userId={} binId={} filePath={}", userId, binId, bin.getFilePath(), ex);
            throw new IllegalStateException("read bin file failed", ex);
        }
        tokenBytes = tokenService.fetchTokenBytes(tokenBytes);
        String encodedToken = Base64.getEncoder().encodeToString(tokenBytes);
        XyzwUserToken token = new XyzwUserToken();
        token.setUserId(userId);
        token.setBinId(binId);
        token.setUuid(UUID.randomUUID().toString());
        token.setToken(encodedToken);
        if (request != null) {
            token.setName(trimOrNull(request.getName()));
            token.setServer(trimOrNull(request.getServer()));
            token.setWsUrl(trimOrNull(request.getWsUrl()));
        }
        LocalDateTime now = LocalDateTime.now();
        token.setCreatedAt(now);
        token.setUpdatedAt(now);
        tokenMapper.insert(token);
        LOGGER.info("createToken success userId={} binId={} tokenId={}", userId, binId, token.getId());
        return toTokenResponse(token);
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
}
