-- =====================================================
-- 查询近7日现有股票最大涨幅
-- 创建时间: 2025-10-28
-- 描述: 统计每只股票在最近7个交易日内的最大涨跌幅
-- 数据范围: 2025-07-21 至 2025-08-20（共23个交易日，29只股票）
-- =====================================================

-- 方案1: 查询每只股票近7日的最大涨幅（简化版）
-- 使用数据中最新的7个交易日
SELECT 
    s.code AS 股票代码,
    i.name AS 股票名称,
    i.industry AS 所属行业,
    MAX(s.change_pct) AS 最大涨幅,
    MIN(s.change_pct) AS 最小涨幅,
    AVG(s.change_pct) AS 平均涨跌幅,
    COUNT(*) AS 交易天数,
    MAX(DATE_FORMAT(s.date, '%Y-%m-%d')) AS 最新交易日
FROM 
    stocks_data.stock_zh_a_daily s
LEFT JOIN 
    stocks_data.stock_info i ON s.code = i.code
WHERE 
    s.date >= (
        SELECT DISTINCT date 
        FROM stocks_data.stock_zh_a_daily 
        ORDER BY date DESC 
        LIMIT 1 OFFSET 6
    )
    AND (i.is_active = 1 OR i.is_active IS NULL)
GROUP BY 
    s.code, i.name, i.industry
HAVING 
    MAX(s.change_pct) IS NOT NULL
ORDER BY 
    最大涨幅 DESC
LIMIT 100;


-- =====================================================
-- 方案1B: 使用固定日期范围（最近7个交易日）
-- =====================================================
SELECT 
    s.code AS 股票代码,
    i.name AS 股票名称,
    i.industry AS 所属行业,
    MAX(s.change_pct) AS 最大涨幅,
    MIN(s.change_pct) AS 最小涨幅,
    AVG(s.change_pct) AS 平均涨跌幅,
    MAX(s.close) AS 期间最高收盘价,
    MIN(s.close) AS 期间最低收盘价,
    COUNT(*) AS 交易天数,
    MAX(DATE_FORMAT(s.date, '%Y-%m-%d')) AS 最新交易日,
    MIN(DATE_FORMAT(s.date, '%Y-%m-%d')) AS 开始日期
FROM 
    stocks_data.stock_zh_a_daily s
LEFT JOIN 
    stocks_data.stock_info i ON s.code = i.code
WHERE 
    s.date >= '2025-08-12'  -- 最新日期往前推7天
GROUP BY 
    s.code, i.name, i.industry
HAVING 
    MAX(s.change_pct) IS NOT NULL
ORDER BY 
    最大涨幅 DESC
LIMIT 100;


-- =====================================================
-- 方案2: 查询近7日最大涨幅详细信息（包含涨幅发生日期）
-- =====================================================
WITH recent_max_gain AS (
    SELECT 
        code,
        MAX(change_pct) AS max_gain
    FROM 
        stocks_data.stock_zh_a_daily
    WHERE 
        date >= '2025-08-12'
    GROUP BY 
        code
)
SELECT 
    s.code AS 股票代码,
    i.name AS 股票名称,
    i.industry AS 所属行业,
    i.market AS 市场,
    DATE_FORMAT(s.date, '%Y-%m-%d') AS 最大涨幅日期,
    s.change_pct AS 最大涨幅,
    s.open AS 开盘价,
    s.close AS 收盘价,
    s.high AS 最高价,
    s.low AS 最低价,
    s.volume AS 成交量,
    s.amount AS 成交额,
    s.turnover_rate AS 换手率,
    s.amplitude AS 振幅
FROM 
    stocks_data.stock_zh_a_daily s
INNER JOIN 
    recent_max_gain r ON s.code = r.code AND s.change_pct = r.max_gain
LEFT JOIN 
    stocks_data.stock_info i ON s.code = i.code
WHERE 
    s.date >= '2025-08-12'
ORDER BY 
    s.change_pct DESC
LIMIT 100;


-- =====================================================
-- 方案3: 按行业统计近7日最大涨幅
-- =====================================================
SELECT 
    COALESCE(i.industry, '未分类') AS 行业,
    COUNT(DISTINCT s.code) AS 股票数量,
    MAX(s.change_pct) AS 行业最大涨幅,
    MIN(s.change_pct) AS 行业最小涨幅,
    AVG(s.change_pct) AS 行业平均涨跌幅,
    SUM(s.amount) AS 行业总成交额,
    AVG(s.turnover_rate) AS 行业平均换手率
FROM 
    stocks_data.stock_zh_a_daily s
LEFT JOIN 
    stocks_data.stock_info i ON s.code = i.code
WHERE 
    s.date >= '2025-08-12'
GROUP BY 
    i.industry
ORDER BY 
    行业最大涨幅 DESC;


-- =====================================================
-- 方案4: 查询连续上涨的股票（近7日）
-- =====================================================
SELECT 
    s.code AS 股票代码,
    i.name AS 股票名称,
    i.industry AS 所属行业,
    COUNT(CASE WHEN s.change_pct > 0 THEN 1 END) AS 上涨天数,
    COUNT(CASE WHEN s.change_pct < 0 THEN 1 END) AS 下跌天数,
    COUNT(*) AS 总交易天数,
    SUM(s.change_pct) AS 累计涨幅,
    MAX(s.change_pct) AS 单日最大涨幅,
    MIN(s.change_pct) AS 单日最大跌幅,
    AVG(s.volume) AS 平均成交量,
    AVG(s.turnover_rate) AS 平均换手率
FROM 
    stocks_data.stock_zh_a_daily s
LEFT JOIN 
    stocks_data.stock_info i ON s.code = i.code
WHERE 
    s.date >= '2025-08-12'
GROUP BY 
    s.code, i.name, i.industry
HAVING 
    上涨天数 >= 4  -- 至少4天上涨
ORDER BY 
    累计涨幅 DESC
LIMIT 50;


-- =====================================================
-- 方案5: 查询涨幅排名Top10的股票详细信息
-- =====================================================
SELECT 
    s.code AS 股票代码,
    i.name AS 股票名称,
    i.industry AS 所属行业,
    DATE_FORMAT(s.date, '%Y-%m-%d') AS 交易日期,
    s.open AS 开盘价,
    s.close AS 收盘价,
    s.high AS 最高价,
    s.low AS 最低价,
    s.change_pct AS 涨跌幅,
    s.change_amount AS 涨跌额,
    s.volume AS 成交量,
    s.amount AS 成交额,
    s.turnover_rate AS 换手率,
    s.amplitude AS 振幅
FROM 
    stocks_data.stock_zh_a_daily s
LEFT JOIN 
    stocks_data.stock_info i ON s.code = i.code
WHERE 
    s.date >= '2025-08-12'
ORDER BY 
    s.change_pct DESC
LIMIT 50;


-- =====================================================
-- 数据统计信息
-- =====================================================
-- 查看数据整体情况
SELECT 
    DATE_FORMAT(MIN(date), '%Y-%m-%d') AS 最早日期,
    DATE_FORMAT(MAX(date), '%Y-%m-%d') AS 最新日期,
    COUNT(DISTINCT date) AS 交易日数量,
    COUNT(DISTINCT code) AS 股票数量,
    COUNT(*) AS 总记录数
FROM stocks_data.stock_zh_a_daily;
