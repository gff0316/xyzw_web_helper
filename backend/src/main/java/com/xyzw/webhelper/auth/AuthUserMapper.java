package com.xyzw.webhelper.auth;

import org.apache.ibatis.annotations.Param;

@org.apache.ibatis.annotations.Mapper
public interface AuthUserMapper {
    AuthUser findByUsername(@Param("username") String username);

    AuthUser findByEmail(@Param("email") String email);

    AuthUser findByToken(@Param("token") String token);

    int insert(AuthUser user);

    int updateToken(@Param("id") Long id, @Param("token") String token, @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
