-- MySQL数据库初始化脚本
-- 用于创建SuperSonic所需的数据库

-- 创建数据库
CREATE DATABASE IF NOT EXISTS supersonic 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE supersonic;

-- 显示数据库信息
SELECT 'SuperSonic数据库创建成功！' AS message;
SELECT DATABASE() AS current_database;
SELECT '字符集: ' + @@character_set_database AS charset;
SELECT '排序规则: ' + @@collation_database AS collation;

-- 显示数据库列表
SHOW DATABASES; 