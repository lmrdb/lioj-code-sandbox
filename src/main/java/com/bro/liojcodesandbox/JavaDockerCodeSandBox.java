package com.bro.liojcodesandbox;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.bro.liojcodesandbox.model.ExecuteCodeResponse;
import com.bro.liojcodesandbox.model.ExecuteMessage;
import com.bro.liojcodesandbox.model.JudgeInfo;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandBox extends JavaCodeSandBoxTemplate {

    public static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static final long TIME_OUT = 5000L;

    public static final Boolean FIRST_INIT=true;


    /**
     * 3.创建容器，把文件复制到容器内
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath=userCodeFile.getParentFile().getAbsolutePath();
        //3.创建容器，上传编译文件
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image="openjdk:8-alpine";
        //拉取镜像
        if(FIRST_INIT){
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback =new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像"+item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
            System.out.println("下载完成");
        }
        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        //指定文件路径映射
        HostConfig hostConfig =new HostConfig();
        hostConfig.setBinds(new Bind(userCodeParentPath,new Volume("/app")));
        //限制内存
        hostConfig.withMemory(100*1000*1000L);

        hostConfig.withMemorySwap(0L);
        //限制cpu
        hostConfig.withCpuCount(1L);
        //linux自带的安全管理配置，可以自行配置
        //hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理字符串"));
        //创建可交互的容器，能接受多次输入并输出
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                //禁用网络
                .withNetworkDisabled(true)
                //限制用户不能往根目录写文件
                //.withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();
        System.out.println(createContainerResponse);
        String containerId=createContainerResponse.getId();

        //4.启动容器,执行代码
        dockerClient.startContainerCmd(containerId).exec();

        //docker exec determined_fermi java -cp /app Main 1 2
        //执行命令并获取结果
        List<ExecuteMessage> executeMessagesList =new ArrayList<>();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] strings=inputArgs.split(" ");
            String[] cmdArray=ArrayUtil.append(new String[]{"java","-cp","/app","Main"},strings);
            //创建给容器执行的命令
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            System.out.println("创建执行命令："+execCreateCmdResponse);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time=0L;
            //是否超时
            final boolean[] timeout={true};
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {

                @Override
                public void onComplete() {
                    //如果程序在5s内执行完，则没超时
                    timeout[0]=false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if(StreamType.STDERR.equals(streamType)){
                        errorMessage[0] = (errorMessage[0] == null ? "" : errorMessage[0]) + new String(frame.getPayload());
                        System.out.println("输出错误结果："+ errorMessage[0]);
                    } else {
                        message[0] = (message[0] == null ? "" : message[0]) + new String(frame.getPayload());
                        System.out.println("输出结果："+ message[0]);
                    }
                    super.onNext(frame);
                }
            };

            final CountDownLatch latch = new CountDownLatch(1);
            final long[] maxMemory = {0L};
            //获取占用的内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) {

                }
                @Override
                public void onNext(Statistics statistics) {
                    Long memoryUsage = statistics.getMemoryStats().getUsage();
                    System.out.println("内存占用：" + memoryUsage);
                    if(memoryUsage!=null){
                        maxMemory[0] =Math.max(memoryUsage, maxMemory[0]);
                    }
                }
                @Override
                public void onError(Throwable throwable) {
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }

                @Override
                public void close() throws IOException {

                }
            };
            statsCmd.exec(statisticsResultCallback);
            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time=stopWatch.getLastTaskTimeMillis();

            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            // 判断message是否为null，是否只有换行符
            String resultMessage = message[0] != null ? message[0].replaceAll("\\r?\\n$", "") : null;
            if (resultMessage != null && !resultMessage.isEmpty()) {
                // 去掉尾部换行符，但保留其他字符
                executeMessage.setMessage(resultMessage);
            } else {
                // 保留原始消息
                executeMessage.setMessage(message[0]);
            }
            System.out.println("处理后输出结果"+message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessagesList.add(executeMessage);
        }
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        return executeMessagesList;
    }

    @Override
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessagesList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList=new ArrayList<>();
        //取用时最大值，便于判断是否超时
        long maxTime=0;
        long maxMemory=0;
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
            Long memory = executeMessage.getMemory();
            if(memory!=null){
                maxMemory=Math.max(maxTime,memory);
            }
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
        judgeInfo.setMemory(maxMemory);
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }
}
