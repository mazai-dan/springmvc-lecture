package com.mazai.mvcframework.controller;

import com.mazai.mvcframework.annotation.Autowired;
import com.mazai.mvcframework.annotation.Controller;
import com.mazai.mvcframework.annotation.RequestMapping;
import com.mazai.mvcframework.annotation.RequestParam;
import com.mazai.mvcframework.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/demo")
public class IDemoController {
    @Autowired
    private IDemoService iDemoService;

    @RequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response, @RequestParam("name") String name){
        String result = iDemoService.get(name);

        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response, @RequestParam("a") Integer a, @RequestParam("b")Integer b){
        try {
            response.getWriter().write(a + " + " + b +"=" + (a+b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping("/remove")
    public void remove(HttpServletRequest request, HttpServletResponse response, @RequestParam("id")Integer id){
    }
}
