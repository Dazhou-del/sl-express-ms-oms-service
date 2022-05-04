package com.sl.ms.oms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@Configuration
public class Knife4jConfiguration {

    @Bean(value = "defaultApi2")
    public Docket defaultApi2() {
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(new ApiInfoBuilder()
                        .title("神领物流 - 订单微服务接口文档")
                        .description("提供订单相关的一些服务功能。")
                        .termsOfServiceUrl("http://www.itcast.cn/")
                        .contact(new Contact("传智教育·研究院", "http://www.itcast.cn/", "yjy@itcast.cn"))
                        .version("1.0")
                        .build())
                //分组名称
                .groupName("1.0版本")
                .select()
                //这里指定Controller扫描包路径
                .apis(RequestHandlerSelectors.basePackage("com.sl.ms.oms.controller"))
                .paths(PathSelectors.any())
                .build();
        return docket;
    }

}