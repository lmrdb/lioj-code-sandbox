package com.bro.liojcodesandbox.utils;

import com.bro.liojcodesandbox.model.ExecuteMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 进程工具类
 */
public class ProccessUtils {

    /**
     * 执行交互式进程并获取信息
     * @param runProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess,String opName){
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            int exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            //正常退出
            if (exitValue == 0) {
                System.out.println(opName+"成功");
                //分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                List<String> outputList = new ArrayList<>();
                //逐行读取
                String compileOutput;
                while ((compileOutput = bufferedReader.readLine()) != null) {
                    outputList.add(compileOutput);
                }
                executeMessage.setMessage(StringUtils.join(outputList, "\n"));
            } else {
                //异常退出
                System.out.println(opName+"失败：" + exitValue);
                //分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                //逐行读取

                List<String> outputList = new ArrayList<>();
                //逐行读取
                String compileOutput;
                while ((compileOutput = bufferedReader.readLine()) != null) {
                    outputList.add(compileOutput);
                }
                executeMessage.setMessage(StringUtils.join(outputList, "\n"));

                //分批获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                //逐行读取
                List<String> errorOutputList = new ArrayList<>();
                //逐行读取
                String errorCompileOutput;
                while ((errorCompileOutput = errorBufferedReader.readLine()) != null) {
                    errorOutputList.add(errorCompileOutput);
                }
                executeMessage.setErrorMessage(StringUtils.join(errorOutputList, "\n"));

            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        }catch (Exception e){
            e.printStackTrace();
        }

       return executeMessage;
    }


    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess,String opName,String args){
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            //向控制台输入
            OutputStream outputStream = runProcess.getOutputStream();

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] s = args.split(" ");
            String join= String.join("\n",s)+"\n";
            outputStreamWriter.write(join);
            //相当于回车，执行输入
            outputStreamWriter.flush();

            //分批获取进程的正常输出
            InputStream inputStream = runProcess.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder compileOutputStringBuilder = new StringBuilder();
            //逐行读取
            String compileOutput;
            while ((compileOutput = bufferedReader.readLine()) != null) {
                compileOutputStringBuilder.append(compileOutput);
            }
            executeMessage.setMessage(compileOutputStringBuilder.toString());
            //释放资源
            outputStreamWriter.close();
            outputStream.close();
            inputStream.close();
            runProcess.destroy();

        }catch (Exception e){
            e.printStackTrace();
        }

        return executeMessage;
    }
}
