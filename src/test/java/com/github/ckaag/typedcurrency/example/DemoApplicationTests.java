package com.github.ckaag.typedcurrency.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DemoApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnDefaultMessage() throws Exception {
        var json = "{\"name\":\"abc\",\"netWithRounding\":31.12345,\"grossWithString\":\"42.1234\",\"notNull\":null,\"nullableValue\":null}";

        this.mockMvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content(json)).andDo(print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.netWithRounding").value("31.12")).andExpect(jsonPath("$.name").value("changed"));
    }
}
