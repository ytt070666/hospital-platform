package com.hospital.platform.bootstrap;
import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication; import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration; import org.springframework.scheduling.annotation.EnableScheduling;
@EnableScheduling @SpringBootApplication(scanBasePackages="com.hospital.platform",exclude=UserDetailsServiceAutoConfiguration.class) public class HospitalApplication { public static void main(String[] args){SpringApplication.run(HospitalApplication.class,args);} }
