package com.itmuch.mycontentcenter;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.itmuch.mycontentcenter.dao.content.ShareMapper;
import com.itmuch.mycontentcenter.domain.dto.user.UserDTO;
import com.itmuch.mycontentcenter.domain.entity.content.Share;
import com.itmuch.mycontentcenter.feignclient.TestBaiduFeignClient;
import com.itmuch.mycontentcenter.feignclient.TestUserCenterFeignClient;
import com.itmuch.mycontentcenter.sentineltest.TestControllerBlockHandlerClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController
public class TestController {
    @Autowired
    private ShareMapper shareMapper;

    @Autowired
    private DiscoveryClient discoveryClient;



    @GetMapping("/test")
    public List<Share> testInsert(){
        // 1.做插入
        Share share = new Share();
        share.setCreateTime(new Date());
        share.setUpdateTime(new Date());
        share.setTitle("xxx");
        share.setCover("xxx");
        share.setAuthor("根源");
        share.setBuyCount(1);

        shareMapper.insertSelective(share);
        // 2.做查询:查询当前数据库所有的share
        List<Share> shares = shareMapper.selectAll();
        return shares;
    }

    /**
     * 测试：服务发现，证明内容中心总能找到用户中心
     *
     * @return 用户中心所有实例的地址信息
     */
    @GetMapping("test2")
    public List<ServiceInstance> getInstances() {
        // 查询指定服务的所有实例的信息
        // consul/eureka/zookeeper...
        return this.discoveryClient.getInstances("my-user-center");
    }

    @Autowired
    private TestUserCenterFeignClient testUserCenterFeignClient;

    @GetMapping("test-get")
    public UserDTO query(UserDTO userDTO) {
        return testUserCenterFeignClient.query(userDTO);
    }

    @Autowired
    private TestBaiduFeignClient testBaiduFeignClient;

    @GetMapping("baidu")
    public String baiduIndex() {
        return this.testBaiduFeignClient.index();
    }

    @Autowired
    private TestService testService;

    @GetMapping("test-a")
    public String testA() {
        this.testService.common();
        return "test-a";
    }

    @GetMapping("test-b")
    public String testB() {
        this.testService.common();
        return "test-b";
    }

    @GetMapping("test-hot")
    @SentinelResource("hot")
    public String testHot(
            @RequestParam(required = false) String a,
            @RequestParam(required = false) String b
    ) {
        return a + " " + b;
    }

    @GetMapping("test-add-flow-rule")
    public String testHot() {
        this.initFlowQpsRule();
        return "success";
    }

    private void initFlowQpsRule() {
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule("/shares/1");
        // set limit qps to 20
        rule.setCount(20);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setLimitApp("default");
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
    }

    @GetMapping("/test-sentinel-api")
    public String testSentinelAPI(
            @RequestParam(required = false) String a) {

        String resourceName = "test-sentinel-api";
        // 针对来源 test-wfw
        ContextUtil.enter(resourceName, "test-wfw");

        // 定义一个sentinel保护的资源，名称是test-sentinel-api
        Entry entry = null;
        try {

            entry = SphU.entry(resourceName);
            // 被保护的业务逻辑
            if (StringUtils.isBlank(a)) {
                throw new IllegalArgumentException("a不能为空");
            }
            return a;
        }
        // 如果被保护的资源被限流或者降级了，就会抛BlockException
        catch (BlockException e) {
            log.warn("限流，或者降级了", e);
            return "限流，或者降级了";
        } catch (IllegalArgumentException e2) {
            // 统计IllegalArgumentException【发生的次数、发生占比...】
            Tracer.trace(e2);
            return "参数非法！";
        } finally {
            if (entry != null) {
                // 退出entry
                entry.exit();
            }
            ContextUtil.exit();
        }
    }

    @GetMapping("/test-sentinel-resource")
    // 默认会Trace Throwable异常 所以IllegalArgumentException不用Tracer
    @SentinelResource(
            value = "test-sentinel-api",
            blockHandler = "block",
            blockHandlerClass = TestControllerBlockHandlerClass.class,
            fallback = "fallback"
    )
    public String testSentinelResource(@RequestParam(required = false) String a) {
        if (StringUtils.isBlank(a)) {
            throw new IllegalArgumentException("a cannot be blank.");
        }
        return a;
    }

    /**
     * 处理降级
     * @param a
     * @param e
     * @return
     */
    public String fallback(String a, Throwable e){
        log.warn("限流，或者降级了 fallback", e);
        return "限流，或者降级了 fallback";
    }

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping("/test-rest-template-sentinel/{userId}")
    public UserDTO test(@PathVariable Integer userId) {
        return this.restTemplate
                .getForObject("http://my-user-center/users/{userId}",
                        UserDTO.class, userId);
    }

    @Autowired
    private Source source;

    @GetMapping("/test-stream")
    public String testStream(){
        this.source.output()
                .send(
                        MessageBuilder
                                .withPayload("消息体")
                                .build()
                );
        return "success";
    }

}
