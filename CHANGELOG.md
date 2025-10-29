# SuperSonic Changelog

- All notable changes to this project will be documented in this file.
- "Breaking Changes" describes any changes that may break existing functionality or cause
  compatibility issues with previous versions.

## SuperSonic [0.9.11-dev] - 2025-10-29

### Added
- **数据集批量导入功能** - 通过上传 JSON 配置文件一键创建完整数据集
  - 新增 `ModelImportController` REST API，支持文件上传、预览、模板下载
  - 新增 `ModelImportService` 和实现类，支持两种导入格式
  - 新增 `DataSetImportConfig` 配置类，支持一个数据集包含多个模型
  - 新增前端 `ModelImportModal` 组件，提供友好的导入界面
  - 自动创建模型、度量、维度和数据集，无需手动配置
  - 在数据集管理页面添加"导入数据集"按钮
  - 提供股票数据分析示例配置文件（`stock_dataset_import.json`）
  - 支持字段级别的 `isCreateMetric` 和 `isCreateDimension` 控制标志
  - 支持为每个度量字段指定聚合方式（AVG、SUM、MAX、MIN等）
  
- **级联删除功能** - 删除模型时自动删除关联的指标和维度
  - 修改 `ModelServiceImpl.deleteModel()` 方法，支持级联删除
  - 避免"存在基于该模型创建的指标和维度，暂不能删除"的错误提示
  - 提升用户体验，一键清理所有关联数据

- **维度别名匹配** - 支持通过别名识别维度字段
  - 修改 `SqlQueryParser` 的字段匹配逻辑，支持 name、bizName、alias 三种匹配方式
  - 添加 `matchesField()` 和 `removeMatchedFields()` 辅助方法
  - 解决 LLM 生成的字段名与模型定义不一致的问题（如"数据日期" vs "交易日期"）

### Enhanced
- **模型字段自动填充** - 创建模型时自动填充字段名称
  - 修改 `ModelFieldForm.tsx`，勾选"快速创建"时自动填入字段注释或字段名
  - 减少手动输入，提升建模效率
  
- **数据库自动匹配** - 导入数据集时智能匹配数据库连接
  - 根据模型配置中的 `dbSchema.db` 自动匹配对应的数据库连接
  - 如果匹配失败，自动使用用户第一个有权限的数据库
  - 移除前端手动选择数据库的步骤，简化导入流程
  - 修复 `databaseId=0` 导致的 `Source must not be null` 异常

- **模型数据完整性** - 确保导入的模型在前端完整显示
  - 修复 `ModelConverter.convert()` 方法，手动设置 measures、dimensions、identifiers 集合
  - 解决 `BeanMapper.mapper` 无法正确复制集合类型字段的问题
  - 修复 `ModelServiceImpl.updateModelByDimAndMetric()` 清空 measures 的危险逻辑
  - 确保前端"模型编辑"页面能正确显示所有度量和维度字段
  - 为导入的维度设置 `isCreateDimension=1`，为指标设置 `status=ONLINE`

- **主模型选择逻辑优化** - 修复多模型数据集中主表选择错误
  - 修复 `DataModelNode.findBaseModel()` 中的 bug，将错误的 `modelMetricCount` 改为 `modelDimCount`
  - 添加详细日志追踪模型选择过程
  - 确保基于维度优先级正确选择事实表

### Fixed
- **数据库并发锁问题修复** - 删除指标/维度时的锁等待超时
  - 在 `deleteModelDetailByDimAndMetric()` 方法添加 `synchronized` 同步锁
  - 添加重试机制（最多3次，递增延迟）
  - 添加异常捕获和详细日志，避免阻塞删除流程
  - 添加空值检查，避免 NPE 错误

- **SQL 关键字冲突修复** - 修复 `date` 等保留字段名导致的解析错误
  - 在 `ModelImportServiceImpl.buildSqlFromDbSchema()` 中为列名和表名添加反引号
  - 在 `SqlBuilder.render()` 中为字段引用添加反引号
  - 解决 Calcite 解析 `Encountered ". date"` 错误
  - 支持使用 `date`、`order`、`status` 等 SQL 保留字作为字段名

- **模型 YAML 转换修复** - 修复 bizName 和 databaseId 丢失问题
  - 修改 `ModelYamlManager.convert2YamlObj()` 正确设置 bizName 和 databaseId
  - 修改 `SemanticSchemaManager.getDataModel()` 正确恢复 bizName 和 databaseId
  - 解决查询时模型 bizName 为 null 导致的错误

- **度量字段创建修复** - 导入时正确创建度量指标
  - 在 `ModelImportServiceImpl` 中为 Measure 对象设置 `isCreateMetric=1`
  - 确保 `ModelConverter.convertMetricList()` 能正确识别并创建指标
  - 修复导入后 `model_detail.measures` 为空的问题

- **模型 SQL 简化指南** - 解决 Calcite 解析复杂 SQL 的问题
  - 提供简化版模型 SQL 示例
  - 文档说明避免使用 JOIN、WHERE、CASE WHEN 等复杂语法
  - 推荐使用数据库视图或小模型组合的方式

### Documentation
- 新增 `模型导入功能使用说明.md` - 完整的导入功能文档
- 新增 `cross_database_analysis_import.json` - 跨数据库导入示例
- 更新 `README.md` in model_sql - 数据集导入和 SQL 说明文档

### Technical Details
- 支持两种导入格式：
  - **新格式**（推荐）：一个 JSON 对象包含数据集配置和多个模型
  - **旧格式**（兼容）：JSON 数组，每个元素是独立模型
- JSON 配置支持：
  - `isCreateMetric` - 控制是否为 measure 字段创建指标
  - `isCreateDimension` - 控制是否为 dimension 字段创建维度
  - `agg` - 度量字段的聚合方式（AVG、SUM、MAX、MIN等）
  - `comment` - 字段注释说明
- API 端点：
  - `POST /api/semantic/model/import/uploadJson` - 导入数据集（databaseId 可选）
  - `POST /api/semantic/model/import/previewJson` - 预览配置
  - `GET /api/semantic/model/import/downloadTemplate` - 下载模板
- 智能数据库匹配逻辑：
  1. 优先使用配置中明确指定的 `databaseId`
  2. 根据 `dbSchema.db` 自动匹配数据库名称
  3. 使用用户第一个有权限的数据库作为后备

### Known Issues
- 预览功能暂时存在错误，建议直接导入（不影响主要功能）
- Calcite 优化器在验证跨数据库 schema 时可能报 `Object 'xxx' not found` 警告（不影响查询执行）

---

## SuperSonic [0.9.8] - 2024-11-01
- Add LLM management module to reuse connection across agents.
- Add ChatAPP configuration sub-module in Agent Management.
- Enhance dimension value management sub-module.
- Enhance memory management and term management sub-module.
- Enhance semantic translation of complex S2SQL.
- Enhance user experience in Chat UI.
- Introduce LLM-based semantic corrector and data interpreter.

## SuperSonic [0.9.2] - 2024-06-01

### Added
- support multiple rounds of dialogue
- add term configuration and identification to help LLM learn private domain knowledge
- support configuring LLM parameters in the agent
- metric market supports searching in natural language

### Updated
- introducing WorkFlow, Mapper, Parser, and Corrector support jump execution
- Introducing the concept of Model-Set to simplify Domain management
- overall optimization and upgrade of system pages
- optimize startup script

## SuperSonic [0.9.0] - 2024-04-03

### Added
- add tag abstraction and enhance tag marketplace management.
- headless-server provides Chat API interface.

### Updated
- migrate chat-core core component to headless-core.

## SuperSonic [0.8.6] - 2024-02-23

### Added
- support view abstraction to Headless.
- add the Metric API to Headless and optimizing the Headless API.
- add integration tests to Headless.
- add TimeCorrector to Chat.

## SuperSonic [0.8.4] - 2024-01-19

### Added
- support creating derived metrics.
  - Support creating metrics using three methods: by measure, metric, and field expressions.
- added support for postgresql data source.
- code adjustment and abstract optimization for chat and headless.

## SuperSonic [0.8.2] - 2023-12-18

### Added
- rewrite Python service with Java project, default to Java implementation.
- support setting the SQL generation method for large models in the interface.
- optimization of metric market experience.
- optimization of semantic modeling canvas experience.
- code structure adjustment and abstraction optimization for chat.

## SuperSonic [0.7.5] - 2023-10-13

### Added
- add SQL generation improvement optimization, support LLM SQL, Logic SQL, and Physical SQL display.
- add showcase functionality to support recommending similar questions.
- add frontend modification of filtering conditions and re-querying feature.
- support nested query functionality in semantic.
- support switching queries between multiple parsers in the frontend.

### Updated
- optimizing the build and deployment of the project.
- overall optimization of the SQL Corrector functionality.

### Fixed
- fix execute error on mysql <=5.7
  
## SuperSonic [0.7.4] - 2023-09-10
  
### Added
- add llm parser config
- add datasource agg_time option
- add function name adaptor in clickhouse
- add dimension and metric show in dsl
  
### Updated
- update user guide doc
- update query building of plugin in default model
- update some core API constructs to keep naming consistency
- update ConfigureDemo config
- update the association mechanism so that invisible dimensions and metrics will no longer be associated

### Fixed
- fix hasAggregateFunction logic in SqlParserSelectHelper

## SuperSonic [0.7.3] - 2023-08-29

### Added
- meet checkstyle code requirements
- save parseInfo after parsing
- add time statistics
- add agent

### Updated
- dsl where condition is used for front-end display
- dsl remove context inheritance

## SuperSonic [0.7.2] - 2023-08-12

### Added
- Support asynchronous query - return parse information to user before executing result
- Add Model as the basic data structure of the semantic definitions - this will repalce the old conception of subdomain

### Updated
- improve knowledge word similarity algorithm
- improve embedding plugin chooser
- improve DSLQuery field correction and parser


### Fixed
-  Fix mapper error that detectWord text is shorter than word
-  Fix MetricDomainQuery inherit context
  
## SuperSonic [0.7.0] - 2023-07-30

### Added

- Add function call parser and embedding recall parser
- Add plugin management
- Add web page query and web service query
- Metric filter query support querying metrics and comparing them in different dimensions
- Support dimension value mapping
- Support dimension/metric invisible, chat filter related data
- Add user guide docs


### Fixed

- Fix the data problem of getDomainList interface in standalone mode

## SuperSonic [0.6.0] - 2023-07-16

### Added

- Support llm parser and llm api server - users can query data through complex natural language.
- Support fuzzy query dimension and metric name - users can set the 'metric.dimension.threshold'
  parameter to control the fuzzy threshold.
- Support query filter and domain filter in query and search - users can specify domainId and query
  filter to filter the results in search and query.
- Support standalone mode - users can integrate semantic and chat services in one process for easy
  management and debugging.
- Support dsl query in semantic - users can specify DSL language to query data in Semantic. In the
  past, data querying was limited to struct language.
- Add unit and integration testing - add integration tests for single-turn and multi-turn
  conversations, to efficiently validate the code.
- Support dimension and metric alias - users can specify one or multiple aliases to expand search
  and query.
- Add scheduled semantic metadata update functionality in chat.
- Support create datasource by table name in the web page.
- Add the ability to set permissions for domain.
- Add a local/Remote implementation to the SemanticLayer interface.

### Updated

- Code architecture adjustment in chat.

1) Abstracting into three modules, namely api, core, and knowledge. Providing four core interfaces:
   SchemaMapper, SemanticLayer, SemanticParser, and SemanticQuery.
2) Add RuleSemanticQuery and LLMSemanticQuery implement to SemanticQuery.
3) Add all possible queries to the candidate queries, and then select the most suitable query from
   the candidate queries.

- Code architecture adjustment in semantic.

1) Refactor semantic layer SQL parsing code through Calcite.
2) Add QueryOptimizer interface.

- Chat config subdivided into detailed and metric scenarios - users can set different parameters in these two scenarios.

### Fixed

- Resolved last word not be recognized in SchemaMapper.
- Fix context inheritance problem.
- Fix the error of querying H2 database by month unit.
- Set faker user to context when authentication disable.

## SuperSonic [0.5.0] - 2023-06-15

### Added
- Add the search and query feature in chat according to rules in an extensible way.
- Add semantic/chat independent service for users.
- Add Modeling Interface - users can visually define and maintain semantic models in the web page.
- Add a unified semantic parsing layer - user can query data by struct language.

# Davinci Changelog

## Davinci [0.3.0] - 2023-06-15

### Added

- add data portal
- add metric trend chart
- add feedback component
- add tab component
- add page setting

### Updated

- modify permission process
- optimize css style
- optimize filter

### Removed

- delete view module
