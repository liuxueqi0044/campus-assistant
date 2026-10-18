package cn.iocoder.yudao.module.campus.identity.controller.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only canonical endpoints used to exercise the production security matcher. */
@RestController
@RequestMapping("/app-api/campus/v1")
public class CampusIdentitySecurityPublicController {

    @GetMapping("/schools")
    public String schools() {
        return "schools";
    }

    @GetMapping("/context")
    public String context() {
        return "context";
    }

    @PutMapping("/context")
    public String updateContext() {
        return "context";
    }

    @PostMapping("/auth/wechat/login")
    public String wechatLogin() {
        return "login";
    }

    @PostMapping("/auth/logout")
    public String logout() {
        return "logout";
    }

}
