package com.github.ckaag.typedcurrency.example;

import com.github.ckaag.typedcurrency.CurrencyGross;
import com.github.ckaag.typedcurrency.CurrencyNet;
import com.github.ckaag.typedcurrency.CurrencyNetNullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestBody {
        private String name;
        private CurrencyNet netWithRounding;
        private CurrencyGross grossWithString;
        private CurrencyNet notNull;
        private CurrencyNetNullable nullableValue;
    }

    @RequiredArgsConstructor
    @RequestMapping("/test")
    @RestController
    public static class TestController {

        @PostMapping
        public TestBody parseAndFormat(@Valid @RequestBody TestBody body) {
            return new TestBody("changed", body.getNetWithRounding(), body.getGrossWithString(), body.getNotNull(), body.getNullableValue());
        }
    }
}
