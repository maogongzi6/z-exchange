package com.example.pojo;

import com.google.gson.Gson;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import javax.validation.constraints.NotNull;
import java.io.IOException;

/**
 * @Description: protobuf bean 转换工具类
 * @Author: junqiang.lu
 * @Date: 2021/8/9
 */
public class ProtobufBeanHelper {

    private ProtobufBeanHelper(){
    }

    public static <PojoType> PojoType toPojoBean(Class<PojoType> destPojoClass, Message sourceMessage) {
        try {
            return toPojo(destPojoClass, sourceMessage);
        } catch (Exception e) {
            throw (BadRequestException) (new BadRequestException()).initCause(e);
        }
    }

    public static void toProtoBean(Message.Builder destBuilder, Object sourcePojoBean) {
        try {
            toProto(destBuilder, sourcePojoBean);
        } catch (Exception e) {
            throw (BadReplyException) (new BadReplyException()).initCause(e);
        }
    }

    /**
     * 将ProtoBean对象转化为POJO对象
     *
     * @param destPojoClass 目标POJO对象的类类型
     * @param sourceMessage 含有数据的ProtoBean对象实例
     * @param <PojoType> 目标POJO对象的类类型范型
     * @return
     * @throws IOException
     */
    public static <PojoType> PojoType toPojo(@NotNull Class<PojoType> destPojoClass, @NotNull Message sourceMessage)
            throws IOException {
        String json = JsonFormat.printer().print(sourceMessage);
        return new Gson().fromJson(json, destPojoClass);
    }

    /**
     * 将POJO对象转化为ProtoBean对象
     *
     * @param destBuilder 目标Message对象的Builder类
     * @param sourcePojoBean 含有数据的POJO对象
     * @return
     * @throws IOException
     */
    public static void toProto(@NotNull Message.Builder destBuilder, @NotNull Object sourcePojoBean) throws IOException {
        String json = new Gson().toJson(sourcePojoBean);
        JsonFormat.parser().merge(json, destBuilder);
    }

    public static class BadRequestException extends RuntimeException {
        private BadRequestException() {
            super("bad_request_exception");
        }
    }
    public static class BadReplyException extends RuntimeException {
        private BadReplyException() {
            super("bad_reply_exception");
        }
    }
}
