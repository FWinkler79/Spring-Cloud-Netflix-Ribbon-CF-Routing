package com.fonz.cloud.address.service.client.ribboninject;

import java.io.IOException;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;

/**
 * A custom RibbonLoadBalancerClient implementation.
 * RibbonLoadBalancerClient is a Spring Cloud implementation of LoadBalancerClient 
 * using Ribbon as its load balancer.
 * 
 * Spring Cloud introduced their own LoadBalancer framework, and  one implementation
 * uses Ribbon.
 *
 */
public class CustomRibbonLoadBalancerClient extends RibbonLoadBalancerClient {

    public CustomRibbonLoadBalancerClient(SpringClientFactory clientFactory) {
        super(clientFactory);
    }

    @Override
    public <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException {
        System.out.println("Execute Called. 1");
        return super.execute(serviceId, request);
    }

    @Override
    public <T> T execute(String serviceId, LoadBalancerRequest<T> request, Object hint) throws IOException {
        System.out.println("Execute Called. 2");
        return super.execute(serviceId, request, hint);
    }

    @Override
    public <T> T execute(String serviceId, ServiceInstance serviceInstance, LoadBalancerRequest<T> request)
            throws IOException {
        System.out.println("Execute Called. 3");
        return super.execute(serviceId, serviceInstance, request);
    }
}
