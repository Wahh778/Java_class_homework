package com.boda.canteen.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.RandomGenerator; // 修正导入的类
import com.boda.canteen.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/common")
public class CommonController {

    /**
     * 文件上传接口
     */
    @PreAuthorize("hasAnyRole('chef','manager')")
    @PostMapping("/upload")
    public R<String> upload(MultipartFile file){
        // 获取文件后缀名
        String originalFilename = file.getOriginalFilename();
        String suffix = null;
        if (originalFilename != null) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 获取UUID文件名
        String fileName = UUID.randomUUID() + suffix;

        // 转储文件
        try {
            // 1. 补全路径分隔符（\），避免拼接错误
            String basePath = "D:\\canteen\\Java_class_homework\\picture\\";
            // 2. 拼接完整绝对路径
            File destFile = new File(basePath + fileName);
            // 3. 确保父目录存在（关键：如果picture文件夹不存在，自动创建）
            if (!destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            }
            // 4. 写入文件（此时transferTo会识别绝对路径，不再走Tomcat临时目录）
            file.transferTo(destFile);
            System.out.println("文件成功保存到：" + destFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return R.success(fileName);
    }

    /**
     * 获取验证码
     */
    @GetMapping("/getCode")
    public void getCode(HttpServletRequest request, HttpServletResponse response){
        // 1. 修正：使用Hutool的RandomGenerator（验证码生成器）
        RandomGenerator randomGenerator = new RandomGenerator("0123456789", 4);
        // 定义图片的显示大小
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(130, 48);
        response.setContentType("image/jpeg");
        response.setHeader("Pragma", "No-cache");
        try {
            // 2. 此时setGenerator参数类型匹配（CodeGenerator）
            lineCaptcha.setGenerator(randomGenerator);
            // 输出到页面
            lineCaptcha.write(response.getOutputStream());
            // 存储验证码
            request.getSession().setAttribute("code", lineCaptcha.getCode());
            // 关闭流
            response.getOutputStream().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}