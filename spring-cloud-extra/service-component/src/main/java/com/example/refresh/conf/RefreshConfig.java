package com.example.refresh.conf;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import com.example.refresh.handle.NacosCustom;
import com.example.refresh.handle.RibbonCustom;
import com.example.refresh.handle.NacosAndRibbonCustom;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @description: bean管理
 * @author: k
 * @create: 2022-06-08
 **/

@EnableConfigurationProperties
@AutoConfigureAfter({NacosAutoServiceRegistration.class})
public class RefreshConfig {

    @Bean
    public NacosCustom nacosCustom(NacosDiscoveryProperties discoveryProperties, NacosAutoServiceRegistration nacosAutoServiceRegistration){
        return new NacosCustom(discoveryProperties,nacosAutoServiceRegistration);
    }

    @Bean
    public RibbonCustom ribbonCustom(){
        return new RibbonCustom();
    }

    @Bean
    public NacosAndRibbonCustom nacosAndRibbonCustom(NacosCustom nacosCustom, RibbonCustom ribbonCustom){
        return new NacosAndRibbonCustom(nacosCustom, ribbonCustom);
    }

    @Bean
    public NacosLifecycle nacosLifecycle(NacosAndRibbonCustom nacosAndRibbonHandle, NacosDiscoveryProperties properties){
        return new NacosLifecycle(nacosAndRibbonHandle,properties);
    }
}
