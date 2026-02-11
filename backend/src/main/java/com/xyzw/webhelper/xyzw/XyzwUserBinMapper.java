package com.xyzw.webhelper.xyzw;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface XyzwUserBinMapper {
    void insert(XyzwUserBin bin);

    XyzwUserBin findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<XyzwUserBin> findByUserId(@Param("userId") Long userId);

    List<XyzwUserBin> findAll();

    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
