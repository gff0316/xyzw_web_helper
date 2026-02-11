package com.xyzw.webhelper.xyzw;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface XyzwUserTokenMapper {
    void insert(XyzwUserToken token);

    XyzwUserToken findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    XyzwUserToken findByUuidAndUserId(@Param("uuid") String uuid, @Param("userId") Long userId);

    List<XyzwUserToken> findByUserId(@Param("userId") Long userId);

    List<XyzwUserToken> findByBinId(@Param("binId") Long binId);

    List<XyzwUserToken> findAll();

    int updateToken(XyzwUserToken token);

    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int deleteByBinIdAndUserId(@Param("binId") Long binId, @Param("userId") Long userId);
}
