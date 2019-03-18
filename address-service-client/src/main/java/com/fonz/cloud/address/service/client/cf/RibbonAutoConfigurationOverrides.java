package com.fonz.cloud.address.service.client.cf;

import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalancedRetryFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.RibbonAutoConfiguration;
import org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerContext;
import org.springframework.cloud.netflix.ribbon.ServerIntrospector;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.netflix.ribbon.apache.RetryableRibbonLoadBalancingHttpClient;
import org.springframework.context.annotation.Bean;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;


/**
 * Configuration that overrides the defaults configured in {@link RibbonAutoConfiguration}.
 */
//DON'T add @Configuration here! The Spring Cloud docs are wrong!
public class RibbonAutoConfigurationOverrides {

    @Autowired
    SpringClientFactory springClientFactory;
    
    @Bean
    public RetryableRibbonLoadBalancingHttpClient retryableRibbonLoadBalancingHttpClient(
            IClientConfig config, ServerIntrospector serverIntrospector,
            ILoadBalancer loadBalancer, RetryHandler retryHandler,
            LoadBalancedRetryFactory loadBalancedRetryFactory,
            CloseableHttpClient httpClient,
            RibbonLoadBalancerContext ribbonLoadBalancerContext) {

        CustomRetryableRibbonLoadBalancingHttpClient client = new CustomRetryableRibbonLoadBalancingHttpClient(httpClient, config, serverIntrospector, loadBalancedRetryFactory);
        client.setLoadBalancer(loadBalancer);
        client.setRetryHandler(retryHandler);
        client.setRibbonLoadBalancerContext(ribbonLoadBalancerContext);
        return client;
    }
    
    @Bean
    public LoadBalancerClient loadBalancerClient() {
        return new CustomRibbonLoadBalancerClient(springClientFactory);
    }
}
