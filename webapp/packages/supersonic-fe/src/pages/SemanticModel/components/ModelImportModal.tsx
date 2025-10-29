import React, { useState } from 'react';
import { Modal, Upload, Button, message, Space, Alert, Table, Tag } from 'antd';
import { UploadOutlined, DownloadOutlined, EyeOutlined } from '@ant-design/icons';
import type { UploadFile, UploadProps } from 'antd';
import { importModelsFromJson, previewImportJson, downloadImportTemplate } from '../service';

type Props = {
  visible: boolean;
  domainId: number;
  onCancel: () => void;
  onSuccess: () => void;
};

/**
 * 模型导入弹窗组件
 */
const ModelImportModal: React.FC<Props> = ({
  visible,
  domainId,
  onCancel,
  onSuccess,
}) => {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [uploading, setUploading] = useState(false);
  const [previewData, setPreviewData] = useState<any[]>([]);
  const [previewVisible, setPreviewVisible] = useState(false);

  const uploadProps: UploadProps = {
    beforeUpload: (file) => {
      // 只允许JSON文件
      const isJson = file.type === 'application/json' || file.name.endsWith('.json');
      if (!isJson) {
        message.error('只能上传JSON格式文件！');
        return Upload.LIST_IGNORE;
      }

      // 文件大小限制：10MB
      const isLt10M = file.size / 1024 / 1024 < 10;
      if (!isLt10M) {
        message.error('文件大小不能超过10MB！');
        return Upload.LIST_IGNORE;
      }

      setFileList([file]);
      return false; // 阻止自动上传
    },
    fileList,
    onRemove: () => {
      setFileList([]);
      setPreviewData([]);
    },
    maxCount: 1,
  };

  // 预览文件内容
  const handlePreview = async () => {
    if (fileList.length === 0) {
      message.warning('请先选择文件');
      return;
    }

    const formData = new FormData();
    formData.append('file', fileList[0] as any);

    try {
      const data = await previewImportJson(formData);
      setPreviewData(data);
      setPreviewVisible(true);
      message.success('预览成功');
    } catch (error: any) {
      message.error(`预览失败: ${error.message || '未知错误'}`);
    }
  };

  // 执行导入
  const handleImport = async () => {
    if (fileList.length === 0) {
      message.warning('请先选择文件');
      return;
    }

    const formData = new FormData();
    formData.append('file', fileList[0] as any);
    formData.append('domainId', String(domainId));
    // 不再需要传递databaseId，系统会自动根据dbSchema.db匹配

    setUploading(true);

    try {
      const result = await importModelsFromJson(formData);
      const count = result?.count || result?.length || 0;
      message.success(`成功导入数据集（包含 ${count} 个模型和指标）`);
      setFileList([]);
      setPreviewData([]);
      onSuccess();
    } catch (error: any) {
      message.error(`导入失败: ${error.message || '未知错误'}`);
    } finally {
      setUploading(false);
    }
  };

  // 下载模板
  const handleDownloadTemplate = async () => {
    try {
      await downloadImportTemplate();
      message.success('模板下载成功');
    } catch (error: any) {
      message.error(`下载失败: ${error.message || '未知错误'}`);
    }
  };

  // 预览表格列定义
  const previewColumns = [
    {
      title: '模型名称',
      dataIndex: ['modelSchema', 'name'],
      key: 'name',
    },
    {
      title: '业务名称',
      dataIndex: ['modelSchema', 'bizName'],
      key: 'bizName',
    },
    {
      title: '描述',
      dataIndex: ['modelSchema', 'description'],
      key: 'description',
    },
    {
      title: '字段数量',
      key: 'fieldCount',
      render: (_: any, record: any) => {
        return record.modelSchema?.filedSchemas?.length || 0;
      },
    },
    {
      title: '主表',
      dataIndex: ['dbSchema', 'table'],
      key: 'table',
      render: (table: string) => <Tag color="blue">{table}</Tag>,
    },
  ];

  return (
    <>
      <Modal
        title="批量导入数据集"
        open={visible}
        onCancel={onCancel}
        width={700}
        footer={[
          <Button key="cancel" onClick={onCancel}>
            取消
          </Button>,
          <Button key="preview" icon={<EyeOutlined />} onClick={handlePreview}>
            预览
          </Button>,
          <Button
            key="import"
            type="primary"
            loading={uploading}
            onClick={handleImport}
            disabled={fileList.length === 0}
          >
            导入
          </Button>,
        ]}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          <Alert
            message="导入说明"
            description={
              <div>
                <p>1. 点击"下载模板"获取JSON配置文件示例</p>
                <p>2. 按照模板格式编辑您的配置（包含模型、指标、数据集）</p>
                <p>3. 系统会根据dbSchema.db自动匹配数据库连接，无需手动选择</p>
                <p>4. 上传JSON文件，系统会自动创建模型+指标+数据集</p>
                <p>5. 导入后即可在Chat BI中使用自然语言查询</p>
              </div>
            }
            type="info"
            showIcon
          />

          <div>
            <Button
              icon={<DownloadOutlined />}
              onClick={handleDownloadTemplate}
              style={{ marginBottom: 16 }}
            >
              下载模板
            </Button>
          </div>

          <Upload {...uploadProps}>
            <Button icon={<UploadOutlined />}>选择JSON文件</Button>
          </Upload>

          {previewData.length > 0 && (
            <div>
              <h4>预览结果：</h4>
              <Alert
                message={`将导入 ${previewData.length} 个数据集（包含模型和指标）`}
                type="success"
                showIcon
                style={{ marginBottom: 8 }}
              />
            </div>
          )}
        </Space>
      </Modal>

      {/* 预览详情弹窗 */}
      <Modal
        title="模型配置预览"
        open={previewVisible}
        onCancel={() => setPreviewVisible(false)}
        width={900}
        footer={[
          <Button key="close" onClick={() => setPreviewVisible(false)}>
            关闭
          </Button>,
        ]}
      >
        <Table
          dataSource={previewData}
          columns={previewColumns}
          pagination={false}
          rowKey={(record) => record.modelSchema?.bizName}
          expandable={{
            expandedRowRender: (record) => (
              <div style={{ margin: 0 }}>
                <p>
                  <strong>字段列表：</strong>
                </p>
                <Table
                  dataSource={record.modelSchema?.filedSchemas || []}
                  columns={[
                    { title: '字段名', dataIndex: 'columnName', key: 'columnName' },
                    { title: '显示名', dataIndex: 'name', key: 'name' },
                    {
                      title: '类型',
                      dataIndex: 'filedType',
                      key: 'filedType',
                      render: (type: string) => {
                        const colorMap: Record<string, string> = {
                          primary_key: 'red',
                          foreign_key: 'orange',
                          dimension: 'blue',
                          data_time: 'green',
                          measure: 'purple',
                        };
                        return <Tag color={colorMap[type] || 'default'}>{type}</Tag>;
                      },
                    },
                    { title: '聚合函数', dataIndex: 'agg', key: 'agg' },
                  ]}
                  pagination={false}
                  size="small"
                  rowKey="columnName"
                />
              </div>
            ),
          }}
        />
      </Modal>
    </>
  );
};

export default ModelImportModal;

