package com.mazai.mvcframework.servlet;

import com.mazai.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {
    //保存application.properties配置文件中的内容
    private Properties contextConfig = new Properties();
    //保存扫描的所有的类名
    private List<String> classNames = new ArrayList<>();
    //ioc容器
    private Map<String, Object> ioc = new HashMap<>();

    private List<Handler> handlerMapping = new ArrayList<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Handler handler = getHandler(req);
        if (handler == null){
            resp.getWriter().write("404 NOT FOUND");
            return;
        }
        Class<?>[] parameterTypes =  handler.method.getParameterTypes();
        //根据参数位置动态赋值
        Object[] paramValues = new Object[parameterTypes.length];

        Map<String, String[]> parameterMap = req.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]","").replaceAll("\\s",",");
            if (!handler.paramIndexMapping.containsKey(entry.getKey()))continue;
            int index = handler.paramIndexMapping.get(entry.getKey());
            paramValues[index] = convert(parameterTypes[index],value);
        }

        if (handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())){
            int requestIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[requestIndex] = req;
        }

        if (handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())){
            int responseIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[responseIndex] = resp;
        }

        Object returnValue = handler.method.invoke(handler.controller,paramValues);
        if (returnValue == null || returnValue instanceof Void){return;}
        resp.getWriter().write(returnValue.toString());
    }



    @Override
    public void init(ServletConfig config) throws ServletException {

        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2.扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //3.初始化扫描到的类，并将他们放到IOC容器中
        doInstance();
        //4.完成依赖注入
        doAutowired();
        //4.初始化HandlerMapping
        initHandlerMapping();

        System.out.println("mvc framework init done");
    }

    private Handler getHandler(HttpServletRequest request){
        if (handlerMapping.isEmpty())return null;
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if (!matcher.matches()){continue;}
            return handler;
        }

        return null;
    }

    private Object convert(Class<?> type,String value){
        if (Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) return;

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(Controller.class)) {
                continue;
            }
            String url = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping classRequestMapping = (RequestMapping) clazz.getDeclaredAnnotation(RequestMapping.class);
                url = classRequestMapping.value();
            }
            //获取所有public类型的方法
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }
                RequestMapping methodRequestMapping = method.getAnnotation(RequestMapping.class);
                String regex = ("/" + url + "/" + methodRequestMapping.value()).replaceAll("/+", "/");
                handlerMapping.add(new Handler(entry.getValue(),method,Pattern.compile(regex)));
                System.out.println("Mapped" + url + "," + method);

            }
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        ;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(Autowired.class)) {
                    continue;
                }
                ;
                Autowired autowired = field.getAnnotation(Autowired.class);
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                    field.setAccessible(true);
                    try {
                        field.set(entry.getValue(), ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    Object controllerInstance = clazz.newInstance();
                    String controllerBeanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(controllerBeanName, controllerInstance);
            }else if (clazz.isAnnotationPresent(Service.class)) {
                    Service service = clazz.getDeclaredAnnotation(Service.class);
                    String serviceBeanName = service.value();
                    if ("".equals(serviceBeanName)) {
                        serviceBeanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object serviceInstance = clazz.newInstance();
                    ioc.put(serviceBeanName, serviceInstance);
                    for (Class<?> clazzInterface : clazz.getInterfaces()) {
                        if (ioc.containsKey(clazzInterface.getName())) {
                            throw new Exception("The" + clazzInterface.getName() + "is exists");
                        }
                        ioc.put(clazzInterface.getName(), serviceInstance);
                    }
                } else {
                    continue;
                }
                ;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        //之所以要做加法，是因为大、小写字母的ASCII码相差32
        //而且大蝎子妈的ASCII码要小于小写字母的ASCII码
        //在Java中，对char做运算实际上就是对ASCII码做算数运算
        chars[0] += 32;
        return String.valueOf(chars);
    }


    //加载配置文件
    private void doLoadConfig(String contextConfigLocation) {
        //直接通过类路径找到Spring主配置文件所在的路径
        //并将其读取出来放到Properties对象中
        //相当于将scanPackage=com.mazai.mvcframework保存到了内存中
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation.replace("classpath:",""));

        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        assert url != null;
        File classDir = new File(url.getFile());
        for (File file : Objects.requireNonNull(classDir.listFiles())) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String clazzName = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(clazzName);
            }
        }
    }

    private class Handler {
        protected Object controller; //保存方法对应的实例
        protected Method method; //保存映射的方法
        protected Pattern pattern;
        protected Map<String, Integer> paramIndexMapping;//参数顺序

        public Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof RequestParam) {
                        String paramName = ((RequestParam) annotation).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                for (int j = 0; j < parameterTypes.length; j++) {
                    Class<?> parameterType = parameterTypes[j];
                    if (parameterType == HttpServletRequest.class || parameterType == HttpServletResponse.class) {
                        paramIndexMapping.put(parameterType.getName(),j);
                    }
                }
            }
        }
    }
}

