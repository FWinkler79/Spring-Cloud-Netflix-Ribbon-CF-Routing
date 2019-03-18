package com.fonz.cloud.address.service.client.ribboninject;

import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancedRetryFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer;
import org.springframework.cloud.netflix.ribbon.ServerIntrospector;
import org.springframework.cloud.netflix.ribbon.apache.RetryableRibbonLoadBalancingHttpClient;
import org.springframework.cloud.netflix.ribbon.apache.RibbonApacheHttpRequest;
import org.springframework.cloud.netflix.ribbon.apache.RibbonApacheHttpResponse;

import com.netflix.client.ClientException;
import com.netflix.client.config.IClientConfig;

/**
 * A custom implementation of the RetryableRibbonLoadBalancingHttpClient - an HttpClient which 
 * is injected into the Spring Cloud Ribbon implementations.
 * 
 * Spring Cloud HTTP Clients like Feign / RestTemplate use this client to send requests.
 * However, this client is not used by Ribbon / RestTemplate / Feign when it retries requests 
 * internally (as a result of Ribbon's retry policies).
 * 
 *  To integrate into the retry flow of Ribbon and intercept the requests going back and forth 
 *  as a result of Ribbon noticing that service instances are not available, you need to implement
 *  a {@link LoadBalancerRequestTransformer}.
 */
public class CustomRetryableRibbonLoadBalancingHttpClient extends RetryableRibbonLoadBalancingHttpClient {

    public CustomRetryableRibbonLoadBalancingHttpClient(CloseableHttpClient delegate, IClientConfig config, ServerIntrospector serverIntrospector,
            LoadBalancedRetryFactory loadBalancedRetryFactory) {
        super(delegate, config, serverIntrospector, loadBalancedRetryFactory);
    }

    @Override
    public RibbonApacheHttpResponse execute(RibbonApacheHttpRequest request, IClientConfig configOverride)
            throws Exception {
        System.out.println("MyLoadbalancer called! - execute(RibbonApacheHttpRequest request, IClientConfig configOverride)");
        return super.execute(request, configOverride);
    }

    @Override
    public RibbonApacheHttpResponse executeWithLoadBalancer(RibbonApacheHttpRequest request) throws ClientException {
        System.out.println("MyLoadbalancer called! - executeWithLoadBalancer(RibbonApacheHttpRequest request)");
        return super.executeWithLoadBalancer(request);
    }

    @Override
    public RibbonApacheHttpResponse executeWithLoadBalancer(RibbonApacheHttpRequest request,
            IClientConfig requestConfig) throws ClientException {
        System.out.println("MyLoadbalancer called! - executeWithLoadBalancer(RibbonApacheHttpRequest request, IClientConfig requestConfig)");
        return super.executeWithLoadBalancer(request, requestConfig);
    }
}
