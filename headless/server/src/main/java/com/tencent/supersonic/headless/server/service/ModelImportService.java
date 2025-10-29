package com.tencent.supersonic.headless.server.service;

import com.tencent.supersonic.common.pojo.User;
import com.tencent.supersonic.headless.api.pojo.response.ModelResp;
import com.tencent.supersonic.headless.server.pojo.ModelImportConfig;

import java.util.List;

/**
 * 模型导入服务接口
 */
public interface ModelImportService {

    /**
     * 从JSON字符串导入模型
     * 
     * @param jsonContent JSON内容
     * @param domainId 领域ID
     * @param databaseId 数据库ID
     * @param user 用户
     * @return 创建的模型列表
     */
    List<ModelResp> importModelsFromJson(String jsonContent, Long domainId, 
                                          Long databaseId, User user) throws Exception;

    /**
     * 解析导入配置（不实际创建）
     * 
     * @param jsonContent JSON内容
     * @return 配置列表
     */
    List<ModelImportConfig> parseImportConfig(String jsonContent) throws Exception;

    /**
     * 获取模板JSON
     * 
     * @return 模板JSON字符串
     */
    String getTemplateJson();
}

