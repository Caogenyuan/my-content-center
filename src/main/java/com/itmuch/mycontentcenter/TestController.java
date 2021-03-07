package com.itmuch.mycontentcenter;

import com.itmuch.mycontentcenter.dao.content.ShareMapper;
import com.itmuch.mycontentcenter.domain.dto.user.UserDTO;
import com.itmuch.mycontentcenter.domain.entity.content.Share;
import com.itmuch.mycontentcenter.feignclient.TestBaiduFeignClient;
import com.itmuch.mycontentcenter.feignclient.TestUserCenterFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

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
}
