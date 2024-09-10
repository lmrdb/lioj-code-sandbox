package com.bro.liojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.bro.liojcodesandbox.model.ExecuteCodeRequest;
import com.bro.liojcodesandbox.model.ExecuteCodeResponse;
import com.bro.liojcodesandbox.model.ExecuteMessage;
import com.bro.liojcodesandbox.model.JudgeInfo;
import com.bro.liojcodesandbox.utils.ProccessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public abstract class JavaCodeSandBoxTemplate implements CodeSandBox{


    public static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static final long TIME_OUT = 5000L;


    /**
     * 1.将代码保存为java文件
     * @param code
     * @return
     */
    public File saveCodeToFile(String code){
        //将代码保存为文件
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //判断全局代码目录是否存在，没有就新建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        //把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2.编译代码
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileFile(File userCodeFile){
        //编译代码，得到class文件
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            //等待程序执行，获取错误码
            ExecuteMessage executeMessage = ProccessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if(executeMessage.getExitValue()!=0){
                throw new RuntimeException("编译错误");
            }
            return  executeMessage;
        } catch (Exception e) {
           throw new RuntimeException(e);
        }
    }

    /**
     * 3.执行代码
     * @param userCodeFile
     * @param inputList
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList ){
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        List<ExecuteMessage> executeMessagesList =new ArrayList<>();
        for(String inputArgs : inputList){
            //设置JVM最大堆内存为256MB
            String runCmd=String.format("java -Xmx256m   -Dfile.encoding=UTF-8 -cp %s Main %s",userCodeParentPath,inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                //超时控制
                new Thread(()->{
                    try {
                        Thread.sleep(TIME_OUT);
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProccessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println(executeMessage);
                executeMessagesList.add(executeMessage);
            } catch (Exception e) {
                throw new RuntimeException("程序执行异常",e);
            }
        }
        return executeMessagesList;
    }

    /**
     * 4.获取输出/响应结果
     * @param executeMessagesList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessagesList){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList=new ArrayList<>();
        //取用时最大值，便于判断是否超时
        long maxTime=0;
        for(ExecuteMessage executeMessage : executeMessagesList){
            String errorMessage = executeMessage.getErrorMessage();
            if(StrUtil.isNotBlank(errorMessage)){
                executeCodeResponse.setMessage(errorMessage);
                //执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time=executeMessage.getTime();
            if(time!=null){
                maxTime=Math.max(maxTime,time);
            }
        }
        if(outputList.size()==executeMessagesList.size()){
            //正常运行完成
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo =new JudgeInfo();
        //judgeInfo.setMessage();
        //需要借助第三方库来实现，此处不做实现
        //judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;

    }

    /**
     * 5.清理文件
     * @param userCodeFile
     * @return
     */
    public boolean cleanFile(File userCodeFile){
        if(userCodeFile.getParentFile()!=null){
            boolean del = FileUtil.del(userCodeFile.getParentFile().getAbsolutePath());
            System.out.println("删除"+(del ? "成功":"失败"));
            return del;
        }
        return true;
    }


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();


        //1.把用户的代码保存为文件
        File userCodeFile = saveCodeToFile(code);


        //2.编译代码，得到class文件
        ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
        System.out.println(compileFileExecuteMessage);


        //3.执行代码，得到结果

        List<ExecuteMessage> executeMessagesList = runFile(userCodeFile, inputList);

        //收集整理输出结果
        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessagesList);

        //清理文件
        boolean cleaned = cleanFile(userCodeFile);
        if(!cleaned){
            log.error("deleteFile error,userCodeFile={}",userCodeFile.getAbsolutePath());
        }

        return outputResponse;
    }


    /**
     * 6.获取错误响应
     * @param e
     * @return
     */
    private ExecuteCodeResponse getResponse(Throwable e){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        //代表代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
