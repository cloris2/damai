package com.example.record.provider;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @description:
 * @author: lk
 * @create: 2023-02-21
 **/
public class RecordBaseInfoProvider {
    
    private final Logger logger = LoggerFactory.getLogger(RecordBaseInfoProvider.class);
    
    /**
     * 操作记录标识
     * */
    public static final String OPERATOR_FLAG = "operatorFlag";
    
    /**
     * 存在操作记录标识
     * */
    public static final String OPERATOR_FLAG_YES = "operatorFlagYes";
    
    private static final String REGEX = "\\{([\\s\\S]*?)}";
    
    private ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();
    
    private ExpressionParser parser = new SpelExpressionParser();
    
    
    /**
     * 获取以"{"开头，以"}"结尾的内容
     * */
    public List<String> getFunction(String content){
        List<String> strs = new ArrayList<>();
        Pattern p = Pattern.compile(REGEX);
        Matcher m = p.matcher(content);
        while(m.find()) {
            strs.add(m.group());
        }
        return strs;
    }
    
    /**
     * spel解析入参
     * */
    public String getDefinitionContent(JoinPoint joinPoint, String content) throws NoSuchMethodException {
        Method method = getMethod(joinPoint);
        String definitionContent = getSpelDefinitionContent(content, method, joinPoint.getArgs());
        return definitionContent;
    }
    
    public Method getMethod(JoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (method.getDeclaringClass().isInterface()) {
            method = joinPoint.getTarget().getClass().getDeclaredMethod(signature.getName(),
                    method.getParameterTypes());
        }
        return method;
    }
    
    private String getSpelDefinitionContent(String definitionKey, Method method, Object[] parameterValues) {
        if (!ObjectUtils.isEmpty(definitionKey)) {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(null, method, parameterValues, nameDiscoverer);
            TemplateParserContext template = new TemplateParserContext("(",")");
            Expression expression = parser.parseExpression(definitionKey, template);
            Object objKey = expression.getValue(context);
            return ObjectUtils.nullSafeToString(objKey);
        }
        return null;
    }
    
    /**
     * 反射执行方法
     * */
    public String handleCustomFunction(String function, JoinPoint joinPoint) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // prepare invocation context
        Method currentMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        Method handleMethod = target.getClass().getDeclaredMethod(function, currentMethod.getParameterTypes());
        handleMethod.setAccessible(true);
    
        Object[] args = joinPoint.getArgs();
        // invoke
        Object result = handleMethod.invoke(target, args);
        return ObjectUtils.nullSafeToString(result);
    }
    
    /**
     * 解析函数并执行
     * */
    public String executeFunction(String content, ProceedingJoinPoint joinPoint) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        List<String> functionStrList = getFunction(content);
        String contentNew = content;
        for (String functionStr : functionStrList) {
            String functionStrRel = functionStr.replace("{","").replace("}","");
            //反射执行方法
            String functionResult = handleCustomFunction(functionStrRel,joinPoint);
            contentNew = contentNew.replace(functionStr,functionResult);
        }
        return contentNew;
    }
}
