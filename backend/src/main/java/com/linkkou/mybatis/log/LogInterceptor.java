package com.linkkou.mybatis.log;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.plugin.Intercepts;

import java.util.Properties;

@Intercepts({
    @Signature(type = org.apache.ibatis.executor.Executor.class, method = "update", args = {org.apache.ibatis.mapping.MappedStatement.class, Object.class}),
    @Signature(type = org.apache.ibatis.executor.Executor.class, method = "query", args = {org.apache.ibatis.mapping.MappedStatement.class, Object.class, org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class})
})
public class LogInterceptor implements Interceptor {
    public LogInterceptor() {
    }

    public LogInterceptor(String config) {
        // accept config string for compatibility with original interceptor
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // No-op to avoid regex replacement issues from external interceptor.
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }
}
