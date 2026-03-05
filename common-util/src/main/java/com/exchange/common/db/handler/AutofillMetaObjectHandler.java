package com.exchange.common.db.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

public class AutofillMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        // do not override if createdAt/UpdatedAt is set explicitly
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // override updatedAt no matter if it is set
        // force overriding because workflow like [select, change, update the entity] will pass an object with an old updatedAt field (which is got from db)
        setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
    }
}
