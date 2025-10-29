package com.tencent.supersonic.headless.server.pojo;

import com.tencent.supersonic.headless.api.pojo.DbSchema;
import lombok.Data;

import java.util.List;

/**
 * 数据集导入配置
 * 一个配置文件 = 一个数据集 + 多个模型
 */
@Data
public class DataSetImportConfig {

    /**
     * 数据集配置
     */
    private DataSetConfig dataSetConfig;

    /**
     * 模型配置列表
     */
    private List<ModelConfig> models;

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
         * 衍生指标配置
         */
        private List<MetricConfig> metrics;
    }

    @Data
    public static class ModelConfig {
        /**
         * 模型名称
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
         * 数据库ID（可选，如果不指定则使用默认的databaseId）
         */
        private Long databaseId;

        /**
         * 数据库Schema
         */
        private DbSchema dbSchema;

        /**
         * 字段配置
         */
        private List<FieldConfig> fields;

        /**
         * 是否为主模型（事实表）
         */
        private Boolean isPrimary = false;

        /**
         * 关联到主模型的JOIN条件（仅非主模型需要）
         */
        private String joinCondition;
    }

    @Data
    public static class FieldConfig {
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
        private String fieldType;

        /**
         * 显示名称
         */
        private String name;

        /**
         * 聚合函数（用于measure）
         */
        private String agg;

        /**
         * 日期格式（用于data_time）
         */
        private String dateFormat;

        /**
         * 时间粒度（用于data_time）
         */
        private String timeGranularity;

        /**
         * 是否创建指标（用于measure字段，对应前端"快速创建"checkbox）
         */
        private Boolean isCreateMetric;

        /**
         * 是否创建维度（用于dimension字段，对应前端"快速创建"checkbox）
         */
        private Boolean isCreateDimension;
    }

    @Data
    public static class MetricConfig {
        /**
         * 指标名称
         */
        private String name;

        /**
         * 业务名称
         */
        private String bizName;

        /**
         * 表达式
         */
        private String expr;

        /**
         * 描述
         */
        private String description;
    }
}

