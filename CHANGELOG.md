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
  - 自动创建模型、衍生指标和数据集，无需手动配置
  - 在数据集管理页面添加"导入数据集"按钮
  - 提供股票数据分析示例配置文件（`stock_dataset_import.json`）
  
- **级联删除功能** - 删除模型时自动删除关联的指标和维度
  - 修改 `ModelServiceImpl.deleteModel()` 方法，支持级联删除
  - 避免"存在基于该模型创建的指标和维度，暂不能删除"的错误提示
  - 提升用户体验，一键清理所有关联数据

### Enhanced
- **模型字段自动填充** - 创建模型时自动填充字段名称
  - 修改 `ModelFieldForm.tsx`，勾选"快速创建"时自动填入字段注释或字段名
  - 减少手动输入，提升建模效率
  
- **数据库选择优化** - 导入数据集时必须选择数据库
  - 在导入弹窗中添加数据库选择下拉框
  - 自动加载数据库列表并默认选择第一个
  - 避免 `databaseId=0` 导致的导入失败问题

### Fixed
- **数据库并发锁问题修复** - 删除指标/维度时的锁等待超时
  - 在 `deleteModelDetailByDimAndMetric()` 方法添加 `synchronized` 同步锁
  - 添加重试机制（最多3次，递增延迟）
  - 添加异常捕获，避免阻塞删除流程
  - 添加空值检查，避免 NPE 错误

- **模型 SQL 简化指南** - 解决 Calcite 解析复杂 SQL 的问题
  - 提供简化版模型 SQL 示例
  - 文档说明避免使用 JOIN、WHERE、CASE WHEN 等复杂语法
  - 推荐使用数据库视图或小模型组合的方式

### Documentation
- 新增 `模型导入功能使用说明.md` - 完整的导入功能文档
- 新增 `SuperSonic_使用完整示例.md` - 从建模到查询的完整教程
- 新增 `stock_semantic_model_simple.sql` - 简化版模型 SQL 示例
- 更新 `README.md` in model_sql - 模型和查询 SQL 说明文档

### Technical Details
- 支持两种导入格式：
  - **新格式**（推荐）：一个 JSON 对象包含数据集配置和多个模型
  - **旧格式**（兼容）：JSON 数组，每个元素是独立模型
- API 端点：
  - `POST /api/semantic/model/import/uploadJson` - 导入数据集
  - `POST /api/semantic/model/import/previewJson` - 预览配置
  - `GET /api/semantic/model/import/downloadTemplate` - 下载模板

### Known Issues
- 预览功能暂时存在错误，建议直接导入（不影响主要功能）
- 导入后如需删除，建议等待几秒让事务完全提交

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
