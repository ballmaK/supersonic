package com.tencent.supersonic.headless.server.pojo;

import com.tencent.supersonic.headless.api.pojo.DbSchema;
import lombok.Data;

import java.util.List;

/**
 * 模型导入配置
 * 对应 s2-buildModel-exemplar.json 的格式
 */
@Data
public class ModelImportConfig {

    /**
     * 主表Schema
     */
    private DbSchema dbSchema;

    /**
     * 关联表Schema列表
     */
    private List<DbSchema> otherRelatedDBSchema;

    /**
     * 模型Schema定义
     */
    private ModelSchema modelSchema;

    /**
     * 数据集配置（可选）
     */
    private DataSetConfig dataSetConfig;

    @Data
    public static class ModelSchema {
        /**
         * 模型名称（中文）
         */
        private String name;

        /**
         * 业务名称（英文）
         */
        private String bizName;

        /**
         * 描述
         */
        private String description;

        /**
         * 字段Schema列表
         */
        private List<FieldSchema> filedSchemas;

        /**
         * 衍生指标列表（可选）
         */
        private List<MetricSchema> metrics;
    }

    @Data
    public static class MetricSchema {
        /**
         * 指标名称（中文）
         */
        private String name;

        /**
         * 业务名称（英文）
         */
        private String bizName;

        /**
         * 指标表达式
         */
        private String expr;

        /**
         * 描述
         */
        private String description;

        /**
         * 指标类型：measure, derived
         */
        private String metricType;
    }

    @Data
    public static class DataSetConfig {
        /**
         * 数据集名称
         */
        private String name;

        /**
         * 业务名称
         */
        private String bizName;

        /**
         * 描述
         */
        private String description;

        /**
         * 是否自动包含所有字段
         */
        private Boolean includesAll = true;

        /**
         * 模型关联配置（可选，用于多模型关联）
         */
        private List<ModelRelationConfig> modelRelations;
    }

    @Data
    public static class ModelRelationConfig {
        /**
         * 关联模型的业务名称
         */
        private String relatedModelBizName;

        /**
         * 关联条件（JOIN ON）
         */
        private String joinCondition;
    }

    @Data
    public static class FieldSchema {
        /**
         * 列名
         */
        private String columnName;

        /**
         * 数据类型
         */
        private String dataType;

        /**
         * 注释
         */
        private String comment;

        /**
         * 字段类型：primary_key, foreign_key, dimension, data_time, measure
         */
        private String filedType;

        /**
         * 显示名称
         */
        private String name;

        /**
         * 聚合函数（用于measure）：SUM, AVG, MAX, MIN, COUNT, COUNT_DISTINCT
         */
        private String agg;

        /**
         * 日期格式（用于data_time）
         */
        private String dateFormat;

        /**
         * 时间粒度（用于data_time）：day, week, month
         */
        private String timeGranularity;
    }
}

