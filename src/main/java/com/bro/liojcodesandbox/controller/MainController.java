package com.bro.liojcodesandbox.controller;

import com.bro.liojcodesandbox.JavaDockerCodeSandBox;
import com.bro.liojcodesandbox.JavaNativeCodeSandBox;
import com.bro.liojcodesandbox.model.ExecuteCodeRequest;
import com.bro.liojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
public class MainController {

    //定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "lifeiyu";

    private static final String AUTH_REQUEST_SECRET = "hantianzun";

    @Resource
    private JavaNativeCodeSandBox javaNativeCodeSandBox;

    @Resource
    private JavaDockerCodeSandBox javaDockerCodeSandBox;

    @GetMapping("/health")
    public String healthCheck() {
        return "OK";
    }

    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest
            , HttpServletRequest request, HttpServletResponse response){
        //基本认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if(!AUTH_REQUEST_SECRET.equals(authHeader)){
            response.setStatus(403);
            return null;
        }

        if(executeCodeRequest==null){
            throw new RuntimeException("请求参数为空");
        }
        return javaNativeCodeSandBox.executeCode(executeCodeRequest);
    };

    @PostMapping("/executeCodeInDocker")
    public ExecuteCodeResponse executeCodeInDocker(@RequestBody ExecuteCodeRequest executeCodeRequest
            , HttpServletRequest request, HttpServletResponse response){
        //基本认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if(!AUTH_REQUEST_SECRET.equals(authHeader)){
            response.setStatus(403);
            return null;
        }

        if(executeCodeRequest==null){
            throw new RuntimeException("请求参数为空");
        }
        return javaDockerCodeSandBox.executeCode(executeCodeRequest);
    };

}
