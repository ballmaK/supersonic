package com.tencent.supersonic.headless.server.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.supersonic.auth.api.authentication.utils.UserHolder;
import com.tencent.supersonic.common.pojo.User;
import com.tencent.supersonic.common.pojo.exception.InvalidArgumentException;
import com.tencent.supersonic.headless.api.pojo.response.ModelResp;
import com.tencent.supersonic.headless.server.pojo.ModelImportConfig;
import com.tencent.supersonic.headless.server.service.ModelImportService;
import com.tencent.supersonic.headless.server.service.ModelService;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 模型文件导入Controller
 * 支持批量导入模型配置文件
 */
@Slf4j
@RestController
@RequestMapping("/api/semantic/model/import")
public class ModelImportController {

    private final ModelImportService modelImportService;
    private final ObjectMapper objectMapper;

    public ModelImportController(ModelImportService modelImportService) {
        this.modelImportService = modelImportService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 上传JSON文件批量导入模型
     * 
     * @param file JSON配置文件
     * @param domainId 领域ID
     * @param databaseId 数据库ID
     * @param request HTTP请求
     * @param response HTTP响应
     * @return 创建的模型列表
     */
    @PostMapping("/uploadJson")
    public Map<String, Object> uploadJson(
            @RequestParam("file") MultipartFile file,
            @RequestParam("domainId") Long domainId,
            @RequestParam(value = "databaseId", required = false) Long databaseId,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        
        User user = UserHolder.findUser(request, response);
        
        log.info("开始导入模型文件: {}, 用户: {}, 领域ID: {}, 数据库ID: {}", 
                file.getOriginalFilename(), user.getName(), domainId, databaseId);
        
        // databaseId 为 null，系统会根据每个模型的 dbSchema.db 自动匹配数据库连接
        if (databaseId == null) {
            log.info("未指定databaseId，系统将根据每个模型的dbSchema.db自动匹配对应的数据库连接");
        }
        
        // 验证文件
        validateFile(file);
        
        // 读取文件内容
        String jsonContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        
        // 解析并导入模型
        List<ModelResp> modelResps = modelImportService.importModelsFromJson(
                jsonContent, domainId, databaseId, user);
        
        log.info("成功导入 {} 个模型，准备返回结果", modelResps.size());
        
        // 简化返回对象，避免序列化问题
        List<Map<String, Object>> simplifiedResps = modelResps.stream()
                .map(resp -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", resp.getId());
                    map.put("name", resp.getName());
                    map.put("bizName", resp.getBizName());
                    return map;
                })
                .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", modelResps.size());
        result.put("models", simplifiedResps);
        
        log.info("返回结果序列化完成");
        
        return result;
    }

    /**
     * 预览JSON文件内容（不实际创建）
     * 
     * @param file JSON配置文件
     * @return 解析后的配置信息
     */
    @PostMapping("/previewJson")
    public List<ModelImportConfig> previewJson(
            @RequestParam("file") MultipartFile file) throws Exception {
        
        log.info("预览模型文件: {}", file.getOriginalFilename());
        
        // 验证文件
        validateFile(file);
        
        // 读取文件内容
        String jsonContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        
        // 解析配置
        List<ModelImportConfig> configs = modelImportService.parseImportConfig(jsonContent);
        
        log.info("解析出 {} 个模型配置", configs.size());
        
        return configs;
    }

    /**
     * 下载模型配置模板
     * 
     * @param response HTTP响应
     */
    @GetMapping("/downloadTemplate")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        String template = modelImportService.getTemplateJson();
        
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", 
                "attachment; filename=model-import-template.json");
        response.getWriter().write(template);
        response.getWriter().flush();
    }

    /**
     * 验证上传的文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("文件不能为空");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".json")) {
            throw new InvalidArgumentException("只支持JSON格式文件");
        }
        
        // 文件大小限制：10MB
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new InvalidArgumentException("文件大小不能超过10MB");
        }
    }
}

