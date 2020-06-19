package com.mazai.mvcframework.service.impl;

import com.mazai.mvcframework.annotation.Service;
import com.mazai.mvcframework.service.IDemoService;

@Service
public class IDemoServiceImpl implements IDemoService {
    @Override
    public String get(String name) {
        return "my name is:" + name;
    }
}
