package com.exchange.common.db;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import org.springframework.dao.DuplicateKeyException;

public class DbBaseManager<T, M extends BaseMapper<T>> {
    protected final M mapper;

    public DbBaseManager(M mapper) {
        this.mapper = mapper;
    }

    protected LambdaUpdateChainWrapper<T> updateChainWrapper() {
        return new LambdaUpdateChainWrapper<>(mapper);
    }

    protected LambdaQueryChainWrapper<T> queryChainWrapper() {
        return new LambdaQueryChainWrapper<>(mapper);
    }

    public int insertIgnore(T record) {
        try {
            return mapper.insert(record);
        } catch (Exception e) {
            if (e instanceof DuplicateKeyException) {
                return 0;
            } else {
                throw e;
            }
        }
    }
}
