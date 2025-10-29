package com.tencent.supersonic.headless.core.translator.parser;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.tencent.supersonic.common.jsqlparser.SqlReplaceHelper;
import com.tencent.supersonic.common.jsqlparser.SqlSelectFunctionHelper;
import com.tencent.supersonic.common.jsqlparser.SqlSelectHelper;
import com.tencent.supersonic.common.pojo.Constants;
import com.tencent.supersonic.common.pojo.enums.EngineType;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.headless.api.pojo.SchemaItem;
import com.tencent.supersonic.headless.api.pojo.enums.AggOption;
import com.tencent.supersonic.headless.api.pojo.response.DimSchemaResp;
import com.tencent.supersonic.headless.api.pojo.response.MetricSchemaResp;
import com.tencent.supersonic.headless.api.pojo.response.QueryState;
import com.tencent.supersonic.headless.api.pojo.response.SemanticSchemaResp;
import com.tencent.supersonic.headless.core.pojo.Ontology;
import com.tencent.supersonic.headless.core.pojo.OntologyQuery;
import com.tencent.supersonic.headless.core.pojo.QueryStatement;
import com.tencent.supersonic.headless.core.pojo.SqlQuery;
import com.tencent.supersonic.headless.core.utils.SqlGenerateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This parser rewrites S2SQL including conversion from metric/dimension name to bizName and build
 * ontology query in preparation for generation of physical SQL.
 */
@Component("SqlQueryParser")
@Slf4j
public class SqlQueryParser implements QueryParser {

    @Override
    public boolean accept(QueryStatement queryStatement) {
        return Objects.nonNull(queryStatement.getSqlQuery()) && queryStatement.getIsS2SQL();
    }

    @Override
    public void parse(QueryStatement queryStatement) throws Exception {
        // build ontologyQuery
        SqlQuery sqlQuery = queryStatement.getSqlQuery();
        List<String> queryFields = SqlSelectHelper.getAllSelectFields(sqlQuery.getSql());
        Set<String> queryAliases = SqlSelectHelper.getAliasFields(sqlQuery.getSql());
        Set<String> ontologyMetricsDimensions = Collections.synchronizedSet(new HashSet<String>());
        Set<String> ontologyBizNameMetricsDimensions = Collections.synchronizedSet(new HashSet<>());
        queryFields.removeAll(queryAliases);
        Ontology ontology = queryStatement.getOntology();
        OntologyQuery ontologyQuery = buildOntologyQuery(ontology, queryFields);
        Set<String> queryFieldsSet = new HashSet<>(queryFields);
        ontologyQuery.getMetrics().forEach(m -> {
            ontologyMetricsDimensions.add(m.getName());
            ontologyBizNameMetricsDimensions.add(m.getBizName());
        });
        ontologyQuery.getDimensions().forEach(d -> {
            ontologyMetricsDimensions.add(d.getName());
            ontologyBizNameMetricsDimensions.add(d.getBizName());
        });
        // check if there are fields not matched with any metric or dimension

        if (!(queryFieldsSet.containsAll(ontologyMetricsDimensions)
                || queryFieldsSet.containsAll(ontologyBizNameMetricsDimensions))) {
            List<String> semanticFields = Lists.newArrayList();
            ontologyQuery.getMetrics().forEach(m -> semanticFields.add(m.getName()));
            ontologyQuery.getDimensions().forEach(d -> semanticFields.add(d.getName()));
            String errMsg =
                    String.format("Querying columns[%s] not matched with semantic fields[%s].",
                            queryFields, semanticFields);
            queryStatement.setErrMsg(errMsg);
            queryStatement.setStatus(QueryState.INVALID);
            return;
        }
        queryStatement.setOntologyQuery(ontologyQuery);

        AggOption sqlQueryAggOption = getAggOption(sqlQuery.getSql(), ontologyQuery.getMetrics());
        ontologyQuery.setAggOption(sqlQueryAggOption);

        convertNameToBizName(queryStatement);
        // Solve the problem of SQL execution error when alias is Chinese
        aliasesWithBackticks(queryStatement);
        rewriteOrderBy(queryStatement);

        // fill sqlQuery
        String tableName = SqlSelectHelper.getTableName(sqlQuery.getSql());
        if (StringUtils.isEmpty(tableName)) {
            return;
        }
        sqlQuery.setTable(Constants.TABLE_PREFIX + queryStatement.getDataSetId());
        SqlGenerateUtils sqlGenerateUtils = ContextUtils.getBean(SqlGenerateUtils.class);
        SemanticSchemaResp semanticSchema = queryStatement.getSemanticSchema();
        if (!sqlGenerateUtils.isSupportWith(
                EngineType.fromString(semanticSchema.getDatabaseResp().getType().toUpperCase()),
                semanticSchema.getDatabaseResp().getVersion())) {
            sqlQuery.setSupportWith(false);
            sqlQuery.setWithAlias(false);
        }

        log.info("parse sqlQuery [{}] ", sqlQuery);
    }

    private void aliasesWithBackticks(QueryStatement queryStatement) {
        String sql = queryStatement.getSqlQuery().getSql();
        sql = SqlReplaceHelper.replaceAliasWithBackticks(sql);
        queryStatement.getSqlQuery().setSql(sql);
    }

    private AggOption getAggOption(String sql, Set<MetricSchemaResp> metricSchemas) {
        if (SqlSelectFunctionHelper.hasAggregateFunction(sql)) {
            return AggOption.AGGREGATION;
        }

        if (!SqlSelectFunctionHelper.hasAggregateFunction(sql) && !SqlSelectHelper.hasGroupBy(sql)
                && !SqlSelectHelper.hasWith(sql) && !SqlSelectHelper.hasSubSelect(sql)) {
            log.debug("getAggOption simple sql set to DEFAULT");
            return AggOption.NATIVE;
        }

        // if there is no group by in S2SQL,set MetricTable's aggOption to "NATIVE"
        // if there is count() in S2SQL,set MetricTable's aggOption to "NATIVE"
        if (!SqlSelectFunctionHelper.hasAggregateFunction(sql)
                || SqlSelectFunctionHelper.hasFunction(sql, "count")
                || SqlSelectFunctionHelper.hasFunction(sql, "count_distinct")) {
            return AggOption.OUTER;
        }

        if (SqlSelectHelper.hasSubSelect(sql) || SqlSelectHelper.hasWith(sql)
                || SqlSelectHelper.hasGroupBy(sql)) {
            return AggOption.OUTER;
        }
        long defaultAggNullCnt = metricSchemas.stream().filter(
                m -> Objects.isNull(m.getDefaultAgg()) || StringUtils.isBlank(m.getDefaultAgg()))
                .count();
        if (defaultAggNullCnt > 0) {
            log.debug("getAggOption find null defaultAgg metric set to NATIVE");
            return AggOption.DEFAULT;
        }
        return AggOption.DEFAULT;
    }

    private Map<String, String> getNameToBizNameMap(OntologyQuery query) {
        // support fieldName and field alias to bizName
        Map<String, String> dimensionResults = query.getDimensions().stream().flatMap(
                entry -> getPairStream(entry.getAlias(), entry.getName(), entry.getBizName()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight, (k1, k2) -> k1));

        Map<String, String> metricResults = query.getMetrics().stream().flatMap(
                entry -> getPairStream(entry.getAlias(), entry.getName(), entry.getBizName()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight, (k1, k2) -> k1));

        dimensionResults.putAll(metricResults);
        return dimensionResults;
    }

    private Stream<Pair<String, String>> getPairStream(String aliasStr, String name,
            String bizName) {
        Set<Pair<String, String>> elements = new HashSet<>();
        elements.add(Pair.of(name, bizName));
        if (StringUtils.isNotBlank(aliasStr)) {
            List<String> aliasList = SchemaItem.getAliasList(aliasStr);
            for (String alias : aliasList) {
                elements.add(Pair.of(alias, bizName));
            }
        }
        return elements.stream();
    }

    private void convertNameToBizName(QueryStatement queryStatement) {
        Map<String, String> fieldNameToBizNameMap =
                getNameToBizNameMap(queryStatement.getOntologyQuery());
        String sql = queryStatement.getSqlQuery().getSql();
        log.debug("dataSetId:{},convert name to bizName before:{}", queryStatement.getDataSetId(),
                sql);
        sql = SqlReplaceHelper.replaceFields(sql, fieldNameToBizNameMap, true);
        log.debug("dataSetId:{},convert name to bizName after:{}", queryStatement.getDataSetId(),
                sql);
        sql = SqlReplaceHelper.replaceTable(sql,
                Constants.TABLE_PREFIX + queryStatement.getDataSetId());
        log.debug("replaceTableName after:{}", sql);
        queryStatement.getSqlQuery().setSql(sql);
    }

    private void rewriteOrderBy(QueryStatement queryStatement) {
        // replace order by field with the select sequence number
        String sql = queryStatement.getSqlQuery().getSql();
        String newSql = SqlReplaceHelper.replaceAggAliasOrderbyField(sql);
        log.debug("replaceOrderAggSameAlias {} -> {}", sql, newSql);
        queryStatement.getSqlQuery().setSql(newSql);
    }

    /**
     * 检查查询字段是否匹配schema元素（支持name、bizName、alias）
     */
    private boolean matchesField(Set<String> fields, String name, String bizName, String alias) {
        if (fields.contains(name) || fields.contains(bizName)) {
            return true;
        }
        
        // 检查alias
        if (StringUtils.isNotBlank(alias)) {
            List<String> aliasList = SchemaItem.getAliasList(alias);
            for (String aliasItem : aliasList) {
                if (fields.contains(aliasItem)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 从fields集合中移除所有匹配项（name、bizName、alias）
     */
    private void removeMatchedFields(Set<String> fields, String name, String bizName, String alias) {
        fields.remove(name);
        fields.remove(bizName);
        
        if (StringUtils.isNotBlank(alias)) {
            List<String> aliasList = SchemaItem.getAliasList(alias);
            for (String aliasItem : aliasList) {
                fields.remove(aliasItem);
            }
        }
    }

    private OntologyQuery buildOntologyQuery(Ontology ontology, List<String> queryFields) {
        OntologyQuery ontologyQuery = new OntologyQuery();
        Set<String> fields = Sets.newHashSet(queryFields);
        
        log.info("buildOntologyQuery - 查询字段: {}", queryFields);
        log.info("buildOntologyQuery - Ontology中的模型: {}", ontology.getModelMap().keySet());

        // find belonging model for every querying metrics
        ontology.getMetricMap().entrySet().forEach(entry -> {
            String modelName = entry.getKey();
            log.info("buildOntologyQuery - 检查模型 {} 的指标", modelName);
            entry.getValue().forEach(m -> {
                if (matchesField(fields, m.getName(), m.getBizName(), m.getAlias())) {
                    log.info("buildOntologyQuery - 匹配到指标: {} (name={}, bizName={}, alias={}) in model {}", 
                            m.getName(), m.getName(), m.getBizName(), m.getAlias(), modelName);
                    ontologyQuery.getModelMap().put(modelName,
                            ontology.getModelMap().get(modelName));
                    ontologyQuery.getMetricMap().computeIfAbsent(modelName, k -> Sets.newHashSet())
                            .add(m);
                    removeMatchedFields(fields, m.getName(), m.getBizName(), m.getAlias());
                }
            });
        });
        
        log.info("buildOntologyQuery - 指标匹配后剩余字段: {}", fields);
        log.info("buildOntologyQuery - 指标匹配后的模型: {}", ontologyQuery.getModelMap().keySet());

        // first try to find all querying dimensions in the models with querying metrics.
        ontology.getDimensionMap().entrySet().stream()
                .filter(entry -> ontologyQuery.getMetricMap().containsKey(entry.getKey()))
                .forEach(entry -> {
                    String modelName = entry.getKey();
                    entry.getValue().forEach(d -> {
                        if (matchesField(fields, d.getName(), d.getBizName(), d.getAlias())) {
                            ontologyQuery.getModelMap().put(modelName,
                                    ontology.getModelMap().get(modelName));
                            ontologyQuery.getDimensionMap()
                                    .computeIfAbsent(modelName, k -> Sets.newHashSet()).add(d);
                            removeMatchedFields(fields, d.getName(), d.getBizName(), d.getAlias());
                        }
                    });
                });

        // second, try to find a model that has all the remaining fields, such that no further join
        // is needed.
        if (!fields.isEmpty()) {
            log.info("buildOntologyQuery - 步骤3：查找包含所有剩余字段的单个模型");
            Map<String, Set<DimSchemaResp>> model2dims = new HashMap<>();
            ontology.getDimensionMap().entrySet().forEach(entry -> {
                String modelName = entry.getKey();
                entry.getValue().forEach(d -> {
                    if (matchesField(fields, d.getName(), d.getBizName(), d.getAlias())) {
                        String matchedBy = fields.contains(d.getName()) ? d.getName() : 
                                          fields.contains(d.getBizName()) ? d.getBizName() : "别名";
                        log.info("buildOntologyQuery - 字段 '{}' 匹配到模型 {} 的维度: {} (bizName={}, alias={})", 
                                matchedBy, modelName, d.getName(), d.getBizName(), d.getAlias());
                        model2dims.computeIfAbsent(modelName, k -> Sets.newHashSet()).add(d);
                    }
                });
            });
            log.info("buildOntologyQuery - model2dims统计: {}", 
                    model2dims.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
            Optional<Map.Entry<String, Set<DimSchemaResp>>> modelEntry = model2dims.entrySet()
                    .stream().filter(entry -> entry.getValue().size() == fields.size()).findFirst();
            if (modelEntry.isPresent()) {
                log.info("buildOntologyQuery - 找到包含所有字段的模型: {}", modelEntry.get().getKey());
                ontologyQuery.getDimensionMap().put(modelEntry.get().getKey(),
                        modelEntry.get().getValue());
                ontologyQuery.getModelMap().put(modelEntry.get().getKey(),
                        ontology.getModelMap().get(modelEntry.get().getKey()));
                fields.clear();
            } else {
                log.info("buildOntologyQuery - 未找到包含所有字段的单个模型");
            }
        }

        // finally if there are still fields not found belonging models, try to find in the models
        // iteratively
        if (!fields.isEmpty()) {
            log.info("buildOntologyQuery - 步骤4：逐个模型查找剩余字段: {}", fields);
            ontology.getDimensionMap().entrySet().forEach(entry -> {
                String modelName = entry.getKey();
                if (!ontologyQuery.getDimensionMap().containsKey(modelName)) {
                    entry.getValue().forEach(d -> {
                        if (matchesField(fields, d.getName(), d.getBizName(), d.getAlias())) {
                            String matchedBy = fields.contains(d.getName()) ? d.getName() : 
                                              fields.contains(d.getBizName()) ? d.getBizName() : "别名";
                            log.info("buildOntologyQuery - 字段 '{}' 匹配到模型 {} 的维度: {} (bizName={}, alias={})", 
                                    matchedBy, modelName, d.getName(), d.getBizName(), d.getAlias());
                            ontologyQuery.getModelMap().put(modelName,
                                    ontology.getModelMap().get(modelName));
                            ontologyQuery.getDimensionMap()
                                    .computeIfAbsent(modelName, k -> Sets.newHashSet()).add(d);
                            removeMatchedFields(fields, d.getName(), d.getBizName(), d.getAlias());
                        }
                    });
                }
            });
        }
        
        log.info("buildOntologyQuery - 最终匹配的模型: {}", ontologyQuery.getModelMap().keySet());
        log.info("buildOntologyQuery - 最终未匹配的字段: {}", fields);

        return ontologyQuery;
    }

}
