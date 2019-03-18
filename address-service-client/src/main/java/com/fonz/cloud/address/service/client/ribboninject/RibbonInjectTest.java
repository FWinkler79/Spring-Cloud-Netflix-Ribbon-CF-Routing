package com.fonz.cloud.address.service.client.ribboninject;

import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * A sample class that shows Ribbon's retry capabilities using @Loadbalance'd
 * RESTTemplates.
 * 
 * It uses {@link ETAddressServiceClient} which internally uses a loadbalanced
 * REST template. That REST Template is backed by Ribbon, which (using
 * application.yml) is configured to retry. With Spring Retry on the classpath
 * (as configured in pom.xml) {@link ETAddressServiceClient} will retry failed
 * requests and even retry them on another service instance.
 * 
 * To try this out, consider changing class RESTEndpoint of project
 * address.service to always throw an exception. Then bring up two instances of
 * address.service and a Eureka instance.
 * 
 * When firing requests with RetryTestApp, you will see two requests being fired
 * against the first address.service instance (as a result of Ribbon retrying)
 * resulting in two exception stack traces in the console of address.service
 * instance one. You will also see two requests fired against the second
 * instance of address.service, also resulting in two exception stack traces
 * being logged in the console of address.service's second instance.
 * 
 * The same is of course true for FeignClients as used in {@ClientApp}. Feel
 * free to play around with it.
 *
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableCircuitBreaker
@RibbonClient(name = "address-service", configuration = RibbonAutoConfigurationOverrides.class)
public class RibbonInjectTest {

    public static void main(String[] args) throws RestClientException, IOException {
        ApplicationContext ctx = SpringApplication.run(RibbonInjectTest.class, args);

        String[] availableBeans = ctx.getBeanDefinitionNames();
        dumpBeans(availableBeans);

        FailingAddressServiceClient failingAddressServiceClient = ctx.getBean(FailingAddressServiceClient.class);
        System.out.println(failingAddressServiceClient);
        System.err.println("Address from RestTemplate Approach: " + failingAddressServiceClient.getAddress());
    }

    private static void dumpBeans(String[] availableBeans) {
        System.out.println("Available Beans");
        for (String name : availableBeans) {
            System.out.println("-- Bean: " + name);
        }
    }

    @Bean
    public FailingAddressServiceClient failingAddressServiceClient() {
        return new FailingAddressServiceClient();
    }

    @LoadBalanced // Note this annotation! It makes sure that RestTemplate uses Ribbon under the
                  // hood and thus inherits Eureka integration.
    @Bean
    public RestTemplate failingAddressServiceClientRestTemplate() {
        return new RestTemplate();
    }
}