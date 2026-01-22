package com.exchange.common.db.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.dao.DuplicateKeyException;

public class DbBaseManager<T, M extends BaseMapper<T>> {
    protected final M mapper;

    public DbBaseManager(M mapper) {
        this.mapper = mapper;
    }

    protected LambdaUpdateWrapper<T> updateLambdaWrapper() {
        return new LambdaUpdateWrapper<>();
    }

    protected LambdaQueryWrapper<T> queryLambdaWrapper() {
        return new LambdaQueryWrapper<>();
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
