package com.tencent.supersonic.headless.server.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.tencent.supersonic.common.pojo.User;
import com.tencent.supersonic.common.pojo.enums.QueryType;
import com.tencent.supersonic.common.pojo.enums.StatusEnum;
import com.tencent.supersonic.common.pojo.exception.InvalidArgumentException;
import com.tencent.supersonic.headless.api.pojo.*;
import com.tencent.supersonic.headless.api.pojo.enums.DimensionType;
import com.tencent.supersonic.headless.api.pojo.enums.IdentifyType;
import com.tencent.supersonic.headless.api.pojo.enums.MetricDefineType;
import com.tencent.supersonic.headless.api.pojo.request.DimensionReq;
import com.tencent.supersonic.headless.api.pojo.request.MetricReq;
import com.tencent.supersonic.headless.api.pojo.request.ModelReq;
import com.tencent.supersonic.headless.api.pojo.request.DataSetReq;
import com.tencent.supersonic.headless.api.pojo.response.DataSetResp;
import com.tencent.supersonic.headless.api.pojo.response.ModelResp;
import com.tencent.supersonic.headless.server.pojo.ModelImportConfig;
import com.tencent.supersonic.headless.server.pojo.DataSetImportConfig;
import com.tencent.supersonic.headless.server.service.ModelImportService;
import com.tencent.supersonic.headless.server.service.ModelService;
import com.tencent.supersonic.headless.server.service.MetricService;
import com.tencent.supersonic.headless.server.service.DataSetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型导入服务实现
 */
@Slf4j
@Service
public class ModelImportServiceImpl implements ModelImportService {

    private final ModelService modelService;
    private final MetricService metricService;
    private final DataSetService dataSetService;
    private final ObjectMapper objectMapper;

    public ModelImportServiceImpl(ModelService modelService, MetricService metricService, 
                                   DataSetService dataSetService) {
        this.modelService = modelService;
        this.metricService = metricService;
        this.dataSetService = dataSetService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ModelResp> importModelsFromJson(String jsonContent, Long domainId, 
                                                  Long databaseId, User user) throws Exception {
        
        // 尝试解析新格式（DataSetImportConfig）
        try {
            DataSetImportConfig dataSetConfig = objectMapper.readValue(
                    jsonContent, DataSetImportConfig.class);
            
            // 如果成功解析为新格式，使用新的导入逻辑
            if (dataSetConfig.getDataSetConfig() != null && dataSetConfig.getModels() != null) {
                return importDataSetFromNewFormat(dataSetConfig, domainId, databaseId, user);
            }
        } catch (Exception e) {
            log.debug("不是新格式的数据集配置，尝试解析旧格式");
        }
        
        // 解析旧格式（ModelImportConfig[]）
        List<ModelImportConfig> configs = parseImportConfig(jsonContent);
        
        // 转换为ModelReq并创建（旧逻辑）
        List<ModelResp> modelResps = new ArrayList<>();
        for (ModelImportConfig config : configs) {
            try {
                ModelReq modelReq = convertToModelReq(config, domainId, databaseId);
                ModelResp modelResp = modelService.createModel(modelReq, user);
                modelResps.add(modelResp);
                log.info("成功创建模型: {}", modelResp.getName());
                
                // 创建衍生指标（如果有配置）
                createMetrics(config, modelResp.getId(), user);
                
                // 创建数据集（如果有配置）
                createDataSet(config, modelResp, user);
            } catch (Exception e) {
                log.error("创建模型失败: {}", config.getModelSchema().getName(), e);
                throw new InvalidArgumentException(
                        String.format("创建模型 [%s] 失败: %s", 
                                config.getModelSchema().getName(), e.getMessage()));
            }
        }
        
        return modelResps;
    }

    /**
     * 使用新格式导入数据集
     */
    private List<ModelResp> importDataSetFromNewFormat(DataSetImportConfig config, 
                                                        Long domainId, Long databaseId, User user) throws Exception {
        
        List<ModelResp> modelResps = new ArrayList<>();
        List<Long> modelIds = new ArrayList<>();
        
        // 1. 创建所有模型
        for (DataSetImportConfig.ModelConfig modelConfig : config.getModels()) {
            ModelReq modelReq = convertModelConfigToReq(modelConfig, domainId, databaseId);
            ModelResp modelResp = modelService.createModel(modelReq, user);
            modelResps.add(modelResp);
            modelIds.add(modelResp.getId());
            log.info("成功创建模型: {}", modelResp.getName());
        }
        
        log.info("准备创建数据集，模型ID列表: {}", modelIds);
        
        // 2. 创建数据集（包含所有模型）
        createDataSetWithModels(config, modelIds, domainId, user);
        
        log.info("数据集创建完成，准备创建衍生指标");
        
        // 3. 创建数据集级别的衍生指标
        createDataSetMetrics(config, modelIds.get(0), user);
        
        log.info("成功导入数据集: {}, 包含 {} 个模型，准备返回", 
                config.getDataSetConfig().getName(), modelIds.size());
        
        return modelResps;
    }

    /**
     * 将新格式的ModelConfig转换为ModelReq
     */
    private ModelReq convertModelConfigToReq(DataSetImportConfig.ModelConfig modelConfig, 
                                              Long domainId, Long databaseId) {
        ModelReq modelReq = new ModelReq();
        modelReq.setName(modelConfig.getName());
        modelReq.setBizName(modelConfig.getBizName());
        modelReq.setDescription(modelConfig.getDescription());
        modelReq.setDomainId(domainId);
        modelReq.setDatabaseId(databaseId);
        modelReq.setStatus(StatusEnum.ONLINE.getCode());
        
        // 模型详情
        ModelDetail modelDetail = new ModelDetail();
        modelDetail.setQueryType(QueryType.DETAIL.name());
        
        // 构建SQL
        String sql = buildSqlFromDbSchema(modelConfig.getDbSchema());
        modelDetail.setSqlQuery(sql);
        
        // 字段定义
        List<Field> fields = new ArrayList<>();
        List<Identify> identifiers = new ArrayList<>();
        List<Dimension> dimensions = new ArrayList<>();
        List<Measure> measures = new ArrayList<>();
        
        for (DataSetImportConfig.FieldConfig fieldConfig : modelConfig.getFields()) {
            // 添加字段
            Field field = new Field();
            field.setFieldName(fieldConfig.getColumnName());
            field.setDataType(fieldConfig.getDataType());
            fields.add(field);
            
            // 根据字段类型添加到对应列表
            String fieldType = fieldConfig.getFieldType();
            if ("primary_key".equals(fieldType)) {
                Identify identify = new Identify();
                identify.setName(fieldConfig.getName());
                identify.setBizName(fieldConfig.getColumnName());
                identify.setType(IdentifyType.primary.name());
                identifiers.add(identify);
            } else if ("foreign_key".equals(fieldType)) {
                Identify identify = new Identify();
                identify.setName(fieldConfig.getName());
                identify.setBizName(fieldConfig.getColumnName());
                identify.setType(IdentifyType.foreign.name());
                identifiers.add(identify);
            } else if ("dimension".equals(fieldType) || "data_time".equals(fieldType)) {
                Dimension dimension = new Dimension();
                dimension.setName(fieldConfig.getName());
                dimension.setBizName(fieldConfig.getColumnName());
                dimension.setExpr(fieldConfig.getColumnName());
                dimension.setDescription(fieldConfig.getComment());
                
                if ("data_time".equals(fieldType)) {
                    dimension.setType(DimensionType.time);
                    dimension.setDateFormat(fieldConfig.getDateFormat());
                    if (fieldConfig.getTimeGranularity() != null) {
                        DimensionTimeTypeParams timeParams = new DimensionTimeTypeParams();
                        timeParams.setIsPrimary("true");
                        timeParams.setTimeGranularity(fieldConfig.getTimeGranularity());
                        dimension.setTypeParams(timeParams);
                    }
                } else {
                    dimension.setType(DimensionType.categorical);
                }
                
                dimensions.add(dimension);
            } else if ("measure".equals(fieldType)) {
                Measure measure = new Measure();
                measure.setName(fieldConfig.getName());
                measure.setBizName(fieldConfig.getColumnName());
                measure.setExpr(fieldConfig.getColumnName());
                measure.setAgg(fieldConfig.getAgg());
                measures.add(measure);
            }
        }
        
        modelDetail.setFields(fields);
        modelDetail.setIdentifiers(identifiers);
        modelDetail.setDimensions(dimensions);
        modelDetail.setMeasures(measures);
        
        modelReq.setModelDetail(modelDetail);
        
        return modelReq;
    }

    /**
     * 创建包含多个模型的数据集
     */
    private void createDataSetWithModels(DataSetImportConfig config, List<Long> modelIds, 
                                          Long domainId, User user) throws Exception {
        DataSetImportConfig.DataSetConfig dsConfig = config.getDataSetConfig();
        
        DataSetReq dataSetReq = new DataSetReq();
        dataSetReq.setName(dsConfig.getName());
        dataSetReq.setBizName(dsConfig.getBizName());
        dataSetReq.setDescription(dsConfig.getDescription());
        dataSetReq.setDomainId(domainId);
        dataSetReq.setStatus(StatusEnum.ONLINE.getCode());
        
        // 配置数据集详情
        DataSetDetail dataSetDetail = new DataSetDetail();
        List<DataSetModelConfig> dataSetModelConfigs = new ArrayList<>();
        
        // 将所有模型添加到数据集
        for (Long modelId : modelIds) {
            DataSetModelConfig modelConfig = new DataSetModelConfig();
            modelConfig.setId(modelId);
            modelConfig.setIncludesAll(dsConfig.getIncludesAll());
            dataSetModelConfigs.add(modelConfig);
        }
        
        dataSetDetail.setDataSetModelConfigs(dataSetModelConfigs);
        dataSetReq.setDataSetDetail(dataSetDetail);
        
        DataSetResp dataSetResp = dataSetService.save(dataSetReq, user);
        log.info("成功创建数据集: {}, 包含 {} 个模型", dataSetResp.getName(), modelIds.size());
    }

    /**
     * 创建数据集级别的衍生指标
     */
    private void createDataSetMetrics(DataSetImportConfig config, Long modelId, User user) throws Exception {
        List<DataSetImportConfig.MetricConfig> metricsConfig = config.getDataSetConfig().getMetrics();
        if (CollectionUtils.isEmpty(metricsConfig)) {
            return;
        }
        
        List<MetricReq> metricReqs = new ArrayList<>();
        for (DataSetImportConfig.MetricConfig metricConfig : metricsConfig) {
            MetricReq metricReq = new MetricReq();
            metricReq.setName(metricConfig.getName());
            metricReq.setBizName(metricConfig.getBizName());
            metricReq.setDescription(metricConfig.getDescription());
            metricReq.setModelId(modelId);
            metricReq.setMetricDefineType(MetricDefineType.MEASURE);
            
            MetricDefineByMeasureParams measureParams = new MetricDefineByMeasureParams();
            measureParams.setExpr(metricConfig.getExpr());
            metricReq.setMetricDefineByMeasureParams(measureParams);
            
            metricReqs.add(metricReq);
        }
        
        if (!metricReqs.isEmpty()) {
            log.info("开始批量创建 {} 个数据集级别的衍生指标", metricReqs.size());
            metricService.createMetricBatch(metricReqs, user);
            log.info("成功创建 {} 个数据集级别的衍生指标", metricReqs.size());
        } else {
            log.info("没有配置数据集级别的衍生指标");
        }
    }

    @Override
    public List<ModelImportConfig> parseImportConfig(String jsonContent) throws Exception {
        try {
            List<ModelImportConfig> configs = objectMapper.readValue(
                    jsonContent, new TypeReference<List<ModelImportConfig>>() {});
            
            // 验证配置
            validateConfigs(configs);
            
            return configs;
        } catch (Exception e) {
            log.error("解析JSON配置失败", e);
            throw new InvalidArgumentException("JSON格式错误: " + e.getMessage());
        }
    }

    @Override
    public String getTemplateJson() {
        // 返回示例配置模板
        String template = """
                [
                  {
                    "dbSchema": {
                      "db": "stocks_data",
                      "table": "stock_zh_a_daily",
                      "dbColumns": [
                        {
                          "columnName": "date",
                          "dataType": "DATE",
                          "comment": "交易日期"
                        },
                        {
                          "columnName": "code",
                          "dataType": "VARCHAR",
                          "comment": "股票代码"
                        },
                        {
                          "columnName": "close",
                          "dataType": "DECIMAL",
                          "comment": "收盘价"
                        }
                      ]
                    },
                    "otherRelatedDBSchema": [],
                    "modelSchema": {
                      "name": "股票日线数据",
                      "bizName": "StockDaily",
                      "description": "股票每日交易数据",
                      "filedSchemas": [
                        {
                          "columnName": "date",
                          "dataType": "DATE",
                          "comment": "交易日期",
                          "filedType": "data_time",
                          "name": "交易日期",
                          "dateFormat": "yyyy-MM-dd",
                          "timeGranularity": "day"
                        },
                        {
                          "columnName": "code",
                          "dataType": "VARCHAR",
                          "comment": "股票代码",
                          "filedType": "primary_key",
                          "name": "股票代码"
                        },
                        {
                          "columnName": "close",
                          "dataType": "DECIMAL",
                          "comment": "收盘价",
                          "filedType": "measure",
                          "name": "收盘价",
                          "agg": "AVG"
                        }
                      ]
                    }
                  }
                ]
                """;
        return template;
    }

    /**
     * 将导入配置转换为ModelReq
     */
    private ModelReq convertToModelReq(ModelImportConfig config, Long domainId, Long databaseId) {
        ModelReq modelReq = new ModelReq();
        
        // 基本信息
        ModelImportConfig.ModelSchema modelSchema = config.getModelSchema();
        modelReq.setName(modelSchema.getName());
        modelReq.setBizName(modelSchema.getBizName());
        modelReq.setDescription(modelSchema.getDescription());
        modelReq.setDomainId(domainId);
        modelReq.setDatabaseId(databaseId);
        modelReq.setStatus(StatusEnum.ONLINE.getCode());
        
        // 模型详情
        ModelDetail modelDetail = new ModelDetail();
        modelDetail.setQueryType(QueryType.DETAIL.name());
        
        // 构建SQL
        String sql = buildSqlFromDbSchema(config.getDbSchema());
        modelDetail.setSqlQuery(sql);
        
        // 字段定义
        List<Field> fields = new ArrayList<>();
        List<Identify> identifiers = new ArrayList<>();
        List<Dimension> dimensions = new ArrayList<>();
        List<Measure> measures = new ArrayList<>();
        
        for (ModelImportConfig.FieldSchema fieldSchema : modelSchema.getFiledSchemas()) {
            // 添加字段
            Field field = new Field();
            field.setFieldName(fieldSchema.getColumnName());
            field.setDataType(fieldSchema.getDataType());
            fields.add(field);
            
            // 根据字段类型添加到对应列表
            String filedType = fieldSchema.getFiledType();
            if ("primary_key".equals(filedType)) {
                Identify identify = new Identify();
                identify.setName(fieldSchema.getName());
                identify.setBizName(fieldSchema.getColumnName());
                identify.setType(IdentifyType.primary.name());
                identifiers.add(identify);
            } else if ("foreign_key".equals(filedType)) {
                Identify identify = new Identify();
                identify.setName(fieldSchema.getName());
                identify.setBizName(fieldSchema.getColumnName());
                identify.setType(IdentifyType.foreign.name());
                identifiers.add(identify);
            } else if ("dimension".equals(filedType) || "data_time".equals(filedType)) {
                Dimension dimension = new Dimension();
                dimension.setName(fieldSchema.getName());
                dimension.setBizName(fieldSchema.getColumnName());
                dimension.setExpr(fieldSchema.getColumnName());
                dimension.setDescription(fieldSchema.getComment());
                
                // 时间维度特殊处理
                if ("data_time".equals(filedType)) {
                    dimension.setType(DimensionType.time);
                    dimension.setDateFormat(fieldSchema.getDateFormat());
                    if (fieldSchema.getTimeGranularity() != null) {
                        DimensionTimeTypeParams timeParams = new DimensionTimeTypeParams();
                        timeParams.setIsPrimary("true");
                        timeParams.setTimeGranularity(fieldSchema.getTimeGranularity());
                        dimension.setTypeParams(timeParams);
                    }
                } else {
                    dimension.setType(DimensionType.categorical);
                }
                
                dimensions.add(dimension);
            } else if ("measure".equals(filedType)) {
                Measure measure = new Measure();
                measure.setName(fieldSchema.getName());
                measure.setBizName(fieldSchema.getColumnName());
                measure.setExpr(fieldSchema.getColumnName());
                measure.setAgg(fieldSchema.getAgg());
                measures.add(measure);
            }
        }
        
        modelDetail.setFields(fields);
        modelDetail.setIdentifiers(identifiers);
        modelDetail.setDimensions(dimensions);
        modelDetail.setMeasures(measures);
        
        modelReq.setModelDetail(modelDetail);
        
        return modelReq;
    }

    /**
     * 从DbSchema构建SQL
     */
    private String buildSqlFromDbSchema(DbSchema dbSchema) {
        String table = dbSchema.getTable();
        
        // 如果table本身就是SQL，直接返回
        if (table.toUpperCase().contains("SELECT")) {
            return table;
        }
        
        // 否则构建简单的SELECT语句
        List<String> columns = dbSchema.getDbColumns().stream()
                .map(DBColumn::getColumnName)
                .collect(Collectors.toList());
        
        String db = dbSchema.getDb();
        String fullTableName = db != null ? db + "." + table : table;
        
        return "SELECT " + String.join(", ", columns) + " FROM " + fullTableName;
    }

    /**
     * 创建数据集
     */
    private void createDataSet(ModelImportConfig config, ModelResp modelResp, User user) throws Exception {
        ModelImportConfig.DataSetConfig dataSetConfig = config.getDataSetConfig();
        if (dataSetConfig == null) {
            log.info("未配置数据集，跳过数据集创建");
            return;
        }
        
        DataSetReq dataSetReq = new DataSetReq();
        dataSetReq.setName(dataSetConfig.getName());
        dataSetReq.setBizName(dataSetConfig.getBizName());
        dataSetReq.setDescription(dataSetConfig.getDescription());
        dataSetReq.setDomainId(modelResp.getDomainId());
        dataSetReq.setStatus(StatusEnum.ONLINE.getCode());
        
        // 配置数据集详情
        DataSetDetail dataSetDetail = new DataSetDetail();
        List<DataSetModelConfig> dataSetModelConfigs = new ArrayList<>();
        
        // 添加当前模型到数据集
        DataSetModelConfig modelConfig = new DataSetModelConfig();
        modelConfig.setId(modelResp.getId());
        modelConfig.setIncludesAll(dataSetConfig.getIncludesAll());
        dataSetModelConfigs.add(modelConfig);
        
        dataSetDetail.setDataSetModelConfigs(dataSetModelConfigs);
        dataSetReq.setDataSetDetail(dataSetDetail);
        
        DataSetResp dataSetResp = dataSetService.save(dataSetReq, user);
        log.info("成功创建数据集: {}", dataSetResp.getName());
    }

    /**
     * 创建衍生指标
     */
    private void createMetrics(ModelImportConfig config, Long modelId, User user) throws Exception {
        List<ModelImportConfig.MetricSchema> metricsConfig = config.getModelSchema().getMetrics();
        if (CollectionUtils.isEmpty(metricsConfig)) {
            return;
        }
        
        List<MetricReq> metricReqs = new ArrayList<>();
        for (ModelImportConfig.MetricSchema metricSchema : metricsConfig) {
            MetricReq metricReq = new MetricReq();
            metricReq.setName(metricSchema.getName());
            metricReq.setBizName(metricSchema.getBizName());
            metricReq.setDescription(metricSchema.getDescription());
            metricReq.setModelId(modelId);
            metricReq.setMetricDefineType(MetricDefineType.MEASURE);
            
            // 创建度量定义参数
            MetricDefineByMeasureParams measureParams = new MetricDefineByMeasureParams();
            measureParams.setExpr(metricSchema.getExpr());
            metricReq.setMetricDefineByMeasureParams(measureParams);
            
            metricReqs.add(metricReq);
        }
        
        if (!metricReqs.isEmpty()) {
            metricService.createMetricBatch(metricReqs, user);
            log.info("成功创建 {} 个衍生指标", metricReqs.size());
        }
    }

    /**
     * 验证配置
     */
    private void validateConfigs(List<ModelImportConfig> configs) {
        if (CollectionUtils.isEmpty(configs)) {
            throw new InvalidArgumentException("配置不能为空");
        }
        
        for (ModelImportConfig config : configs) {
            if (config.getDbSchema() == null) {
                throw new InvalidArgumentException("dbSchema不能为空");
            }
            if (config.getModelSchema() == null) {
                throw new InvalidArgumentException("modelSchema不能为空");
            }
            if (CollectionUtils.isEmpty(config.getModelSchema().getFiledSchemas())) {
                throw new InvalidArgumentException("filedSchemas不能为空");
            }
        }
    }
}

